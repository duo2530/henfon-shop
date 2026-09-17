package com.henfon.shop.marketing.service;

import com.henfon.shop.marketing.entity.MarketingFlashSaleReservation;
import com.henfon.shop.marketing.entity.MarketingFlashSale;
import com.henfon.shop.marketing.entity.MarketingFlashSaleItem;
import com.henfon.shop.marketing.mapper.MarketingFlashSaleItemMapper;
import com.henfon.shop.marketing.mapper.MarketingFlashSaleMapper;
import com.henfon.shop.marketing.mapper.MarketingFlashSaleReservationMapper;
import com.henfon.shop.catalog.entity.CatalogProduct;
import com.henfon.shop.catalog.entity.CatalogSku;
import com.henfon.shop.catalog.mapper.CatalogProductMapper;
import com.henfon.shop.catalog.mapper.CatalogSkuMapper;
import com.henfon.shop.identity.mapper.MemberUserMapper;
import com.henfon.shop.inventory.service.InventoryStockService;
import com.henfon.shop.marketing.dto.MarketingFlashSaleSaveRequest;
import com.henfon.shop.trade.mapper.TradeOrderMapper;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.common.marketing.FlashSaleReservationItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private final MemberUserMapper memberUserMapper = mock(MemberUserMapper.class);
    private final TradeOrderMapper tradeOrderMapper = mock(TradeOrderMapper.class);
    private final InventoryStockService inventoryStockService = mock(InventoryStockService.class);
    private final FlashSalePortalCache portalCache = mock(FlashSalePortalCache.class);
    private final MarketingFlashSaleService service = new MarketingFlashSaleService(activityMapper, itemMapper,
            reservationMapper, productMapper, skuMapper, memberUserMapper, tradeOrderMapper, inventoryStockService,
            portalCache);

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
     * 验证活动按商品维度配置（sku_id 为空）时，携带具体 SKU 的下单请求仍能命中明细。
     *
     * @author Henfon
     * @date 2026-09-16
     */
    @Test
    void shouldFallbackToProductWideItemWhenExactSkuMisses() {
        MarketingFlashSale activity = new MarketingFlashSale();
        activity.setId(10L);
        activity.setStatus(1);
        activity.setStartAt(LocalDateTime.now().minusMinutes(1));
        activity.setEndAt(LocalDateTime.now().plusHours(1));
        activity.setLimitPerMember(2);
        MarketingFlashSaleItem item = new MarketingFlashSaleItem();
        item.setId(11L);
        item.setActivityId(10L);
        item.setProductId(100L);
        item.setActivityPrice(BigDecimal.valueOf(0.01).setScale(2));
        item.setTotalStock(10);
        item.setSoldStock(0);
        item.setLimitPerMember(2);
        item.setStatus(1);
        when(activityMapper.selectById(10L)).thenReturn(activity);
        when(reservationMapper.selectList(any())).thenReturn(List.of(), List.of());
        // 精确匹配未命中（活动未按 SKU 配置），通配查询返回按商品配置的明细。
        when(itemMapper.selectOne(any())).thenReturn(null, item);
        when(itemMapper.update(any(), any())).thenReturn(1);

        service.reserve(10L, 20L, 30L, List.of(new FlashSaleReservationItem(100L, 391L, 1,
                BigDecimal.valueOf(0.01).setScale(2))));

        // 两次查询：先精确匹配，未命中后回退通配，最终落库一条预占。
        verify(itemMapper, times(2)).selectOne(any());
        verify(reservationMapper).insert(org.mockito.ArgumentMatchers.<MarketingFlashSaleReservation>any());
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

    /**
     * 验证保存活动时以 inventory 台账可用库存为上限，而不是 catalog 的总库存字段。
     *
     * @author Henfon
     * @date 2026-09-17
     */
    @Test
    void shouldValidateAvailableStockFromInventory() {
        CatalogProduct product = new CatalogProduct();
        product.setId(100L);
        product.setStatus(1);
        product.setPrice(BigDecimal.valueOf(100));
        CatalogSku sku = new CatalogSku();
        sku.setId(200L);
        sku.setProductId(100L);
        sku.setStatus(1);
        sku.setPrice(BigDecimal.valueOf(100));
        when(productMapper.selectById(100L)).thenReturn(product);
        when(skuMapper.selectById(200L)).thenReturn(sku);
        // 台账可用量只有 5，活动配额配 10 必须被拒绝，错误文案要带出可用量。
        when(inventoryStockService.availableStock(200L)).thenReturn(5);
        MarketingFlashSaleSaveRequest request = new MarketingFlashSaleSaveRequest(null, "FS001", "库存校验活动",
                LocalDateTime.now(), LocalDateTime.now().plusHours(1), 1, 1,
                List.of(new MarketingFlashSaleSaveRequest.Item(null, 100L, 200L, BigDecimal.valueOf(50), 10, 1, 1,
                        null)), null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.save(request));

        assertEquals("MARKETING_FLASH_SALE_STOCK_INVALID", exception.getCode());
        assertTrue(exception.getMessage().contains("5"));
    }

    /**
     * 验证启用活动时按最新可用库存复校，库存已被普通订单占走的活动不能上线。
     *
     * @author Henfon
     * @date 2026-09-17
     */
    @Test
    void shouldRejectEnableWhenAvailableStockNotEnough() {
        MarketingFlashSale activity = new MarketingFlashSale();
        activity.setId(10L);
        activity.setStatus(0);
        MarketingFlashSaleItem item = new MarketingFlashSaleItem();
        item.setId(11L);
        item.setActivityId(10L);
        item.setProductId(100L);
        item.setSkuId(200L);
        item.setTotalStock(50);
        item.setSoldStock(0);
        when(activityMapper.selectById(10L)).thenReturn(activity);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(inventoryStockService.availableStock(200L)).thenReturn(10);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.updateStatus(10L, 1));

        assertEquals("MARKETING_FLASH_SALE_STOCK_INVALID", exception.getCode());
        verify(activityMapper, never()).updateById(any(MarketingFlashSale.class));
    }
}
