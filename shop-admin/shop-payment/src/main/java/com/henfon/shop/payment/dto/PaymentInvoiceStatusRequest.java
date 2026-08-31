package com.henfon.shop.payment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 后台更新发票状态请求。
 *
 * @author Henfon
 * @date 2026-08-31
 */
public record PaymentInvoiceStatusRequest(
        @NotNull @Min(0) @Max(4) Integer status,
        @Size(max = 1024) String invoiceUrl,
        @Size(max = 500) String failureReason) {
}
