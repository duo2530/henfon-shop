package com.henfon.shop.payment.service;

import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.payment.dto.PaymentNotifyRequest;
import com.henfon.shop.payment.entity.PaymentOrder;
import com.henfon.shop.payment.mapper.PaymentOrderMapper;
import com.henfon.shop.trade.service.TradeOrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付单应用服务单元测试。
 *
 * @author Henfon
 * @date 2026-09-04
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentOrderMapper paymentOrderMapper;

    @Mock
    private TradeOrderService tradeOrderService;

    /**
     * 校验支付回调金额不一致时拒绝推进订单状态。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldRejectPaymentCallbackWhenAmountMismatches() {
        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setPaymentNo("PAY-1");
        paymentOrder.setAmount(new BigDecimal("10.00"));
        paymentOrder.setStatus(0);
        when(paymentOrderMapper.selectOne(any())).thenReturn(paymentOrder);

        PaymentService service = new PaymentService(paymentOrderMapper, tradeOrderService, 30);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.notifyPayment(new PaymentNotifyRequest("PAY-1", "WX-1", "{}",
                        new BigDecimal("9.99"))));

        assertEquals("PAYMENT_AMOUNT_MISMATCH", exception.getCode());
        verify(paymentOrderMapper, never()).updateById(any(PaymentOrder.class));
        verify(tradeOrderService, never()).markPaid(any(), any(), any(), any());
    }

    /**
     * 校验服务层收到空回调请求时返回明确业务错误，避免空指针。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldRejectNullPaymentCallbackRequest() {
        PaymentService service = new PaymentService(paymentOrderMapper, tradeOrderService, 30);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.notifyPayment(null));

        assertEquals("PAYMENT_NOTIFY_INVALID", exception.getCode());
        verify(paymentOrderMapper, never()).selectOne(any());
    }

    /**
     * 校验服务层收到空白第三方交易号时拒绝入账，防止产生脏支付记录。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldRejectBlankTransactionNo() {
        PaymentService service = new PaymentService(paymentOrderMapper, tradeOrderService, 30);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.notifyPayment(new PaymentNotifyRequest("PAY-1", "  ", "{}")));

        assertEquals("PAYMENT_NOTIFY_INVALID", exception.getCode());
        verify(paymentOrderMapper, never()).selectOne(any());
    }
}
