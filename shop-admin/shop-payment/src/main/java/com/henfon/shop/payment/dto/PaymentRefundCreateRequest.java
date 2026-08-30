package com.henfon.shop.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 创建退款单请求。
 *
 * @author Henfon
 * @date 2026-08-30
 */
public record PaymentRefundCreateRequest(
        @NotNull(message = "订单ID不能为空")
        Long orderId,
        @DecimalMin(value = "0.01", message = "退款金额必须大于0")
        @NotNull(message = "退款金额不能为空")
        BigDecimal amount,
        @Size(max = 500, message = "退款原因长度不能超过500个字符")
        String reason,
        @Size(max = 128, message = "退款幂等键长度不能超过128个字符")
        String idempotencyKey) {
}
