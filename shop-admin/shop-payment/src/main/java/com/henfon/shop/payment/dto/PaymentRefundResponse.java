package com.henfon.shop.payment.dto;

import com.henfon.shop.payment.entity.PaymentRefundOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单响应。
 *
 * @author Henfon
 * @date 2026-08-30
 */
public record PaymentRefundResponse(
        Long id,
        String refundNo,
        String paymentNo,
        Long orderId,
        String orderNo,
        BigDecimal amount,
        String reason,
        Integer status,
        String transactionNo,
        LocalDateTime requestedAt,
        LocalDateTime refundedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * 将退款单实体转换为接口响应。
     *
     * @param refundOrder 退款单实体
     * @return 退款单响应
     * @author Henfon
     * @date 2026-08-30
     */
    public static PaymentRefundResponse from(PaymentRefundOrder refundOrder) {
        // 不向接口返回回调原文，避免泄露渠道签名和敏感字段。
        return new PaymentRefundResponse(refundOrder.getId(), refundOrder.getRefundNo(), refundOrder.getPaymentNo(),
                refundOrder.getOrderId(), refundOrder.getOrderNo(), refundOrder.getAmount(), refundOrder.getReason(),
                refundOrder.getStatus(), refundOrder.getTransactionNo(), refundOrder.getRequestedAt(),
                refundOrder.getRefundedAt(), refundOrder.getCreatedAt(), refundOrder.getUpdatedAt());
    }
}
