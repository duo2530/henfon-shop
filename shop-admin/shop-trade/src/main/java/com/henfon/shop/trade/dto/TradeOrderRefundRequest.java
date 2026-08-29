package com.henfon.shop.trade.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 后台订单退款请求。
 *
 * @author Henfon
 * @date 2026-08-29
 */
public record TradeOrderRefundRequest(
        @NotNull(message = "退款金额不能为空")
        @DecimalMin(value = "0.01", message = "退款金额必须大于0")
        BigDecimal refundAmount,
        @NotBlank(message = "退款原因不能为空")
        @Size(max = 500, message = "退款原因长度不能超过500个字符")
        String reason) {
}
