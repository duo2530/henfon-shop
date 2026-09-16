package com.henfon.shop.export.dataset;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.export.dto.ExportQuery;
import com.henfon.shop.export.entity.ExportType;
import com.henfon.shop.export.excel.ExcelColumn;
import com.henfon.shop.identity.entity.SysLoginLog;
import com.henfon.shop.identity.mapper.SysLoginLogMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * 管理员登录日志导出数据集。
 *
 * @author Henfon
 * @date 2026-09-16
 */
@Component
public class LoginLogExportDataset extends AbstractExportDataset<LoginLogExportDataset.Row> {

    private final SysLoginLogMapper sysLoginLogMapper;

    /**
     * 创建登录日志导出数据集。
     *
     * @param sysLoginLogMapper 登录日志数据访问对象
     * @author Henfon
     * @date 2026-09-16
     */
    public LoginLogExportDataset(SysLoginLogMapper sysLoginLogMapper) {
        this.sysLoginLogMapper = sysLoginLogMapper;
    }

    /**
     * 登录日志导出行。
     *
     * @param loginAt 登录时间
     * @param username 用户名
     * @param statusLabel 登录结果
     * @param loginIp 登录IP
     * @param userAgent 客户端信息
     * @param failureReason 失败原因
     * @author Henfon
     * @date 2026-09-16
     */
    public record Row(LocalDateTime loginAt, String username, String statusLabel, String loginIp,
                      String userAgent, String failureReason) {
    }

    @Override
    public ExportType type() {
        return ExportType.LOGIN_LOG;
    }

    @Override
    public List<ExcelColumn<Row>> columns() {
        return List.of(
                ExcelColumn.dateTime("登录时间", Row::loginAt, 21),
                ExcelColumn.text("用户名", Row::username, 18),
                ExcelColumn.text("登录结果", Row::statusLabel, 10),
                ExcelColumn.text("登录IP", Row::loginIp, 18),
                ExcelColumn.text("客户端信息", Row::userAgent, 40),
                ExcelColumn.text("失败原因", Row::failureReason, 26));
    }

    @Override
    public void stream(Consumer<Row> consumer, ExportQuery query) {
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.keyword()), SysLoginLog::getUsername, query.keyword());
        wrapper.eq(query.status() != null, SysLoginLog::getLoginStatus, query.status());
        wrapper.orderByDesc(SysLoginLog::getId);

        streamPages(consumer, (pageNo, pageSize) -> {
            Page<SysLoginLog> page = sysLoginLogMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
            return page.getRecords().stream().map(LoginLogExportDataset::toRow).toList();
        });
    }

    /**
     * 实体转导出行为。
     *
     * @param log 登录日志实体
     * @return 导出行
     * @author Henfon
     * @date 2026-09-16
     */
    private static Row toRow(SysLoginLog log) {
        return new Row(log.getLoginAt(), log.getUsername(),
                log.getLoginStatus() != null && log.getLoginStatus() == 1 ? "成功" : "失败",
                log.getLoginIp(), log.getUserAgent(), log.getFailureReason());
    }
}
