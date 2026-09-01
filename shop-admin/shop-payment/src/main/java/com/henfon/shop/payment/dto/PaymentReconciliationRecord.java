package com.henfon.shop.payment.dto;

import java.math.BigDecimal;

/**
 * 财务对账流水记录。
 *
 * @author Henfon
 * @date 2026-09-01
 */
public record PaymentReconciliationRecord(
        String id,
        String transNo,
        String orderNumber,
        String type,
        String channel,
        BigDecimal amount,
        BigDecimal fee,
        BigDecimal netAmount,
        String status,
        String settledAt,
        String accountNumber,
        String notes) {
}
