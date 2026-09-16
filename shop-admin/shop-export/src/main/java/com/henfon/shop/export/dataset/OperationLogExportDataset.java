package com.henfon.shop.export.dataset;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.export.dto.ExportQuery;
import com.henfon.shop.export.entity.ExportType;
import com.henfon.shop.export.excel.ExcelColumn;
import com.henfon.shop.identity.entity.SysOperLog;
import com.henfon.shop.identity.mapper.SysOperLogMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 系统操作审计日志导出数据集。
 *
 * @author Henfon
 * @date 2026-09-16
 */
@Component
public class OperationLogExportDataset extends AbstractExportDataset<OperationLogExportDataset.Row> {

    /** 业务模块中文名，与前端操作审计页面的模块筛选保持一致。 */
    private static final Map<String, String> MODULE_LABELS = Map.ofEntries(
            Map.entry("auth", "认证"), Map.entry("catalog", "商品"), Map.entry("content", "内容"),
            Map.entry("inventory", "库存"), Map.entry("marketing", "营销"), Map.entry("member", "会员"),
            Map.entry("payment", "支付"), Map.entry("reporting", "报表"), Map.entry("storage", "存储"),
            Map.entry("system", "系统"), Map.entry("trade", "交易"));

    private final SysOperLogMapper sysOperLogMapper;

    /**
     * 创建操作日志导出数据集。
     *
     * @param sysOperLogMapper 操作日志数据访问对象
     * @author Henfon
     * @date 2026-09-16
     */
    public OperationLogExportDataset(SysOperLogMapper sysOperLogMapper) {
        this.sysOperLogMapper = sysOperLogMapper;
    }

    /**
     * 操作日志导出行。
     *
     * @param createdAt 操作时间
     * @param username 操作人
     * @param moduleLabel 业务模块
     * @param operation 操作内容
     * @param requestMethod 请求方式
     * @param requestUri 请求地址
     * @param responseStatus 响应状态
     * @param durationMs 耗时
     * @param clientIp 客户端IP
     * @param traceId 追踪ID
     * @author Henfon
     * @date 2026-09-16
     */
    public record Row(LocalDateTime createdAt, String username, String moduleLabel, String operation,
                      String requestMethod, String requestUri, Integer responseStatus, Long durationMs,
                      String clientIp, String traceId) {
    }

    @Override
    public ExportType type() {
        return ExportType.OPERATION_LOG;
    }

    @Override
    public List<ExcelColumn<Row>> columns() {
        return List.of(
                ExcelColumn.dateTime("操作时间", Row::createdAt, 21),
                ExcelColumn.text("操作人", Row::username, 18),
                ExcelColumn.text("业务模块", Row::moduleLabel, 12),
                ExcelColumn.text("操作内容", Row::operation, 30),
                ExcelColumn.text("请求方式", Row::requestMethod, 11),
                ExcelColumn.text("请求地址", Row::requestUri, 38),
                ExcelColumn.integer("响应状态", Row::responseStatus, 11),
                ExcelColumn.integer("耗时(ms)", Row::durationMs, 11),
                ExcelColumn.text("客户端IP", Row::clientIp, 18),
                ExcelColumn.text("追踪ID", Row::traceId, 34));
    }

    @Override
    public void stream(Consumer<Row> consumer, ExportQuery query) {
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.keyword()), SysOperLog::getUsername, query.keyword());
        wrapper.eq(StringUtils.hasText(query.moduleKey()), SysOperLog::getModuleKey, query.moduleKey());
        wrapper.orderByDesc(SysOperLog::getId);

        streamPages(consumer, (pageNo, pageSize) -> {
            Page<SysOperLog> page = sysOperLogMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
            return page.getRecords().stream().map(OperationLogExportDataset::toRow).toList();
        });
    }

    /**
     * 实体转导出行为。
     *
     * @param log 操作日志实体
     * @return 导出行
     * @author Henfon
     * @date 2026-09-16
     */
    private static Row toRow(SysOperLog log) {
        String moduleLabel = log.getModuleKey() == null
                ? null : MODULE_LABELS.getOrDefault(log.getModuleKey(), log.getModuleKey());
        return new Row(log.getCreatedAt(), log.getUsername(), moduleLabel, log.getOperation(),
                log.getRequestMethod(), log.getRequestUri(), log.getResponseStatus(), log.getDurationMs(),
                log.getClientIp(), log.getTraceId());
    }
}
