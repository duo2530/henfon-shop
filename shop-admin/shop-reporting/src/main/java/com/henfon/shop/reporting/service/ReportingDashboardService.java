package com.henfon.shop.reporting.service;

import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.reporting.dto.ReportingDashboardMetricsResponse;
import com.henfon.shop.reporting.dto.ReportingSalesTrendPoint;
import com.henfon.shop.reporting.dto.ReportingSalesTrendRow;
import com.henfon.shop.reporting.mapper.ReportingMetricsMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * 查询指定日期范围内的每日销售趋势。
     *
     * @param startDate 开始日期，为空时默认取结束日期前六天
     * @param endDate 结束日期，为空时默认当天
     * @return 每日销售额、订单数和商品销量
     * @author Henfon
     * @date 2026-08-31
     */
    public List<ReportingSalesTrendPoint> querySalesTrend(LocalDate startDate, LocalDate endDate) {
        // 默认返回最近7天，并限制查询窗口不超过30天，避免报表接口扫描过大的时间范围。
        LocalDate today = LocalDate.now();
        LocalDate queryEnd = endDate == null ? today : endDate;
        LocalDate queryStart = startDate == null ? queryEnd.minusDays(6) : startDate;
        if (queryStart.isAfter(queryEnd)) {
            throw new BusinessException("REPORTING_DATE_RANGE_INVALID", "销售趋势开始日期不能晚于结束日期");
        }
        if (queryEnd.isAfter(today)) {
            throw new BusinessException("REPORTING_DATE_INVALID", "销售趋势结束日期不能晚于今天");
        }
        if (ChronoUnit.DAYS.between(queryStart, queryEnd) > 29) {
            throw new BusinessException("REPORTING_DATE_RANGE_TOO_LARGE", "销售趋势查询范围最多支持30天");
        }

        LocalDateTime startTime = queryStart.atStartOfDay();
        LocalDateTime endTime = queryEnd.plusDays(1).atStartOfDay();
        List<ReportingSalesTrendRow> rows = reportingMetricsMapper.listSalesTrend(startTime, endTime);
        Map<LocalDate, ReportingSalesTrendRow> rowMap = new HashMap<>();
        if (rows != null) {
            rows.forEach(row -> rowMap.put(row.getDate(), row));
        }

        // 将没有订单的日期补零，确保趋势图时间轴连续且不受数据稀疏影响。
        List<ReportingSalesTrendPoint> points = new ArrayList<>();
        for (LocalDate cursor = queryStart; !cursor.isAfter(queryEnd); cursor = cursor.plusDays(1)) {
            ReportingSalesTrendRow row = rowMap.get(cursor);
            points.add(new ReportingSalesTrendPoint(
                    cursor,
                    amountOrZero(row == null ? null : row.getSalesAmount()),
                    valueOrZero(row == null ? null : row.getOrderCount()),
                    valueOrZero(row == null ? null : row.getProductQuantity())));
        }
        return points;
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
