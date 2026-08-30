package com.henfon.shop.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建支付单请求。
 *
 * @author Henfon
 * @date 2026-08-30
 */
public record PaymentCreateRequest(
        @NotBlank(message = "支付渠道不能为空")
        @Size(max = 32, message = "支付渠道长度不能超过32个字符")
        String channel) {
}
