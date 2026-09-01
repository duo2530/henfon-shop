package com.henfon.shop.reporting.dto;

import java.math.BigDecimal;

/**
 * 会员等级消费分析项。
 *
 * @param memberLevel 会员等级
 * @param memberCount 会员总数
 * @param activeMemberCount 期间有支付行为的会员数
 * @param paidOrderCount 有效支付订单数
 * @param paidAmount 有效支付金额
 * @author Henfon
 * @date 2026-09-01
 */
public record ReportingMemberLevelStat(
        String memberLevel,
        long memberCount,
        long activeMemberCount,
        long paidOrderCount,
        BigDecimal paidAmount) {
}
