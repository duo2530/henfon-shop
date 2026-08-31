package com.henfon.shop.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.henfon.shop.integration.messaging.DomainEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 售后审核退款单消息消费者测试。
 *
 * @author Henfon
 * @date 2026-08-31
 */
class PaymentAfterSaleApprovedRefundListenerTest {

    /**
     * 校验仅退款审核事件会以售后单号生成稳定幂等键。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldCreateRefundWithAfterSaleIdempotencyKey() {
        PaymentRefundService refundService = mock(PaymentRefundService.class);
        PaymentAfterSaleApprovedRefundListener listener = new PaymentAfterSaleApprovedRefundListener(
                new ObjectMapper(), refundService);
        DomainEvent event = new DomainEvent("evt-1", "AFTER_SALE_APPROVED", "42", Instant.now(),
                "{\"afterSaleNo\":\"AS-42\",\"orderId\":42,\"afterSaleType\":1,\"refundAmount\":\"30.00\"}");

        listener.onMessage(event);

        ArgumentCaptor<com.henfon.shop.payment.dto.PaymentRefundCreateRequest> captor =
                ArgumentCaptor.forClass(com.henfon.shop.payment.dto.PaymentRefundCreateRequest.class);
        verify(refundService).create(captor.capture());
        assertEquals(42L, captor.getValue().orderId());
        assertEquals(new BigDecimal("30.00"), captor.getValue().amount());
        assertEquals("AFTER_SALE:AS-42", captor.getValue().idempotencyKey());
    }

    /**
     * 校验退货退款审核事件暂不创建原路退款单。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldIgnoreReturnRefundEvent() {
        PaymentRefundService refundService = mock(PaymentRefundService.class);
        PaymentAfterSaleApprovedRefundListener listener = new PaymentAfterSaleApprovedRefundListener(
                new ObjectMapper(), refundService);
        DomainEvent event = new DomainEvent("evt-2", "AFTER_SALE_APPROVED", "43", Instant.now(),
                "{\"afterSaleNo\":\"AS-43\",\"orderId\":43,\"afterSaleType\":2,\"refundAmount\":\"30.00\"}");

        listener.onMessage(event);

        org.mockito.Mockito.verifyNoInteractions(refundService);
    }
}
