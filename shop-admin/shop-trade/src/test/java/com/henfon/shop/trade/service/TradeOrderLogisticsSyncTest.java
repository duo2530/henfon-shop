package com.henfon.shop.trade.service;

import com.henfon.shop.catalog.mapper.CatalogProductMapper;
import com.henfon.shop.catalog.mapper.CatalogSkuMapper;
import com.henfon.shop.common.marketing.FlashSaleReservationService;
import com.henfon.shop.identity.service.MemberAdminService;
import com.henfon.shop.integration.logistics.LogisticsProvider;
import com.henfon.shop.integration.logistics.LogisticsTrackNode;
import com.henfon.shop.integration.logistics.LogisticsTrackResult;
import com.henfon.shop.integration.storage.MinioStorageService;
import com.henfon.shop.inventory.service.InventoryStockService;
import com.henfon.shop.trade.entity.TradeOrder;
import com.henfon.shop.trade.entity.TradeOrderLogistics;
import com.henfon.shop.trade.mapper.TradeOrderItemMapper;
import com.henfon.shop.trade.mapper.TradeOrderLogisticsMapper;
import com.henfon.shop.trade.mapper.TradeOrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单物流同步闭环测试。
 *
 * @author Henfon
 * @date 2026-09-04
 */
class TradeOrderLogisticsSyncTest {

    /**
     * 第三方返回签收状态时自动完成订单并记录完成事件。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldCompleteOrderWhenLogisticsSigned() {
        TradeOrderMapper orderMapper = mock(TradeOrderMapper.class);
        TradeOrderLogisticsMapper logisticsMapper = mock(TradeOrderLogisticsMapper.class);
        TradeOrder order = new TradeOrder();
        order.setId(1L);
        order.setOrderNo("AO-1");
        order.setOrderStatus(TradeOrderStateMachine.STATUS_SHIPPED);
        order.setLogisticsCompany("顺丰速运");
        order.setTrackingNo("SF123456");
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(logisticsMapper.selectOne(any())).thenReturn(null);
        when(logisticsMapper.insert(any(TradeOrderLogistics.class))).thenReturn(1);
        when(orderMapper.updateById(any(TradeOrder.class))).thenReturn(1);

        LogisticsProvider provider = mock(LogisticsProvider.class);
        when(provider.query("顺丰速运", "SF123456")).thenReturn(new LogisticsTrackResult(
                true, "mock", "sf", "顺丰速运", "SF123456", "SIGNED", "已签收",
                List.of(new LogisticsTrackNode(LocalDateTime.now(), "SIGNED", "已签收", "上海"))));
        TradeEventOutboxService outbox = mock(TradeEventOutboxService.class);
        TradeOrderService service = new TradeOrderService(orderMapper, mock(TradeOrderItemMapper.class),
                logisticsMapper, mock(InventoryStockService.class), outbox, mock(CatalogProductMapper.class),
                mock(CatalogSkuMapper.class), mock(MemberAdminService.class), provider,
                mock(TradeFreightService.class), mock(MinioStorageService.class),
                mock(ObjectProvider.class));

        service.syncLogistics(1L);

        assertEquals(TradeOrderStateMachine.STATUS_COMPLETED, order.getOrderStatus());
        verify(orderMapper).updateById(order);
        verify(outbox).recordOrderCompleted(order);
    }
}
