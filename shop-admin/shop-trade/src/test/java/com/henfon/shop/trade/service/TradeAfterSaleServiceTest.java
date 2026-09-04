package com.henfon.shop.trade.service;

import com.henfon.shop.trade.entity.TradeAfterSale;
import com.henfon.shop.trade.mapper.TradeAfterSaleMapper;
import com.henfon.shop.trade.mapper.TradeOrderItemMapper;
import com.henfon.shop.trade.mapper.TradeOrderMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}
