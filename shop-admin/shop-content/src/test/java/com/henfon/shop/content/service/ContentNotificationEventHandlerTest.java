package com.henfon.shop.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.henfon.shop.content.dto.NotificationEventRequest;
import com.henfon.shop.integration.messaging.DomainEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * RocketMQ 领域事件通知处理器单元测试。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@ExtendWith(MockitoExtension.class)
class ContentNotificationEventHandlerTest {

    @Mock
    private ContentNotificationService notificationService;

    /**
     * 校验支付成功事件能生成幂等通知请求。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldHandlePaymentEventWithEventIdDedupeKey() {
        ContentNotificationEventHandler handler = new ContentNotificationEventHandler(notificationService,
                new ObjectMapper());
        handler.handle(new DomainEvent("evt-1", "PAYMENT_SUCCEEDED", "11", Instant.now(),
                "{\"memberId\":21,\"orderId\":11,\"orderNo\":\"ORD-11\"}"));

        ArgumentCaptor<NotificationEventRequest> captor = ArgumentCaptor.forClass(NotificationEventRequest.class);
        verify(notificationService).saveEvent(captor.capture());
        NotificationEventRequest request = captor.getValue();
        assertEquals(21L, request.memberId());
        assertEquals("PAYMENT_SUCCEEDED", request.eventType());
        assertEquals("ROCKETMQ:evt-1", request.dedupeKey());
    }

    /**
     * 校验缺少会员归属的脏事件抛出异常以触发 RocketMQ 重试或死信。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldRejectEventWithoutMemberId() {
        ContentNotificationEventHandler handler = new ContentNotificationEventHandler(notificationService,
                new ObjectMapper());

        assertThrows(IllegalArgumentException.class, () -> handler.handle(new DomainEvent(
                "evt-invalid", "ORDER_SHIPPED", "11", Instant.now(), "{\"orderId\":11}")));
        verifyNoInteractions(notificationService);
    }

    /**
     * 校验消费者不会吞掉处理异常，RocketMQ 才能执行重试/DLQ。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldPropagateListenerFailureForRetry() {
        ContentNotificationEventHandler handler = org.mockito.Mockito.mock(ContentNotificationEventHandler.class);
        doThrow(new IllegalStateException("temporary failure")).when(handler).handle(org.mockito.ArgumentMatchers.any());
        PaymentSucceededNotificationListener listener = new PaymentSucceededNotificationListener(handler);

        assertThrows(IllegalStateException.class, () -> listener.onMessage(new DomainEvent(
                "evt-retry", "PAYMENT_SUCCEEDED", "11", Instant.now(), "{}")));
    }
}
