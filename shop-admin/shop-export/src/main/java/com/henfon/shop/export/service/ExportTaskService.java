package com.henfon.shop.export.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.export.dataset.ExportDatasetRegistry;
import com.henfon.shop.export.dto.ExportFile;
import com.henfon.shop.export.dto.ExportSubmitRequest;
import com.henfon.shop.export.dto.ExportTaskResponse;
import com.henfon.shop.export.entity.ExportTask;
import com.henfon.shop.export.entity.ExportTaskStatus;
import com.henfon.shop.export.entity.ExportType;
import com.henfon.shop.export.mapper.ExportTaskMapper;
import com.henfon.shop.integration.messaging.DomainEvent;
import com.henfon.shop.integration.messaging.RocketMqEventPublisher;
import com.henfon.shop.integration.messaging.RocketMqTopics;
import com.henfon.shop.integration.storage.MinioStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 导出任务服务。
 *
 * <p>提交任务只做三件事：落库为待执行、投递 MQ、返回任务标识。真正的生成动作由消费者
 * 或兜底扫描触发，因此接口不会阻塞页面。</p>
 *
 * @author Henfon
 * @date 2026-09-16
 */
@Service
public class ExportTaskService {

    private static final Logger log = LoggerFactory.getLogger(ExportTaskService.class);

    private static final DateTimeFormatter TASK_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final String EVENT_TYPE_REQUESTED = "EXPORT_TASK_REQUESTED";

    /** 列表单页上限，防止前端传入过大页大小拖垮查询。 */
    private static final long MAX_PAGE_SIZE = 50L;

    private final ExportTaskMapper exportTaskMapper;

    private final ExportDatasetRegistry exportDatasetRegistry;

    private final MinioStorageService minioStorageService;

    private final RocketMqEventPublisher rocketMqEventPublisher;

    private final ObjectMapper objectMapper;

    /**
     * 创建导出任务服务。
     *
     * @param exportTaskMapper 导出任务数据访问对象
     * @param exportDatasetRegistry 导出数据集注册表
     * @param minioStorageService 文件存储服务
     * @param rocketMqEventPublisher 消息发布器
     * @param objectMapper JSON 序列化器
     * @author Henfon
     * @date 2026-09-16
     */
    public ExportTaskService(ExportTaskMapper exportTaskMapper,
                            ExportDatasetRegistry exportDatasetRegistry,
                            MinioStorageService minioStorageService,
                            RocketMqEventPublisher rocketMqEventPublisher,
                            ObjectMapper objectMapper) {
        this.exportTaskMapper = exportTaskMapper;
        this.exportDatasetRegistry = exportDatasetRegistry;
        this.minioStorageService = minioStorageService;
        this.rocketMqEventPublisher = rocketMqEventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * 提交导出任务。
     *
     * @param request 提交请求
     * @param operatorId 提交人管理员ID
     * @param operatorName 提交人名称
     * @return 任务信息
     * @author Henfon
     * @date 2026-09-16
     */
    public ExportTaskResponse submit(ExportSubmitRequest request, Long operatorId, String operatorName) {
        ExportType type = ExportType.parse(request.exportType());
        if (type == null) {
            throw new BusinessException("EXPORT_TYPE_UNSUPPORTED", "不支持的导出类型：" + request.exportType());
        }
        // 提前校验该类型是否已实现，避免提交出永远无法完成的任务。
        exportDatasetRegistry.require(type);

        ExportTask task = new ExportTask();
        task.setExportType(type.name());
        task.setExportName(type.displayName());
        task.setQueryParams(writeQuery(request));
        task.setStatus(ExportTaskStatus.PENDING.name());
        task.setRequestedBy(operatorId);
        task.setRequestedByName(operatorName);
        task.setCreatedAt(LocalDateTime.now());
        task.setIsDeleted(0);
        exportTaskMapper.insert(task);

        // 任务编号依赖自增主键，插入后回填，天然避免并发提交时的编号冲突。
        String taskNo = "EXP" + LocalDateTime.now().format(TASK_NO_FORMATTER)
                + "-" + String.format("%04d", task.getId() % 10000);
        exportTaskMapper.assignTaskNo(task.getId(), taskNo);
        task.setTaskNo(taskNo);

        publishRequested(task.getId());
        return toResponse(task);
    }

    /**
     * 分页查询本人提交的导出任务。
     *
     * @param current 当前页
     * @param size 页大小
     * @param operatorId 提交人管理员ID
     * @return 任务分页结果
     * @author Henfon
     * @date 2026-09-16
     */
    public Page<ExportTaskResponse> page(long current, long size, Long operatorId) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        LambdaQueryWrapper<ExportTask> wrapper = new LambdaQueryWrapper<ExportTask>()
                .eq(ExportTask::getRequestedBy, operatorId)
                .eq(ExportTask::getIsDeleted, 0)
                .orderByDesc(ExportTask::getId);
        Page<ExportTask> result = exportTaskMapper.selectPage(new Page<>(safeCurrent, safeSize), wrapper);
        Page<ExportTaskResponse> response = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        response.setRecords(result.getRecords().stream().map(ExportTaskService::toResponse).toList());
        return response;
    }

