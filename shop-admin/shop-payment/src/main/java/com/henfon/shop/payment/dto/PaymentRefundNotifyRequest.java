package com.henfon.shop.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 退款异步通知请求。
 *
 * @author Henfon
 * @date 2026-08-30
 */
public record PaymentRefundNotifyRequest(
        @NotBlank(message = "退款单号不能为空")
        String refundNo,
        @NotBlank(message = "第三方退款交易号不能为空")
        @Size(max = 128, message = "第三方退款交易号长度不能超过128个字符")
        String transactionNo,
        @NotNull(message = "退款结果不能为空")
        Boolean success,
        String rawPayload) {
}
