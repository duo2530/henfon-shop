package com.henfon.shop.payment.dto;

import com.henfon.shop.payment.entity.PaymentInvoice;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发票申请响应。
 *
 * @author Henfon
 * @date 2026-08-31
 */
public record PaymentInvoiceResponse(
        Long id,
        String invoiceNo,
        Long orderId,
        String orderNo,
        Integer invoiceType,
        String title,
        String taxNo,
        String email,
        BigDecimal amount,
        Integer status,
        String invoiceUrl,
        String failureReason,
        LocalDateTime requestedAt,
        LocalDateTime issuedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * 将发票实体转换为接口响应。
     *
     * @param invoice 发票实体
     * @return 发票响应
     * @author Henfon
     * @date 2026-08-31
     */
    public static PaymentInvoiceResponse from(PaymentInvoice invoice) {
        return new PaymentInvoiceResponse(invoice.getId(), invoice.getInvoiceNo(), invoice.getOrderId(),
                invoice.getOrderNo(), invoice.getInvoiceType(), invoice.getTitle(), invoice.getTaxNo(),
                invoice.getEmail(), invoice.getAmount(), invoice.getStatus(), invoice.getInvoiceUrl(),
                invoice.getFailureReason(), invoice.getRequestedAt(), invoice.getIssuedAt(),
                invoice.getCreatedAt(), invoice.getUpdatedAt());
    }
}
