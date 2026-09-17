package com.henfon.shop.marketing.service;

import com.henfon.shop.marketing.entity.MarketingFlashSale;
import com.henfon.shop.marketing.entity.MarketingFlashSaleItem;
import com.henfon.shop.catalog.entity.CatalogProduct;
import com.henfon.shop.catalog.mapper.CatalogProductMapper;
import com.henfon.shop.catalog.mapper.CatalogSkuMapper;
import com.henfon.shop.integration.storage.MinioStorageService;
import com.henfon.shop.marketing.dto.MarketingFlashSalePortalResponse;
import com.henfon.shop.marketing.mapper.MarketingFlashSaleItemMapper;
import com.henfon.shop.marketing.mapper.MarketingFlashSaleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 门户秒杀活动查询服务测试。
 *
 * @author Henfon
 * @date 2026-09-01
 */
class MarketingFlashSalePortalServiceTest {

    private final MarketingFlashSaleMapper activityMapper = mock(MarketingFlashSaleMapper.class);
    private final MarketingFlashSaleItemMapper itemMapper = mock(MarketingFlashSaleItemMapper.class);
    private final CatalogProductMapper productMapper = mock(CatalogProductMapper.class);
    private final CatalogSkuMapper skuMapper = mock(CatalogSkuMapper.class);
    private final MinioStorageService minioStorageService = mock(MinioStorageService.class);
    private final FlashSalePortalCache portalCache = mock(FlashSalePortalCache.class);
    private final MarketingFlashSalePortalService service = new MarketingFlashSalePortalService(activityMapper, itemMapper,
            productMapper, skuMapper, minioStorageService, portalCache);

    /**
     * 隔离对象存储，图片地址续签在测试中原样返回。
     *
     * @author Henfon
     * @date 2026-09-16
     */
    @BeforeEach
    void stubStorage() {
        when(minioStorageService.resolveAccessUrl(any())).thenAnswer(invocation -> invocation.getArgument(0));
        // Mockito 对 List 返回值默认返回空集合而不是 null，不显式声明会把缓存误判成命中。
        when(portalCache.get()).thenReturn(null);
    }

    /**
     * 验证活动商品返回剩余库存，并过滤停用和售罄商品。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldReturnAvailableItemsWithRemainingStock() {
        MarketingFlashSale activity = new MarketingFlashSale();
        activity.setId(10L);
        activity.setActivityCode("FLASH-001");
        activity.setActivityName("午间秒杀");
        activity.setStartAt(LocalDateTime.now().minusMinutes(5));
        activity.setEndAt(LocalDateTime.now().plusMinutes(55));
        activity.setStatus(1);
        activity.setLimitPerMember(2);
        MarketingFlashSaleItem item = new MarketingFlashSaleItem();
        item.setId(20L);
        item.setActivityId(10L);
        item.setProductId(100L);
        item.setActivityPrice(new BigDecimal("9.90"));
        item.setTotalStock(10);
        item.setSoldStock(3);
        item.setLimitPerMember(1);
        item.setStatus(1);
        CatalogProduct product = new CatalogProduct();
        product.setId(100L);
        product.setProductName("测试商品");
        product.setPrice(new BigDecimal("19.90"));
        product.setStatus(1);
        when(activityMapper.selectList(any())).thenReturn(List.of(activity));
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(productMapper.selectList(any())).thenReturn(List.of(product));
        when(skuMapper.selectList(any())).thenReturn(List.of());

        var result = service.activeFlashSales();

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).items().size());
        assertEquals(7, result.get(0).items().get(0).remainingStock());
    }

    /**
     * 验证没有可售商品时门户不展示活动。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldHideActivityWhenNoItemsAvailable() {
        MarketingFlashSale activity = new MarketingFlashSale();
        activity.setId(11L);
        activity.setStartAt(LocalDateTime.now().minusMinutes(5));
        activity.setEndAt(LocalDateTime.now().plusMinutes(5));
        activity.setStatus(1);
        when(activityMapper.selectList(any())).thenReturn(List.of(activity));
        when(itemMapper.selectList(any())).thenReturn(List.of());

        assertTrue(service.activeFlashSales().isEmpty());
    }

    /**
     * 验证缓存命中时直接返回缓存内容，不再访问数据库，也不重复写缓存。
     *
     * @author Henfon
     * @date 2026-09-17
     */
    @Test
    void shouldReturnCachedActivitiesWithoutDatabaseQuery() {
        MarketingFlashSalePortalResponse cached = new MarketingFlashSalePortalResponse(10L, "FLASH-001", "午间秒杀",
                LocalDateTime.now(), LocalDateTime.now().plusMinutes(30), 2, List.of());
        when(portalCache.get()).thenReturn(List.of(cached));

        var result = service.activeFlashSales();

        assertEquals(1, result.size());
        assertEquals("FLASH-001", result.get(0).activityCode());
        verify(activityMapper, never()).selectList(any());
        verify(portalCache, never()).put(any());
    }
}
