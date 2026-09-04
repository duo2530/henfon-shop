package com.henfon.shop.payment.service;

import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.payment.entity.PaymentOrder;
import com.henfon.shop.payment.entity.PaymentRefundOrder;
import com.henfon.shop.payment.dto.PaymentRefundCreateRequest;
import com.henfon.shop.payment.mapper.PaymentOrderMapper;
import com.henfon.shop.payment.mapper.PaymentRefundOrderMapper;
import com.henfon.shop.payment.dto.PaymentRefundNotifyRequest;
import com.henfon.shop.payment.dto.PaymentRefundResponse;
import com.henfon.shop.trade.service.TradeAfterSaleService;
import com.henfon.shop.trade.service.TradeOrderService;
import com.henfon.shop.integration.messaging.RocketMqEventPublisher;
import com.henfon.shop.integration.messaging.RocketMqTopics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.argThat;

/**
 * 退款单应用服务单元测试。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@ExtendWith(MockitoExtension.class)
class PaymentRefundServiceTest {

    @Mock
    private PaymentOrderMapper paymentOrderMapper;

    @Mock
    private PaymentRefundOrderMapper refundOrderMapper;

    @Mock
    private TradeOrderService tradeOrderService;

    @Mock
    private TradeAfterSaleService tradeAfterSaleService;

    @Mock
    private RocketMqEventPublisher eventPublisher;

    /**
     * 校验部分退款成功时完成对应仅退款售后，但不提前结束订单。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldCompleteAfterSaleForPartialRefund() {
        PaymentRefundOrder refund = refundOrder(10L, "REF-10", 20L, "PAY-20", 30L);
        PaymentOrder payment = paymentOrder("PAY-20", new BigDecimal("100.00"));
        when(refundOrderMapper.selectOne(any())).thenReturn(refund);
        when(refundOrderMapper.updateById(any(PaymentRefundOrder.class))).thenReturn(1);
        when(paymentOrderMapper.selectOne(any())).thenReturn(payment);
        when(refundOrderMapper.selectList(any())).thenReturn(List.of(refund));

        PaymentRefundService service = new PaymentRefundService(paymentOrderMapper, refundOrderMapper,
                tradeOrderService, tradeAfterSaleService);
        service.notifyRefund(new PaymentRefundNotifyRequest("REF-10", "WX-10", true, "{}"));

        verify(tradeAfterSaleService).markRefundSucceeded(eq(20L), eq(new BigDecimal("30.00")));
        verify(tradeOrderService, never()).markRefunded(any(), any());
        assertEquals(2, refund.getStatus());
    }

    /**
     * 校验部分退款成功时发布累计金额事件，驱动营销优惠券分摊。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldPublishPartialRefundEventForMarketing() {
        PaymentRefundOrder refund = refundOrder(10L, "REF-10", 20L, "PAY-20", 30L);
        refund.setMemberId(7L);
        PaymentOrder payment = paymentOrder("PAY-20", new BigDecimal("100.00"));
        when(refundOrderMapper.selectOne(any())).thenReturn(refund);
        when(refundOrderMapper.updateById(any(PaymentRefundOrder.class))).thenReturn(1);
        when(paymentOrderMapper.selectOne(any())).thenReturn(payment);
        when(refundOrderMapper.selectList(any())).thenReturn(List.of(refund));

        PaymentRefundService service = new PaymentRefundService(paymentOrderMapper, refundOrderMapper,
                tradeOrderService, tradeAfterSaleService, eventPublisher);
        service.notifyRefund(new PaymentRefundNotifyRequest("REF-10", "WX-10", true, "{}"));

        verify(eventPublisher).publish(eq(RocketMqTopics.PARTIAL_REFUND_SUCCEEDED), argThat(event ->
                event.payload().toString().contains("\"refundAmount\":30.00")
                        && event.payload().toString().contains("\"paidAmount\":100.00")));
    }

    /**
     * 校验重复回调使用不同第三方交易号时拒绝处理，防止回调重放污染退款单。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldRejectTerminalCallbackWithDifferentTransactionNo() {
        PaymentRefundOrder refund = refundOrder(10L, "REF-10", 20L, "PAY-20", 30L);
        refund.setStatus(2);
        refund.setTransactionNo("WX-ORIGINAL");
        when(refundOrderMapper.selectOne(any())).thenReturn(refund);

        PaymentRefundService service = new PaymentRefundService(paymentOrderMapper, refundOrderMapper,
                tradeOrderService, tradeAfterSaleService);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.notifyRefund(new PaymentRefundNotifyRequest("REF-10", "WX-REPLAY", true, "{}")));

        assertEquals("PAYMENT_REFUND_NOTIFY_CONFLICT", exception.getCode());
        verify(refundOrderMapper, never()).updateById(any(PaymentRefundOrder.class));
    }

    /**
     * 校验渠道退款金额不一致时拒绝回调，防止错误金额推进退款状态。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldRejectRefundCallbackWhenAmountMismatches() {
        PaymentRefundOrder refund = refundOrder(10L, "REF-10", 20L, "PAY-20", 30L);
        when(refundOrderMapper.selectOne(any())).thenReturn(refund);

        PaymentRefundService service = new PaymentRefundService(paymentOrderMapper, refundOrderMapper,
                tradeOrderService, tradeAfterSaleService);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.notifyRefund(new PaymentRefundNotifyRequest("REF-10", "WX-10", true, "{}",
                        new BigDecimal("29.99"))));

        assertEquals("PAYMENT_REFUND_AMOUNT_MISMATCH", exception.getCode());
        verify(refundOrderMapper, never()).updateById(any(PaymentRefundOrder.class));
    }

    /**
     * 校验失败回调释放处理中售后目标，允许会员重新申请。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldReleaseAfterSaleWhenRefundFails() {
        PaymentRefundOrder refund = refundOrder(10L, "REF-10", 20L, "PAY-20", 30L);
        when(refundOrderMapper.selectOne(any())).thenReturn(refund);
        when(refundOrderMapper.updateById(any(PaymentRefundOrder.class))).thenReturn(1);

        PaymentRefundService service = new PaymentRefundService(paymentOrderMapper, refundOrderMapper,
                tradeOrderService, tradeAfterSaleService);
        service.notifyRefund(new PaymentRefundNotifyRequest("REF-10", "WX-10", false, "{}"));

        verify(tradeAfterSaleService).markRefundFailed(eq(20L), eq(new BigDecimal("30.00")));
        assertEquals(3, refund.getStatus());
    }

    /**
     * 校验同一幂等键复用时请求金额或原因不一致会被拒绝。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldRejectIdempotencyKeyWithDifferentRequest() {
        PaymentRefundOrder refund = refundOrder(10L, "REF-10", 20L, "PAY-20", 30L);
        refund.setIdempotencyKey("AFTER_SALE:AS-10");
        refund.setReason("原申请原因");
        when(refundOrderMapper.selectOne(any())).thenReturn(refund);

        PaymentRefundService service = new PaymentRefundService(paymentOrderMapper, refundOrderMapper,
                tradeOrderService, tradeAfterSaleService);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(new PaymentRefundCreateRequest(
                        20L, new BigDecimal("30.00"), "新申请原因", "AFTER_SALE:AS-10")));

        assertEquals("PAYMENT_REFUND_IDEMPOTENCY_CONFLICT", exception.getCode());
    }

    /**
     * 校验订单不存在时创建退款单返回明确业务错误。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldRejectRefundWhenOrderMissing() {
        when(tradeOrderService.findById(99L)).thenReturn(null);

        PaymentRefundService service = new PaymentRefundService(paymentOrderMapper, refundOrderMapper,
                tradeOrderService, tradeAfterSaleService);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(new PaymentRefundCreateRequest(
                        99L, new BigDecimal("10.00"), "订单取消退款", null)));

        assertEquals("TRADE_ORDER_NOT_FOUND", exception.getCode());
        verify(paymentOrderMapper, never()).selectOne(any());
    }

    /**
     * 校验渠道提交成功后退款单进入处理中，失败退款单可再次提交。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldMarkRefundProcessingAndAllowRetryFromFailed() {
        PaymentRefundOrder refund = refundOrder(11L, "REF-11", 21L, "PAY-21", 15L);
        refund.setStatus(3);
        when(refundOrderMapper.selectOne(any())).thenReturn(refund);
        when(refundOrderMapper.updateById(any(PaymentRefundOrder.class))).thenReturn(1);

        PaymentRefundService service = new PaymentRefundService(paymentOrderMapper, refundOrderMapper,
                tradeOrderService, tradeAfterSaleService);
        PaymentRefundResponse response = service.markProcessing("REF-11");

        assertEquals(1, response.status());
        assertEquals(1, refund.getStatus());
        verify(refundOrderMapper).updateById(refund);
    }

    /**
     * 创建退款单测试夹具。
     *
     * @param id 退款单ID
     * @param refundNo 退款单号
     * @param orderId 订单ID
     * @param paymentNo 支付单号
     * @param amount 退款金额（分）
     * @return 退款单实体
     * @author Henfon
     * @date 2026-08-31
     */
    private PaymentRefundOrder refundOrder(Long id, String refundNo, Long orderId, String paymentNo, long amount) {
        PaymentRefundOrder refund = new PaymentRefundOrder();
        refund.setId(id);
        refund.setRefundNo(refundNo);
        refund.setOrderId(orderId);
        refund.setPaymentNo(paymentNo);
        refund.setAmount(BigDecimal.valueOf(amount).setScale(2));
        refund.setStatus(0);
        refund.setVersion(0);
        return refund;
    }

    /**
     * 创建支付单测试夹具。
     *
     * @param paymentNo 支付单号
     * @param amount 支付金额
     * @return 支付单实体
     * @author Henfon
     * @date 2026-08-31
     */
    private PaymentOrder paymentOrder(String paymentNo, BigDecimal amount) {
        PaymentOrder payment = new PaymentOrder();
        payment.setPaymentNo(paymentNo);
        payment.setAmount(amount);
        payment.setStatus(2);
        return payment;
    }
}
