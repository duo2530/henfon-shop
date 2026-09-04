package com.henfon.shop.trade.service;

import com.henfon.shop.trade.entity.TradeAfterSale;
import com.henfon.shop.trade.mapper.TradeAfterSaleMapper;
import com.henfon.shop.trade.mapper.TradeOrderItemMapper;
import com.henfon.shop.trade.mapper.TradeOrderMapper;
import com.henfon.shop.trade.entity.TradeOrder;
import com.henfon.shop.trade.entity.TradeOrderItem;
import com.henfon.shop.trade.dto.TradeOrderRefundRequest;
import com.henfon.shop.inventory.service.InventoryStockService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * 交易售后退款联动测试。
 *
 * @author Henfon
 * @date 2026-09-04
 */
class TradeAfterSaleServiceTest {

    private final TradeAfterSaleMapper afterSaleMapper = mock(TradeAfterSaleMapper.class);
    private final TradeOrderMapper orderMapper = mock(TradeOrderMapper.class);
    private final TradeOrderItemMapper orderItemMapper = mock(TradeOrderItemMapper.class);
    private final TradeOrderService tradeOrderService = mock(TradeOrderService.class);
    private final TradeAfterSaleService service = new TradeAfterSaleService(
            afterSaleMapper, orderMapper, orderItemMapper, tradeOrderService);

    /**
     * 退款成功后将退货退款售后单推进为已完成，避免售后长期停留处理中。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldCompleteReturnRefundAfterRefundSucceeded() {
        TradeAfterSale afterSale = new TradeAfterSale();
        afterSale.setId(10L);
        afterSale.setOrderId(20L);
        afterSale.setAfterSaleType(2);
        afterSale.setStatus(20);
        afterSale.setRefundAmount(new BigDecimal("30.00"));
        when(afterSaleMapper.selectOne(any())).thenReturn(afterSale);
        when(afterSaleMapper.updateById(any(TradeAfterSale.class))).thenReturn(1);

        boolean updated = service.markRefundSucceeded(20L, new BigDecimal("30.00"));

        assertTrue(updated);
        assertEquals(30, afterSale.getStatus());
        verify(afterSaleMapper).updateById(afterSale);
    }

    /**
     * 退货入库确认时回补对应 SKU 库存，避免售后完成后库存仍被占用。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldInboundStockWhenConfirmReturn() {
        InventoryStockService inventory = mock(InventoryStockService.class);
        TradeAfterSaleService inboundService = new TradeAfterSaleService(afterSaleMapper, orderMapper,
                orderItemMapper, tradeOrderService, null, inventory);
        TradeAfterSale afterSale = new TradeAfterSale();
        afterSale.setId(11L);
        afterSale.setOrderId(21L);
        afterSale.setOrderItemId(31L);
        afterSale.setAfterSaleType(2);
        afterSale.setStatus(20);
        afterSale.setRefundAmount(new BigDecimal("20.00"));
        afterSale.setAfterSaleNo("AS-11");
        TradeOrderItem item = new TradeOrderItem();
        item.setId(31L);
        item.setOrderId(21L);
        item.setSkuId(41L);
        item.setQuantity(2);
        TradeOrder order = new TradeOrder();
        order.setId(21L);
        order.setOrderStatus(20);
        when(afterSaleMapper.selectById(11L)).thenReturn(afterSale);
        when(orderItemMapper.selectOne(any())).thenReturn(item);
        when(tradeOrderService.findById(21L)).thenReturn(order);
        when(afterSaleMapper.updateById(any(TradeAfterSale.class))).thenReturn(1);

        inboundService.confirmReturn(11L, "仓库已收货");

        verify(inventory).inboundReturn(41L, 2, "AS-11");
        verify(tradeOrderService).refund(eq(21L), any(TradeOrderRefundRequest.class));
    }

    /**
     * 整单退货入库时逐个回补订单明细库存，避免漏记 SKU。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldInboundAllItemsWhenConfirmWholeOrderReturn() {
        InventoryStockService inventory = mock(InventoryStockService.class);
        TradeAfterSaleService inboundService = new TradeAfterSaleService(afterSaleMapper, orderMapper,
                orderItemMapper, tradeOrderService, null, inventory);
        TradeAfterSale afterSale = new TradeAfterSale();
        afterSale.setId(12L);
        afterSale.setOrderId(22L);
        afterSale.setAfterSaleType(2);
        afterSale.setStatus(20);
        afterSale.setRefundAmount(new BigDecimal("40.00"));
        afterSale.setAfterSaleNo("AS-12");
        TradeOrderItem first = new TradeOrderItem(); first.setSkuId(51L); first.setQuantity(1);
        TradeOrderItem second = new TradeOrderItem(); second.setSkuId(52L); second.setQuantity(3);
        TradeOrder order = new TradeOrder(); order.setId(22L); order.setOrderStatus(20);
        when(afterSaleMapper.selectById(12L)).thenReturn(afterSale);
        when(orderItemMapper.selectList(any())).thenReturn(List.of(first, second));
        when(tradeOrderService.findById(22L)).thenReturn(order);
        when(afterSaleMapper.updateById(any(TradeAfterSale.class))).thenReturn(1);

        inboundService.confirmReturn(12L, "整单退货入库");

        verify(inventory).inboundReturn(51L, 1, "AS-12");
        verify(inventory).inboundReturn(52L, 3, "AS-12");
    }
}
