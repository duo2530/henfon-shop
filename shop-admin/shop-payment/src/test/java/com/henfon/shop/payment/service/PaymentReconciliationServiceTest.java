package com.henfon.shop.payment.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.henfon.shop.payment.entity.PaymentOrder;
import com.henfon.shop.payment.entity.PaymentRefundOrder;
import com.henfon.shop.payment.mapper.PaymentOrderMapper;
import com.henfon.shop.payment.mapper.PaymentRefundOrderMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 财务对账流水聚合测试。
 *
 * @author Henfon
 * @date 2026-09-01
 */
class PaymentReconciliationServiceTest {

    private final PaymentOrderMapper paymentOrderMapper = mock(PaymentOrderMapper.class);
    private final PaymentRefundOrderMapper refundOrderMapper = mock(PaymentRefundOrderMapper.class);
    private final PaymentReconciliationService service = new PaymentReconciliationService(paymentOrderMapper, refundOrderMapper);

    /**
     * 验证支付和退款记录可以合并并按时间倒序分页。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldAggregatePaymentAndRefundRecords() {
        LocalDateTime now = LocalDateTime.now();
        PaymentOrder payment = new PaymentOrder();
        payment.setId(1L);
        payment.setPaymentNo("PAY-1");
        payment.setOrderNo("ORD-1");
        payment.setChannel("WECHAT");
        payment.setStatus(2);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setPaidAt(now.minusMinutes(2));
        PaymentRefundOrder refund = new PaymentRefundOrder();
        refund.setId(2L);
        refund.setRefundNo("REF-1");
        refund.setOrderNo("ORD-1");
        refund.setStatus(2);
        refund.setAmount(new BigDecimal("20.00"));
        refund.setRefundedAt(now);
        when(paymentOrderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(payment));
        when(refundOrderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(refund));

        var page = service.page(null, null, null, 1, 20);

        // 退款记录金额使用负数，且最新退款排在支付记录之前。
        assertEquals(2, page.getTotal());
        assertEquals("refund_payout", page.getRecords().get(0).type());
        assertEquals(new BigDecimal("-20.00"), page.getRecords().get(0).amount());
        assertEquals("order_income", page.getRecords().get(1).type());
    }
}
