package com.henfon.shop.reporting.service;

import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.reporting.dto.ReportingDashboardMetricsResponse;
import com.henfon.shop.reporting.mapper.ReportingMetricsMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 后台首页经营指标应用服务。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Service
public class ReportingDashboardService {

    private final ReportingMetricsMapper reportingMetricsMapper;

    /**
     * 创建经营指标服务。
     *
     * @param reportingMetricsMapper 经营指标聚合数据访问对象
     * @author Henfon
     * @date 2026-08-31
     */
    public ReportingDashboardService(ReportingMetricsMapper reportingMetricsMapper) {
        this.reportingMetricsMapper = reportingMetricsMapper;
    }

    /**
     * 查询指定日期的首页经营指标。
     *
     * @param date 指标日期，为空时使用当天
     * @return 首页经营指标
     * @author Henfon
     * @date 2026-08-31
     */
    public ReportingDashboardMetricsResponse queryMetrics(LocalDate date) {
        // 统一使用业务时区的自然日边界，查询条件采用左闭右开避免毫秒边界重复统计。
        LocalDate queryDate = date == null ? LocalDate.now() : date;
        LocalDate today = LocalDate.now();
        if (queryDate.isAfter(today)) {
            throw new BusinessException("REPORTING_DATE_INVALID", "指标日期不能晚于今天");
        }
        LocalDateTime startTime = queryDate.atStartOfDay();
        LocalDateTime endTime = queryDate.plusDays(1).atStartOfDay();

        // 销售额仅统计已支付且未退款订单，订单数则统计日期内创建的全部有效订单。
        return new ReportingDashboardMetricsResponse(
                queryDate,
                valueOrZero(reportingMetricsMapper.countOrders(startTime, endTime)),
                amountOrZero(reportingMetricsMapper.sumSales(startTime, endTime)),
                valueOrZero(reportingMetricsMapper.countProducts(startTime, endTime)),
                valueOrZero(reportingMetricsMapper.countMembers(startTime, endTime)),
                valueOrZero(reportingMetricsMapper.countOrdersBefore(endTime)),
                amountOrZero(reportingMetricsMapper.sumSalesBefore(endTime)),
                valueOrZero(reportingMetricsMapper.countProductsBefore(endTime)),
                valueOrZero(reportingMetricsMapper.countMembersBefore(endTime)));
    }

    /**
     * 将可能为空的数量转换为零。
     *
     * @param value 数据库聚合结果
     * @return 非空数量
     * @author Henfon
     * @date 2026-08-31
     */
    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 将可能为空的金额转换为零金额。
     *
     * @param value 数据库聚合结果
     * @return 非空金额
     * @author Henfon
     * @date 2026-08-31
     */
    private BigDecimal amountOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
