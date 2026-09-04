package com.henfon.shop.payment.dto;

/**
 * 对账差异人工处理请求。
 *
 * @author Henfon
 * @date 2026-09-04
 */
public record PaymentReconciliationActionRequest(
        String action,
        String remark,
        String matchPaymentNo) {
}
