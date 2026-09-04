package com.henfon.shop.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 支付异步通知请求。
 *
 * @author Henfon
 * @date 2026-08-30
 */
public record PaymentNotifyRequest(
        @NotBlank(message = "支付单号不能为空")
        String paymentNo,
        @NotBlank(message = "第三方交易号不能为空")
        @Size(max = 128, message = "第三方交易号长度不能超过128个字符")
        String transactionNo,
        String rawPayload,
        BigDecimal amount) {

    /**
     * 兼容历史调用方构造支付通知请求，不携带渠道金额。
     *
     * @param paymentNo 支付单号
     * @param transactionNo 第三方交易号
     * @param rawPayload 原始通知报文
     * @author Henfon
     * @date 2026-09-04
     */
    public PaymentNotifyRequest(String paymentNo, String transactionNo, String rawPayload) {
        this(paymentNo, transactionNo, rawPayload, null);
    }
}
