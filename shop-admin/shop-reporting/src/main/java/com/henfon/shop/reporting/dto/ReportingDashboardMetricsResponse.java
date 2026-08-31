package com.henfon.shop.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 后台首页经营指标响应。
 *
 * <p>今日指标对应请求日期当天；累计指标对应截至请求日期结束时的有效数据。</p>
 *
 * @param date 指标日期
 * @param todayOrderCount 当日创建订单数
 * @param todaySalesAmount 当日有效销售额
 * @param todayProductCount 当日新增商品数
 * @param todayMemberCount 当日新增会员数
 * @param totalOrderCount 截止日期累计订单数
 * @param totalSalesAmount 截止日期累计有效销售额
 * @param totalProductCount 截止日期累计商品数
 * @param totalMemberCount 截止日期累计会员数
 * @author Henfon
 * @date 2026-08-31
 */
public record ReportingDashboardMetricsResponse(
        LocalDate date,
        long todayOrderCount,
        BigDecimal todaySalesAmount,
        long todayProductCount,
        long todayMemberCount,
        long totalOrderCount,
        BigDecimal totalSalesAmount,
        long totalProductCount,
        long totalMemberCount) {
}
