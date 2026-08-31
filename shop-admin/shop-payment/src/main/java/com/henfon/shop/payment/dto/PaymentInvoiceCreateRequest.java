package com.henfon.shop.payment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 门户发票申请请求。
 *
 * @author Henfon
 * @date 2026-08-31
 */
public record PaymentInvoiceCreateRequest(
        @NotNull @Min(1) @Max(2) Integer invoiceType,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 64) String taxNo,
        @Email @Size(max = 128) String email,
        @Size(max = 128) String idempotencyKey) {
}
