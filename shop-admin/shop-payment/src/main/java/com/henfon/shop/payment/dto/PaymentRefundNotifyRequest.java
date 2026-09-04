package com.henfon.shop.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

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
        String rawPayload,
        BigDecimal amount) {

    /**
     * 兼容历史调用方构造退款通知请求，不携带渠道退款金额。
     *
     * @param refundNo 商户退款单号
     * @param transactionNo 第三方退款交易号
     * @param success 是否退款成功
     * @param rawPayload 原始通知报文
     * @author Henfon
     * @date 2026-09-04
     */
    public PaymentRefundNotifyRequest(String refundNo, String transactionNo, Boolean success, String rawPayload) {
        this(refundNo, transactionNo, success, rawPayload, null);
    }
}
