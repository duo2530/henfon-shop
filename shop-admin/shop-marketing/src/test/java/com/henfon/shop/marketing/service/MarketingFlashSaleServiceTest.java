package com.henfon.shop.marketing.service;

import com.henfon.shop.marketing.entity.MarketingFlashSaleReservation;
import com.henfon.shop.marketing.entity.MarketingFlashSale;
import com.henfon.shop.marketing.entity.MarketingFlashSaleItem;
import com.henfon.shop.marketing.mapper.MarketingFlashSaleItemMapper;
import com.henfon.shop.marketing.mapper.MarketingFlashSaleMapper;
import com.henfon.shop.marketing.mapper.MarketingFlashSaleReservationMapper;
import com.henfon.shop.catalog.mapper.CatalogProductMapper;
import com.henfon.shop.catalog.mapper.CatalogSkuMapper;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.common.marketing.FlashSaleReservationItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 秒杀库存预占服务测试。
 *
 * @author Henfon
 * @date 2026-09-04
 */
class MarketingFlashSaleServiceTest {

    private final MarketingFlashSaleMapper activityMapper = mock(MarketingFlashSaleMapper.class);
    private final MarketingFlashSaleItemMapper itemMapper = mock(MarketingFlashSaleItemMapper.class);
    private final MarketingFlashSaleReservationMapper reservationMapper = mock(MarketingFlashSaleReservationMapper.class);
    private final CatalogProductMapper productMapper = mock(CatalogProductMapper.class);
    private final CatalogSkuMapper skuMapper = mock(CatalogSkuMapper.class);
    private final MarketingFlashSaleService service = new MarketingFlashSaleService(activityMapper, itemMapper,
            reservationMapper, productMapper, skuMapper);

    /**
     * 验证同一订单重复预占时直接幂等返回，不重复扣减库存。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldReturnWhenOrderAlreadyReserved() {
        MarketingFlashSaleReservation reservation = new MarketingFlashSaleReservation();
        reservation.setActivityId(10L);
        reservation.setMemberId(20L);
        reservation.setOrderId(30L);
        reservation.setStatus(0);
        when(reservationMapper.selectList(any())).thenReturn(List.of(reservation));

        // 重试请求命中已有预占后直接结束，活动和商品表都不应再访问。
        service.reserve(10L, 20L, 30L, List.of(new FlashSaleReservationItem(100L, null, 1,
                java.math.BigDecimal.TEN)));

        verify(activityMapper, never()).selectById(any());
        verify(itemMapper, never()).update(any(), any());
        verify(reservationMapper, never()).insert(org.mockito.ArgumentMatchers.<MarketingFlashSaleReservation>any());
    }

    /**
     * 验证订单已绑定其他会员或活动时拒绝复用预占记录。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldRejectOrderReservationConflict() {
        MarketingFlashSaleReservation reservation = new MarketingFlashSaleReservation();
        reservation.setActivityId(99L);
        reservation.setMemberId(88L);
        reservation.setOrderId(30L);
        reservation.setStatus(0);
        when(reservationMapper.selectList(any())).thenReturn(List.of(reservation));

        // 订单与当前请求上下文不一致时必须报错，防止跨会员篡改库存预占。
        BusinessException exception = assertThrows(BusinessException.class, () -> service.reserve(10L, 20L, 30L,
                List.of(new FlashSaleReservationItem(100L, null, 1, java.math.BigDecimal.TEN))));
        assertEquals("MARKETING_FLASH_SALE_ORDER_CONFLICT", exception.getCode());
        verify(activityMapper, never()).selectById(any());
    }

    /**
     * 验证释放不存在的秒杀预占时安全返回。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldIgnoreReleaseWhenNoReservationExists() {
        when(reservationMapper.selectList(any())).thenReturn(List.of());

        // 普通订单或重复取消场景没有秒杀记录时不应更新库存。
        service.release(30L);

        verify(itemMapper, never()).update(any(), any());
        verify(reservationMapper, never()).updateById(org.mockito.ArgumentMatchers.<MarketingFlashSaleReservation>any());
    }

    /**
     * 验证预热会补齐缺失已售库存并处理即将开始的活动。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldWarmupUpcomingActivityAndInitializeSoldStock() {
        MarketingFlashSale activity = new MarketingFlashSale();
        activity.setId(10L);
        activity.setStatus(1);
        activity.setStartAt(LocalDateTime.now().plusMinutes(5));
        activity.setEndAt(LocalDateTime.now().plusHours(1));
        MarketingFlashSaleItem item = new MarketingFlashSaleItem();
        item.setId(11L);
        item.setActivityId(10L);
        item.setTotalStock(20);
        item.setSoldStock(null);
        item.setStatus(1);
        when(activityMapper.selectList(any())).thenReturn(List.of(), List.of(activity));
        when(itemMapper.selectList(any())).thenReturn(List.of(item));

        // 预热阶段将历史空值库存快照初始化为 0，保证后续原子扣减稳定。
        assertEquals(1, service.warmupUpcomingActivities(30));
        assertEquals(0, item.getSoldStock());
        verify(itemMapper).updateById(item);
    }

    /**
     * 验证预热会将已结束的启用活动自动收口为结束状态。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldCloseExpiredActivityDuringWarmup() {
        MarketingFlashSale expired = new MarketingFlashSale();
        expired.setId(20L);
        expired.setStatus(1);
        expired.setStartAt(LocalDateTime.now().minusHours(2));
        expired.setEndAt(LocalDateTime.now().minusMinutes(1));
        when(activityMapper.selectList(any())).thenReturn(List.of(expired), List.of());

        // 已结束活动不再参与门户售卖，并在调度轮次内统一切换到结束状态。
        assertEquals(0, service.warmupUpcomingActivities(30));
        assertEquals(2, expired.getStatus());
        verify(activityMapper).updateById(expired);
    }
}
