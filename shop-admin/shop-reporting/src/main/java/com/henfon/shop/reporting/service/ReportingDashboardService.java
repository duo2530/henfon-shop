package com.henfon.shop.reporting.service;

import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.reporting.dto.ReportingDashboardMetricsResponse;
import com.henfon.shop.reporting.dto.ReportingSalesTrendPoint;
import com.henfon.shop.reporting.dto.ReportingSalesTrendRow;
import com.henfon.shop.reporting.dto.ReportingProductRankingItem;
import com.henfon.shop.reporting.dto.ReportingProductRankingRow;
import com.henfon.shop.reporting.dto.ReportingMemberAnalysisResponse;
import com.henfon.shop.reporting.dto.ReportingMemberLevelStat;
import com.henfon.shop.reporting.dto.ReportingMemberLevelStatRow;
import com.henfon.shop.reporting.dto.ReportingChannelStat;
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
import java.util.Locale;
import java.util.Objects;

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
     * 查询指定日期范围内的商品销售排行。
     *
     * @param startDate 开始日期，为空时默认结束日期前29天
     * @param endDate 结束日期，为空时默认当天
     * @param limit 返回条数，默认20，最大100
     * @return 商品销售排行
     * @author Henfon
     * @date 2026-09-01
     */
    public List<ReportingProductRankingItem> queryProductRanking(LocalDate startDate, LocalDate endDate,
                                                                  Integer limit) {
        DateRange range = resolveRange(startDate, endDate);
        int queryLimit = limit == null ? 20 : limit;
        if (queryLimit < 1 || queryLimit > 100) {
            throw new BusinessException("REPORTING_LIMIT_INVALID", "商品排行条数必须在1到100之间");
        }
        List<ReportingProductRankingRow> rows = reportingMetricsMapper.listProductRanking(
                range.startTime(), range.endTime(), queryLimit);
        List<ReportingProductRankingItem> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        int rank = 1;
        for (ReportingProductRankingRow row : rows) {
            if (row == null) {
                continue;
            }
            result.add(new ReportingProductRankingItem(rank++, row.getProductId(),
                    Objects.requireNonNullElse(row.getProductName(), "未知商品"),
                    Objects.requireNonNullElse(row.getCategoryName(), "未分类"),
                    valueOrZero(row.getSalesVolume()), amountOrZero(row.getSalesAmount()),
                    valueOrZero(row.getOrderCount())));
        }
        return result;
    }

    /**
     * 查询指定日期范围内的会员分析。
     *
     * @param startDate 开始日期，为空时默认结束日期前29天
     * @param endDate 结束日期，为空时默认当天
     * @return 会员分析报表
     * @author Henfon
     * @date 2026-09-01
     */
    public ReportingMemberAnalysisResponse queryMemberAnalysis(LocalDate startDate, LocalDate endDate) {
        DateRange range = resolveRange(startDate, endDate);
        Long totalMemberCount = reportingMetricsMapper.countMembersBefore(range.endTime());
        Long newMemberCount = reportingMetricsMapper.countMembers(range.startTime(), range.endTime());
        List<ReportingMemberLevelStatRow> rows = reportingMetricsMapper.listMemberLevelStats(
                range.startTime(), range.endTime());

        long activeMemberCount = 0L;
        long paidOrderCount = 0L;
        BigDecimal paidAmount = BigDecimal.ZERO;
        List<ReportingMemberLevelStat> levelStats = new ArrayList<>();
        if (rows != null) {
            for (ReportingMemberLevelStatRow row : rows) {
                if (row == null) {
                    continue;
                }
                activeMemberCount += valueOrZero(row.getActiveMemberCount());
                paidOrderCount += valueOrZero(row.getPaidOrderCount());
                paidAmount = paidAmount.add(amountOrZero(row.getPaidAmount()));
                levelStats.add(new ReportingMemberLevelStat(
                        Objects.requireNonNullElse(row.getMemberLevel(), "UNKNOWN"),
                        valueOrZero(row.getMemberCount()), valueOrZero(row.getActiveMemberCount()),
                        valueOrZero(row.getPaidOrderCount()), amountOrZero(row.getPaidAmount())));
            }
        }
        long repeatPurchaseMemberCount = valueOrZero(reportingMetricsMapper.countRepeatMembers(
                range.startTime(), range.endTime()));
        BigDecimal repurchaseRate = activeMemberCount == 0L ? BigDecimal.ZERO
                : BigDecimal.valueOf(repeatPurchaseMemberCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(activeMemberCount), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal averageOrderAmount = paidOrderCount == 0L ? BigDecimal.ZERO
                : paidAmount.divide(BigDecimal.valueOf(paidOrderCount), 2, java.math.RoundingMode.HALF_UP);
        return new ReportingMemberAnalysisResponse(range.startDate(), range.endDate(),
                valueOrZero(totalMemberCount), valueOrZero(newMemberCount), activeMemberCount,
                repeatPurchaseMemberCount, repurchaseRate, paidOrderCount, paidAmount,
                averageOrderAmount, levelStats);
    }

    /**
     * 查询指定日期范围内的支付渠道统计。
     *
     * @param startDate 开始日期，为空时默认结束日期前29天
     * @param endDate 结束日期，为空时默认当天
     * @return 支付渠道统计列表
     * @author Henfon
     * @date 2026-09-04
     */
    public List<ReportingChannelStat> queryChannelStats(LocalDate startDate, LocalDate endDate) {
        DateRange range = resolveRange(startDate, endDate);
        List<ReportingChannelStat> rows = reportingMetricsMapper.listChannelStats(
                range.startTime(), range.endTime());
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        // 清理历史脏数据中的空渠道，避免接口返回不可识别的统计分组。
        List<ReportingChannelStat> result = new ArrayList<>();
        for (ReportingChannelStat row : rows) {
            if (row == null || row.channel() == null || row.channel().isBlank()) {
                continue;
            }
            result.add(new ReportingChannelStat(row.channel().trim().toUpperCase(Locale.ROOT),
                    Math.max(0L, row.paymentOrderCount()), amountOrZero(row.paidAmount())));
        }
        return result;
    }

    /**
     * 规范化报表日期范围并限制最大查询窗口。
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 日期范围
     * @author Henfon
     * @date 2026-09-01
     */
    private DateRange resolveRange(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now();
        LocalDate queryEnd = endDate == null ? today : endDate;
        LocalDate queryStart = startDate == null ? queryEnd.minusDays(29) : startDate;
        if (queryStart.isAfter(queryEnd)) {
            throw new BusinessException("REPORTING_DATE_RANGE_INVALID", "报表开始日期不能晚于结束日期");
        }
        if (queryEnd.isAfter(today)) {
            throw new BusinessException("REPORTING_DATE_INVALID", "报表结束日期不能晚于今天");
        }
        if (ChronoUnit.DAYS.between(queryStart, queryEnd) > 365) {
            throw new BusinessException("REPORTING_DATE_RANGE_TOO_LARGE", "报表查询范围最多支持366天");
        }
        return new DateRange(queryStart, queryEnd, queryStart.atStartOfDay(), queryEnd.plusDays(1).atStartOfDay());
    }

    /**
     * 报表日期范围内部值对象。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    private record DateRange(LocalDate startDate, LocalDate endDate,
                             LocalDateTime startTime, LocalDateTime endTime) {
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