    /**
     * 下载导出文件。
     *
     * @param taskId 任务ID
     * @param operatorId 提交人管理员ID
     * @return 下载载荷
     * @author Henfon
     * @date 2026-09-16
     */
    public ExportFile download(Long taskId, Long operatorId) {
        ExportTask task = requireOwnedTask(taskId, operatorId);
        if (!ExportTaskStatus.SUCCESS.name().equals(task.getStatus()) || task.getObjectKey() == null) {
            throw new BusinessException("EXPORT_FILE_NOT_READY", "文件尚未生成或已过期，请稍后重试或重新导出");
        }
        return new ExportFile(task.getFileName(), task.getFileSize(),
                minioStorageService.openObject(task.getObjectKey()));
    }

    /**
     * 移除任务记录，同时清理已生成的文件。
     *
     * @param taskId 任务ID
     * @param operatorId 提交人管理员ID
     * @author Henfon
     * @date 2026-09-16
     */
    public void remove(Long taskId, Long operatorId) {
        ExportTask task = requireOwnedTask(taskId, operatorId);
        if (ExportTaskStatus.PENDING.name().equals(task.getStatus())
                || ExportTaskStatus.RUNNING.name().equals(task.getStatus())) {
            throw new BusinessException("EXPORT_TASK_RUNNING", "任务正在生成中，请稍后再移除");
        }
        deleteObjectQuietly(task);
        exportTaskMapper.softDelete(taskId);
    }

    /**
     * 重试失败的任务。
     *
     * @param taskId 任务ID
     * @param operatorId 提交人管理员ID
     * @return 重新排队后的任务信息
     * @author Henfon
     * @date 2026-09-16
     */
    public ExportTaskResponse retry(Long taskId, Long operatorId) {
        ExportTask task = requireOwnedTask(taskId, operatorId);
        if (exportTaskMapper.requeue(taskId) != 1) {
            throw new BusinessException("EXPORT_TASK_RETRY_INVALID", "仅生成失败的任务可以重试");
        }
        publishRequested(taskId);
        task.setStatus(ExportTaskStatus.PENDING.name());
        task.setErrorMessage(null);
        return toResponse(task);
    }

    /**
     * 删除对象存储中的导出文件，失败只记录日志。
     *
     * @param task 任务
     * @author Henfon
     * @date 2026-09-16
     */
    public void deleteObjectQuietly(ExportTask task) {
        if (task.getObjectKey() == null) {
            return;
        }
        try {
            minioStorageService.delete(task.getObjectKey());
        } catch (Exception exception) {
            log.warn("导出文件删除失败，任务={}，对象键={}", task.getId(), task.getObjectKey(), exception);
        }
    }

    /**
     * 投递任务就绪消息。
     *
     * <p>消息投递失败不影响任务本身：任务已持久化为待执行，兜底扫描会接替触发，
     * 因此这里只记录告警，不向调用方抛出。</p>
     *
     * @param taskId 任务ID
     * @author Henfon
     * @date 2026-09-16
     */
    private void publishRequested(Long taskId) {
        try {
            DomainEvent event = new DomainEvent(UUID.randomUUID().toString(), EVENT_TYPE_REQUESTED,
                    String.valueOf(taskId), Instant.now(), taskId);
            rocketMqEventPublisher.publish(RocketMqTopics.EXPORT_TASK_REQUESTED, event);
        } catch (Exception exception) {
            log.warn("导出任务{}消息投递失败，将由兜底扫描接替执行：{}", taskId, exception.getMessage());
        }
    }

    /**
     * 查询任务并校验归属。
     *
     * <p>下载、重试、移除都只允许操作自己提交的任务。控制层在调用这些动作前也需要拿到任务实体，
     * 用于按导出类型做权限二次校验，因此本方法对外可见；它只做归属过滤，不改变任务状态。</p>
     *
     * @param taskId 任务ID
     * @param operatorId 提交人管理员ID
     * @return 任务实体
     * @author Henfon
     * @date 2026-09-16
     */
    public ExportTask requireOwnedTask(Long taskId, Long operatorId) {
        ExportTask task = exportTaskMapper.selectOne(new LambdaQueryWrapper<ExportTask>()
                .eq(ExportTask::getId, taskId)
                .eq(ExportTask::getRequestedBy, operatorId)
                .eq(ExportTask::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (task == null) {
            throw new BusinessException("EXPORT_TASK_NOT_FOUND", "导出任务不存在");
        }
        return task;
    }

    /**
     * 序列化查询条件。
     *
     * @param request 提交请求
     * @return JSON 文本
     * @author Henfon
     * @date 2026-09-16
     */
    private String writeQuery(ExportSubmitRequest request) {
        try {
            return objectMapper.writeValueAsString(request.toQuery());
        } catch (Exception exception) {
            // 条件快照只用于追溯，序列化失败不应阻断导出本身。
            return null;
        }
    }

    /**
     * 实体转响应。
     *
     * @param task 任务实体
     * @return 响应对象
     * @author Henfon
     * @date 2026-09-16
     */
    private static ExportTaskResponse toResponse(ExportTask task) {
        boolean downloadable = ExportTaskStatus.SUCCESS.name().equals(task.getStatus())
                && task.getObjectKey() != null;
        return new ExportTaskResponse(task.getId(), task.getTaskNo(), task.getExportType(), task.getExportName(),
                task.getStatus(), task.getFileName(), task.getFileSize(), task.getRowCount(),
                task.getErrorMessage(), task.getRequestedByName(), task.getCreatedAt(), task.getFinishedAt(),
                task.getExpiresAt(), downloadable);
    }
}
