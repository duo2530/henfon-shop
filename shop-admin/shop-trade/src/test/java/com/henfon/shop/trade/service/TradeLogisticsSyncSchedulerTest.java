package com.henfon.shop.trade.service;

import com.henfon.shop.integration.logistics.LogisticsProvider;
import com.henfon.shop.trade.dto.TradeOrderLogisticsSyncResult;
import com.henfon.shop.trade.entity.TradeOrder;
import com.henfon.shop.trade.mapper.TradeOrderMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 物流同步重试与告警测试。
 *
 * @author Henfon
 * @date 2026-09-04
 */
class TradeLogisticsSyncSchedulerTest {

    /**
     * 服务商返回暂无轨迹时应执行有限重试并停止。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldRetryWhenProviderReturnsNoTracks() {
        TradeOrderMapper mapper = mock(TradeOrderMapper.class);
        TradeOrderService service = mock(TradeOrderService.class);
        LogisticsProvider provider = mock(LogisticsProvider.class);
        when(provider.enabled()).thenReturn(true);
        TradeOrder order = new TradeOrder();
        order.setId(9L);
        order.setLogisticsCompany("顺丰速运");
        order.setTrackingNo("SF123456789");
        when(mapper.selectList(any())).thenReturn(List.of(order));
        when(service.syncLogistics(9L)).thenReturn(
                new TradeOrderLogisticsSyncResult(true, "mock", "IN_TRANSIT", 0, "暂无物流轨迹"));

        TradeLogisticsSyncScheduler scheduler = new TradeLogisticsSyncScheduler(mapper, service, provider);
        scheduler.syncActiveOrders();

        verify(service, times(3)).syncLogistics(9L);
    }

    /**
     * 服务商连续异常时应执行重试，不能阻塞批量任务。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldRetryWhenProviderThrows() {
        TradeOrderMapper mapper = mock(TradeOrderMapper.class);
        TradeOrderService service = mock(TradeOrderService.class);
        LogisticsProvider provider = mock(LogisticsProvider.class);
        when(provider.enabled()).thenReturn(true);
        TradeOrder order = new TradeOrder();
        order.setId(10L);
        order.setLogisticsCompany("中通");
        order.setTrackingNo("ZT123456789");
        when(mapper.selectList(any())).thenReturn(List.of(order));
        when(service.syncLogistics(10L)).thenThrow(new RuntimeException("timeout"));

        TradeLogisticsSyncScheduler scheduler = new TradeLogisticsSyncScheduler(mapper, service, provider);
        scheduler.syncActiveOrders();

        verify(service, times(3)).syncLogistics(10L);
    }
}
