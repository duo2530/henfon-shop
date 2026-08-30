package com.henfon.shop.payment.dto;

import com.henfon.shop.payment.entity.PaymentOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 门户支付单响应，不暴露回调原文等内部字段。
 *
 * @author Henfon
 * @date 2026-08-30
 */
public record PaymentOrderResponse(
        Long id,
        String paymentNo,
        Long orderId,
        String orderNo,
        String channel,
        Integer status,
        BigDecimal amount,
        String transactionNo,
        LocalDateTime paidAt,
        LocalDateTime expireAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * 将支付单实体转换为门户响应。
     *
     * @param paymentOrder 支付单实体
     * @return 脱敏后的支付单响应
     * @author Henfon
     * @date 2026-08-30
     */
    public static PaymentOrderResponse from(PaymentOrder paymentOrder) {
        // 回调原文可能包含签名和敏感信息，只保留支付查询所需字段。
        return new PaymentOrderResponse(paymentOrder.getId(), paymentOrder.getPaymentNo(), paymentOrder.getOrderId(),
                paymentOrder.getOrderNo(), paymentOrder.getChannel(), paymentOrder.getStatus(), paymentOrder.getAmount(),
                paymentOrder.getTransactionNo(), paymentOrder.getPaidAt(), paymentOrder.getExpireAt(),
                paymentOrder.getCreatedAt(), paymentOrder.getUpdatedAt());
    }
}
