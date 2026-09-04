package com.henfon.shop.payment.service;

import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.payment.dto.PaymentInvoiceCreateRequest;
import com.henfon.shop.payment.dto.PaymentInvoiceResponse;
import com.henfon.shop.payment.dto.PaymentInvoiceStatusRequest;
import com.henfon.shop.payment.entity.PaymentInvoice;
import com.henfon.shop.payment.mapper.PaymentInvoiceMapper;
import com.henfon.shop.trade.entity.TradeOrder;
import com.henfon.shop.trade.service.TradeOrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 发票申请服务单元测试。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@ExtendWith(MockitoExtension.class)
class PaymentInvoiceServiceTest {

    @Mock
    private PaymentInvoiceMapper invoiceMapper;

    @Mock
    private TradeOrderService tradeOrderService;

    /**
     * 校验已支付订单可以创建发票申请并保存订单金额快照。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldCreateInvoiceForPaidOrder() {
        TradeOrder order = paidOrder(11L, 21L);
        when(tradeOrderService.findById(11L)).thenReturn(order);
        when(invoiceMapper.selectOne(any())).thenReturn(null);
        when(invoiceMapper.insert(any(PaymentInvoice.class))).thenAnswer(invocation -> {
            PaymentInvoice invoice = invocation.getArgument(0);
            invoice.setId(31L);
            return 1;
        });

        PaymentInvoiceService service = new PaymentInvoiceService(invoiceMapper, tradeOrderService);
        PaymentInvoiceResponse response = service.create(21L, 11L,
                new PaymentInvoiceCreateRequest(1, "  测试抬头 ", null, "test@example.com", null));

        assertEquals(31L, response.id());
        assertEquals(11L, response.orderId());
        assertEquals(new BigDecimal("199.00"), response.amount());
    }

    /**
     * 校验会员不能为其他会员的订单申请发票。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldRejectInvoiceForAnotherMemberOrder() {
        when(tradeOrderService.findById(11L)).thenReturn(paidOrder(11L, 21L));
        PaymentInvoiceService service = new PaymentInvoiceService(invoiceMapper, tradeOrderService);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(22L, 11L,
                        new PaymentInvoiceCreateRequest(1, "测试抬头", null, null, null)));

        assertEquals("PAYMENT_INVOICE_FORBIDDEN", exception.getCode());
    }

    /**
     * 校验未支付订单不能申请发票。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldRejectInvoiceForUnpaidOrder() {
        TradeOrder order = paidOrder(11L, 21L);
        order.setPaymentStatus(0);
        when(tradeOrderService.findById(11L)).thenReturn(order);
        PaymentInvoiceService service = new PaymentInvoiceService(invoiceMapper, tradeOrderService);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(21L, 11L,
                        new PaymentInvoiceCreateRequest(1, "测试抬头", null, null, null)));

        assertEquals("PAYMENT_INVOICE_ORDER_UNPAID", exception.getCode());
    }

    /**
     * 校验后台可以将待开票申请推进到开票中。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldAdvanceInvoiceStatus() {
        PaymentInvoice invoice = new PaymentInvoice();
        invoice.setId(31L);
        invoice.setInvoiceNo("INV-31");
        invoice.setStatus(0);
        invoice.setVersion(0);
        when(invoiceMapper.selectOne(any())).thenReturn(invoice);
        when(invoiceMapper.updateById(any(PaymentInvoice.class))).thenReturn(1);

        PaymentInvoiceService service = new PaymentInvoiceService(invoiceMapper, tradeOrderService);
        PaymentInvoiceResponse response = service.updateStatus("INV-31", new PaymentInvoiceStatusRequest(1, null, null));

        assertEquals(1, response.status());
    }

    /**
     * 校验已开票申请不允许逆向回退。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldRejectBackwardInvoiceStatus() {
        PaymentInvoice invoice = new PaymentInvoice();
        invoice.setInvoiceNo("INV-31");
        invoice.setStatus(2);
        when(invoiceMapper.selectOne(any())).thenReturn(invoice);

        PaymentInvoiceService service = new PaymentInvoiceService(invoiceMapper, tradeOrderService);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateStatus("INV-31", new PaymentInvoiceStatusRequest(1, null, null)));

        assertEquals("PAYMENT_INVOICE_STATUS_INVALID", exception.getCode());
    }

    /**
     * 校验已开票状态必须关联电子发票文件地址。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldRequireInvoiceUrlWhenIssued() {
        PaymentInvoice invoice = new PaymentInvoice();
        invoice.setInvoiceNo("INV-31");
        invoice.setStatus(1);
        when(invoiceMapper.selectOne(any())).thenReturn(invoice);

        PaymentInvoiceService service = new PaymentInvoiceService(invoiceMapper, tradeOrderService);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateStatus("INV-31", new PaymentInvoiceStatusRequest(2, "  ", null)));

        assertEquals("PAYMENT_INVOICE_URL_REQUIRED", exception.getCode());
    }

    /**
     * 校验开票失败状态必须填写失败原因。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldRequireFailureReasonWhenFailed() {
        PaymentInvoice invoice = new PaymentInvoice();
        invoice.setInvoiceNo("INV-31");
        invoice.setStatus(1);
        when(invoiceMapper.selectOne(any())).thenReturn(invoice);

        PaymentInvoiceService service = new PaymentInvoiceService(invoiceMapper, tradeOrderService);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateStatus("INV-31", new PaymentInvoiceStatusRequest(3, null, " ")));

        assertEquals("PAYMENT_INVOICE_FAILURE_REASON_REQUIRED", exception.getCode());
    }

    /**
     * 创建已支付订单测试夹具。
     *
     * @param orderId 订单ID
     * @param memberId 会员ID
     * @return 已支付订单
     * @author Henfon
     * @date 2026-08-31
     */
    private TradeOrder paidOrder(Long orderId, Long memberId) {
        TradeOrder order = new TradeOrder();
        order.setId(orderId);
        order.setMemberId(memberId);
        order.setOrderNo("ORD-" + orderId);
        order.setPaymentStatus(1);
        order.setPaidAmount(new BigDecimal("199.00"));
        return order;
    }
}
