package com.henfon.shop.payment.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.payment.entity.PaymentOrder;
import com.henfon.shop.payment.entity.PaymentRefundOrder;
import com.henfon.shop.payment.dto.PaymentReconciliationActionRequest;
import com.henfon.shop.payment.dto.PaymentReconciliationRecord;
import com.henfon.shop.payment.mapper.PaymentOrderMapper;
import com.henfon.shop.payment.mapper.PaymentRefundOrderMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
     * 验证流式导出跨页推送时既不漏行也不错序，退款行按时间插在支付行之间。
     *
     * @author Henfon
     * @date 2026-09-16
     */
    @Test
    void shouldStreamExportRecordsAcrossPagesInSettledTimeDescOrder() {
        LocalDateTime base = LocalDateTime.of(2026, 9, 1, 12, 0);
        // 按结算时间倒序生成 501 笔支付，正好超过一页（500），用来验证第二页会被读取。
        List<PaymentOrder> payments = new ArrayList<>();
        for (int index = 501; index >= 1; index--) {
            payments.add(payment(index, "PAY-" + index, base.plusMinutes(index),
                    base.minusHours(1), 2, "100.00"));
        }
        PaymentRefundOrder refund = refund("REF-MID", "PAY-1", "10.00");
        refund.setId(9001L);
        // 退款时间落在第 251 与第 250 笔支付之间，命中则说明归并是交错取行而不是先付后退。
        refund.setRefundedAt(base.plusMinutes(250).plusSeconds(30));
        when(paymentOrderMapper.selectPage(any(), any()))
                .thenAnswer(invocation -> pageOf(payments, invocation.getArgument(0)));
        when(refundOrderMapper.selectPage(any(), any()))
                .thenAnswer(invocation -> pageOf(List.of(refund), invocation.getArgument(0)));
        when(paymentOrderMapper.selectList(any(Wrapper.class))).thenReturn(payments);

        List<String> ids = streamIds(null, null, null);

        assertEquals(502, ids.size());
        assertEquals("pay-501", ids.get(0));
        assertEquals("pay-251", ids.get(250));
        assertEquals("refund-9001", ids.get(251));
        assertEquals("pay-250", ids.get(252));
        assertEquals("pay-1", ids.get(501));
        // 501 笔支付需要两页，第二页确实被读取。
        verify(paymentOrderMapper, times(2)).selectPage(any(), any());
    }

    /**
     * 验证支付与退款结算时间相同时支付行排在前面，与内存排序的稳定语义一致。
     *
     * @author Henfon
     * @date 2026-09-16
     */
    @Test
    void shouldPreferPaymentWhenSettledTimeTies() {
        LocalDateTime settledAt = LocalDateTime.of(2026, 9, 2, 9, 30);
        PaymentOrder payment = payment(11L, "PAY-TIE", settledAt, settledAt, 2, "80.00");
        PaymentRefundOrder refund = refund("REF-TIE", "PAY-TIE", "30.00");
        refund.setId(12L);
        refund.setRefundedAt(settledAt);
        when(paymentOrderMapper.selectPage(any(), any()))
                .thenAnswer(invocation -> pageOf(List.of(payment), invocation.getArgument(0)));
        when(refundOrderMapper.selectPage(any(), any()))
                .thenAnswer(invocation -> pageOf(List.of(refund), invocation.getArgument(0)));
        when(paymentOrderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(payment));

        assertEquals(List.of("pay-11", "refund-12"), streamIds(null, null, null));
    }

    /**
     * 验证缺结算时间的行排在末尾，且支付单在未支付时会退回创建时间参与排序。
     *
     * @author Henfon
     * @date 2026-09-16
     */
    @Test
    void shouldPlaceRecordsWithoutSettledTimeAtTheEnd() {
        LocalDateTime newest = LocalDateTime.of(2026, 9, 3, 10, 0);
        // 在途支付单没有支付时间，排序应使用创建时间而不是被丢弃或排在前面。
        PaymentOrder processing = payment(21L, "PAY-PROCESSING", null, newest, 1, "50.00");
        PaymentOrder succeeded = payment(22L, "PAY-DONE", newest.minusHours(2), newest.minusHours(3), 2, "60.00");
        PaymentOrder stale = payment(23L, "PAY-STALE", null, null, 0, "70.00");
        when(paymentOrderMapper.selectPage(any(), any()))
                .thenAnswer(invocation -> pageOf(List.of(processing, succeeded, stale), invocation.getArgument(0)));

        assertEquals(List.of("pay-21", "pay-22", "pay-23"), streamIds(null, "order_income", null));
    }

    /**
     * 验证只导出收款流水时不会去查退款数据。
     *
     * @author Henfon
     * @date 2026-09-16
     */
    @Test
    void shouldSkipRefundQueryWhenOnlyPaymentTypeRequested() {
        PaymentOrder payment = payment(31L, "PAY-ONLY", LocalDateTime.of(2026, 9, 4, 8, 0),
                LocalDateTime.of(2026, 9, 4, 7, 0), 2, "88.00");
        when(paymentOrderMapper.selectPage(any(), any()))
                .thenAnswer(invocation -> pageOf(List.of(payment), invocation.getArgument(0)));

        assertEquals(List.of("pay-31"), streamIds(null, "order_income", null));
        verify(refundOrderMapper, never()).selectOverRefundPaymentNos();
        verify(refundOrderMapper, never()).selectPage(any(), any());
    }

    /**
     * 验证超额退款的判断改为库侧聚合后，退款行状态与内存聚合口径一致。
     *
     * @author Henfon
     * @date 2026-09-16
     */
    @Test
    void shouldApplyOverRefundSetFromAggregationQuery() {
        PaymentOrder payment = payment(41L, "PAY-OVER", LocalDateTime.of(2026, 9, 5, 10, 0),
                LocalDateTime.of(2026, 9, 5, 9, 0), 2, "100.00");
        PaymentRefundOrder refund = refund("REF-OVER", "PAY-OVER", "150.00");
        refund.setId(42L);
        refund.setRefundedAt(LocalDateTime.of(2026, 9, 5, 11, 0));
        when(refundOrderMapper.selectOverRefundPaymentNos()).thenReturn(List.of("PAY-OVER"));
        when(paymentOrderMapper.selectPage(any(), any()))
                .thenAnswer(invocation -> pageOf(List.of(payment), invocation.getArgument(0)));
        when(refundOrderMapper.selectPage(any(), any()))
                .thenAnswer(invocation -> pageOf(List.of(refund), invocation.getArgument(0)));
        when(paymentOrderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(payment));

        List<PaymentReconciliationRecord> records = new ArrayList<>();
        service.forEachExportRecord(null, null, "discrepancy", records::add);

        // 退款时间最新所以排在前面；超额退款被判为差异，而不是关联到成功支付单后就认作平账。
        assertEquals(1, records.size());
        assertEquals("refund-42", records.get(0).id());
    }

    /**
     * 验证关键字与状态筛选在流式路径上同样生效。
     *
     * @author Henfon
     * @date 2026-09-16
     */
    @Test
    void shouldFilterStreamedRecordsByKeywordAndStatus() {
        PaymentOrder matched = payment(51L, "PAY-ALPHA", LocalDateTime.of(2026, 9, 6, 10, 0),
                LocalDateTime.of(2026, 9, 6, 9, 0), 2, "10.00");
        PaymentOrder other = payment(52L, "PAY-BETA", LocalDateTime.of(2026, 9, 6, 8, 0),
                LocalDateTime.of(2026, 9, 6, 7, 0), 2, "20.00");
        when(paymentOrderMapper.selectPage(any(), any()))
                .thenAnswer(invocation -> pageOf(List.of(matched, other), invocation.getArgument(0)));

        assertEquals(List.of("pay-51"), streamIds("ALPHA", "order_income", null));
        assertEquals(List.of("pay-51", "pay-52"), streamIds(null, "order_income", null));
        assertEquals(List.of(), streamIds(null, "order_income", "discrepancy"));
    }

    /**
     * 收集流式导出的记录 ID。
     *
     * @param keyword 关键字
     * @param type 流水类型
     * @param status 对账状态
     * @return 记录 ID 序列
     * @author Henfon
     * @date 2026-09-16
     */
    private List<String> streamIds(String keyword, String type, String status) {
        List<String> ids = new ArrayList<>();
        service.forEachExportRecord(keyword, type, status, record -> ids.add(record.id()));
        return ids;
    }

    /**
     * 按请求的页码与页大小切分固定数据集，模拟真实分页查询。
     *
     * @param all 全量数据，顺序即数据库返回顺序
     * @param request 分页请求
     * @param <T> 行类型
     * @return 分页结果
     * @author Henfon
     * @date 2026-09-16
     */
    private static <T> Page<T> pageOf(List<T> all, Page<T> request) {
        long size = request.getSize();
        long current = request.getCurrent();
        int from = (int) Math.min((current - 1) * size, all.size());
        int to = (int) Math.min(from + size, all.size());
        Page<T> result = new Page<>(current, size);
        result.setRecords(new ArrayList<>(all.subList(from, to)));
        return result;
    }

    /**
     * 构造支付单测试数据。
     *
     * @param id 主键
     * @param paymentNo 支付单号
     * @param paidAt 支付时间
     * @param createdAt 创建时间
     * @param status 支付状态
     * @param amount 金额
     * @return 支付单实体
     * @author Henfon
     * @date 2026-09-16
     */
    private static PaymentOrder payment(long id, String paymentNo, LocalDateTime paidAt,
                                        LocalDateTime createdAt, int status, String amount) {
        PaymentOrder payment = new PaymentOrder();
        payment.setId(id);
        payment.setPaymentNo(paymentNo);
        payment.setOrderNo("ORD-" + id);
        payment.setChannel("WECHAT");
        payment.setStatus(status);
        payment.setAmount(new BigDecimal(amount));
        payment.setPaidAt(paidAt);
        payment.setCreatedAt(createdAt);
        return payment;
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
