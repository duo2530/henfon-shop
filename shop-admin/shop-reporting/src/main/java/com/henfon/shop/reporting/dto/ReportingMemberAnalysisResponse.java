package com.henfon.shop.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 会员分析报表响应。
 *
 * @param startDate 统计开始日期
 * @param endDate 统计结束日期
 * @param totalMemberCount 截止结束日期会员总数
 * @param newMemberCount 期间新增会员数
 * @param activeMemberCount 期间有支付行为的会员数
 * @param repeatPurchaseMemberCount 期间重复购买会员数
 * @param repurchaseRate 重复购买率（百分比）
 * @param paidOrderCount 有效支付订单数
 * @param paidAmount 有效支付金额
 * @param averageOrderAmount 平均客单价
 * @param levelStats 会员等级分布与消费明细
 * @author Henfon
 * @date 2026-09-01
 */
public record ReportingMemberAnalysisResponse(
        LocalDate startDate,
        LocalDate endDate,
        long totalMemberCount,
        long newMemberCount,
        long activeMemberCount,
        long repeatPurchaseMemberCount,
        BigDecimal repurchaseRate,
        long paidOrderCount,
        BigDecimal paidAmount,
        BigDecimal averageOrderAmount,
        List<ReportingMemberLevelStat> levelStats) {
}
