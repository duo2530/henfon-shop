package com.henfon.shop.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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
        String rawPayload) {
}
