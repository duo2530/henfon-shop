package com.henfon.shop.reporting.dto;

import java.math.BigDecimal;

/**
 * 支付渠道经营统计。
 *
 * @param channel 渠道编码
 * @param paymentOrderCount 支付单数量
 * @param paidAmount 支付金额
 * @author Henfon
 * @date 2026-09-04
 */
public record ReportingChannelStat(String channel, long paymentOrderCount, BigDecimal paidAmount) {
}
