package com.henfon.shop.export.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.henfon.shop.export.dataset.ExportDataset;
import com.henfon.shop.export.dataset.ExportDatasetRegistry;
import com.henfon.shop.export.dto.ExportQuery;
import com.henfon.shop.export.entity.ExportTask;
import com.henfon.shop.export.entity.ExportType;
import com.henfon.shop.export.excel.ExcelExporter;
import com.henfon.shop.export.mapper.ExportTaskMapper;
import com.henfon.shop.integration.storage.MinioStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * 导出任务执行器。
 *
 * <p>执行入口必须是幂等的：先以带状态条件的更新认领任务，认领失败直接返回。
 * RocketMQ 的重复投递、兜底扫描与人工重试都可能并发触发同一任务，
 * 靠数据库行级条件更新保证只有一个执行者真正生成文件。</p>
 *
 * @author Henfon
 * @date 2026-09-16
 */
@Service
public class ExportTaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(ExportTaskExecutor.class);

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final DateTimeFormatter OBJECT_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 失败原因入库前的最大长度，超出会被截断。 */
    private static final int MAX_ERROR_LENGTH = 480;

    private final ExportTaskMapper exportTaskMapper;

    private final ExportDatasetRegistry exportDatasetRegistry;

    private final MinioStorageService minioStorageService;

    private final ObjectMapper objectMapper;

    /** 导出文件保留天数。 */
    private final long retentionDays;

    /**
     * 创建导出任务执行器。
     *
     * @param exportTaskMapper 导出任务数据访问对象
     * @param exportDatasetRegistry 导出数据集注册表
     * @param minioStorageService 文件存储服务
     * @param objectMapper JSON 序列化器
     * @param retentionDays 导出文件保留天数
     * @author Henfon
     * @date 2026-09-16
     */
    public ExportTaskExecutor(ExportTaskMapper exportTaskMapper,
                              ExportDatasetRegistry exportDatasetRegistry,
                              MinioStorageService minioStorageService,
                              ObjectMapper objectMapper,
                              @Value("${shop.export.retention-days:7}") long retentionDays) {
        this.exportTaskMapper = exportTaskMapper;
        this.exportDatasetRegistry = exportDatasetRegistry;
        this.minioStorageService = minioStorageService;
        this.objectMapper = objectMapper;
        this.retentionDays = retentionDays;
    }

    /**
     * 执行一个导出任务。
     *
     * <p>任何异常都会落到任务自身的失败状态上，不向外抛出：MQ 层面已视为消费成功，
     * 重试交由任务列表的「重试」按钮或兜底扫描决定。</p>
     *
     * @param taskId 任务ID
     * @author Henfon
     * @date 2026-09-16
     */
    public void execute(Long taskId) {
        // 认领时刻会被写入 claimed_at 并作为后续写回结果的比对条件，
        // 因此按列精度截断到毫秒，避免入库四舍五入导致比对失败。
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        ExportTask task = exportTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        if (exportTaskMapper.claim(taskId, now) != 1) {
            // 已被其它执行者认领或状态已变更，直接跳过，保证不重复生成。
            log.debug("导出任务{}未被认领，跳过执行", taskId);
            return;
        }
        try {
            ExportType type = ExportType.parse(task.getExportType());
            ExportDataset<?> dataset = exportDatasetRegistry.require(type);
            ExportQuery query = readQuery(task.getQueryParams());

            String fileName = buildFileName(type, now);
            String objectKey = buildObjectKey(task, fileName);
            // Excel 先写到本地临时文件再流式上传：导出产物可能几十兆，直接拼字节数组会让
            // SXSSF 的流式写盘白做，大表导出照样受堆内存约束。
            Path tempFile = Files.createTempFile("shop-export-", ".xlsx");
            int rowCount;
            long fileSize;
            try {
                try (OutputStream out = Files.newOutputStream(tempFile)) {
                    rowCount = writeExcel(out, dataset, query);
                }
                fileSize = Files.size(tempFile);
                minioStorageService.uploadGenerated(objectKey, tempFile, XLSX_CONTENT_TYPE);
            } finally {
                deleteQuietly(tempFile);
            }

            LocalDateTime finishedAt = LocalDateTime.now();
            int updated = exportTaskMapper.markSuccess(taskId, objectKey, fileName, fileSize, rowCount,
                    finishedAt, finishedAt.plusDays(retentionDays), now);
            if (updated != 1) {
                // 兜底调度已把任务重新排队并再次认领，本次属于过期执行者，结果直接丢弃并留痕。
                log.warn("导出任务{}结果写入失败，任务已被重新认领，本次生成结果已忽略", taskId);
                return;
            }
            log.info("导出任务{}生成完成，类型={}，行数={}，大小={}字节", taskId, task.getExportType(), rowCount, fileSize);
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            exportTaskMapper.markFailed(taskId, truncate(message), LocalDateTime.now(), now);
            log.warn("导出任务{}生成失败：{}", taskId, message, exception);
        }
    }

    /**
     * 读取任务提交时的查询条件。
     *
     * @param queryParams JSON 文本
     * @return 查询条件，解析失败时退化为空条件
     * @author Henfon
     * @date 2026-09-16
     */
    private ExportQuery readQuery(String queryParams) {
        if (queryParams == null || queryParams.isBlank()) {
            return ExportQuery.empty();
        }
        try {
            return objectMapper.readValue(queryParams, ExportQuery.class);
        } catch (Exception exception) {
            log.warn("导出任务查询条件解析失败，按无条件导出处理：{}", queryParams);
            return ExportQuery.empty();
        }
    }

    /**
     * 生成 Excel 内容。
     *
     * <p>数据集实现持有各自的强类型列定义，这里通过原始类型桥接，避免为每个类型写一遍编排逻辑。</p>
     *
     * @param out 输出流
     * @param dataset 数据集
     * @param query 查询条件
     * @return 数据行数
     * @author Henfon
     * @date 2026-09-16
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private int writeExcel(OutputStream out, ExportDataset dataset, ExportQuery query) {
        return ExcelExporter.write(out, dataset.type().sheetName(), dataset.columns(),
                consumer -> dataset.stream(consumer, query));
    }

    /**
     * 删除临时文件，失败只记录日志。
     *
     * <p>临时文件位于系统临时目录，即使删除失败也会被操作系统的清理策略回收，
     * 不值得让整次导出因此失败。</p>
     *
     * @param file 临时文件
     * @author Henfon
     * @date 2026-09-16
     */
    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            log.warn("导出临时文件{}清理失败：{}", file, exception.getMessage());
        }
    }

    /**
     * 构造导出文件名。
     *
     * @param type 导出类型
     * @param now 生成时间
     * @return 文件名
     * @author Henfon
     * @date 2026-09-16
     */
    private String buildFileName(ExportType type, LocalDateTime now) {
        return type.displayName() + "_" + now.format(FILE_TIME_FORMATTER) + ".xlsx";
    }

    /**
     * 构造对象键，按日期分目录便于批量清理。
     *
     * @param task 任务
     * @param fileName 文件名
     * @return 对象键
     * @author Henfon
     * @date 2026-09-16
     */
    private String buildObjectKey(ExportTask task, String fileName) {
        String datePath = LocalDateTime.now().format(OBJECT_PATH_FORMATTER);
        String suffix = fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx") ? ".xlsx" : "";
        return "export/" + datePath + "/" + task.getId() + "_" + task.getExportType() + suffix;
    }

    /**
     * 截断过长的失败原因，避免超出数据库列长度。
     *
     * @param message 原始信息
     * @return 截断后的信息
     * @author Henfon
     * @date 2026-09-16
     */
    private String truncate(String message) {
        if (message == null) {
            return "导出失败";
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
