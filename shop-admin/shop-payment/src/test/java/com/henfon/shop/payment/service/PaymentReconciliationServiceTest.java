package com.henfon.shop.payment.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.henfon.shop.payment.entity.PaymentOrder;
import com.henfon.shop.payment.entity.PaymentRefundOrder;
import com.henfon.shop.payment.dto.PaymentReconciliationActionRequest;
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
import static org.mockito.Mockito.verify;

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

    /**
     * 验证退款成功但缺少对应成功支付单时标记为差异，避免孤儿退款被误判为平账。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldMarkOrphanSuccessfulRefundAsDiscrepancy() {
        PaymentRefundOrder refund = new PaymentRefundOrder();
        refund.setId(3L);
        refund.setRefundNo("REF-ORPHAN");
        refund.setPaymentNo("PAY-MISSING");
        refund.setOrderNo("ORD-ORPHAN");
        refund.setStatus(2);
        refund.setAmount(new BigDecimal("12.00"));
        refund.setRefundedAt(LocalDateTime.now());
        when(paymentOrderMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(refundOrderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(refund));

        var page = service.page(null, "refund_payout", null, 1, 20);

        // 渠道已退款但本地支付事实缺失，必须进入差异队列供人工核查。
        assertEquals(1, page.getTotal());
        assertEquals("discrepancy", page.getRecords().get(0).status());
    }

    /**
     * 验证同一支付单累计退款超过实付金额时，相关退款流水全部标记为差异。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldMarkOverRefundAsDiscrepancy() {
        PaymentOrder payment = new PaymentOrder();
        payment.setId(4L);
        payment.setPaymentNo("PAY-OVER");
        payment.setOrderNo("ORD-OVER");
        payment.setStatus(2);
        payment.setAmount(new BigDecimal("100.00"));

        PaymentRefundOrder first = refund("REF-OVER-1", "PAY-OVER", "60.00");
        PaymentRefundOrder second = refund("REF-OVER-2", "PAY-OVER", "50.00");
        when(paymentOrderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(payment));
        when(refundOrderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, second));

        var page = service.page(null, "refund_payout", null, 1, 20);

        // 累计退款超过收款金额时，不能将任一笔退款显示为已平账。
        assertEquals(2, page.getTotal());
        assertEquals(2, page.getRecords().stream().filter(record -> "discrepancy".equals(record.status())).count());
    }

    /**
     * 验证差异退款可以人工确认并保留处理备注。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldConfirmDiscrepancyRefundWithRemark() {
        PaymentRefundOrder refund = refund("REF-ACTION", "PAY-ACTION", "10.00");
        when(refundOrderMapper.selectById(refund.getId())).thenReturn(refund);
        var result = service.action("refund-" + refund.getId(),
                new PaymentReconciliationActionRequest("confirm", "人工核对渠道流水", null));

        assertEquals("reconciled", result.status());
        assertEquals("人工核对渠道流水", result.notes());
        verify(refundOrderMapper).updateById(refund);
    }

    /**
     * 构造成功退款测试数据。
     *
     * @param refundNo 退款单号
     * @param paymentNo 支付单号
     * @param amount 退款金额
     * @return 退款单实体
     * @author Henfon
     * @date 2026-09-04
     */
    private PaymentRefundOrder refund(String refundNo, String paymentNo, String amount) {
        PaymentRefundOrder refund = new PaymentRefundOrder();
        refund.setId((long) refundNo.hashCode());
        refund.setRefundNo(refundNo);
        refund.setPaymentNo(paymentNo);
        refund.setOrderNo("ORD-OVER");
        refund.setStatus(2);
        refund.setAmount(new BigDecimal(amount));
        refund.setRefundedAt(LocalDateTime.now());
        return refund;
    }
}
