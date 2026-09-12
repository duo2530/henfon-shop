package com.henfon.shop.marketing.service;

import com.henfon.shop.marketing.entity.MarketingFlashSale;
import com.henfon.shop.marketing.entity.MarketingFlashSaleItem;
import com.henfon.shop.catalog.entity.CatalogProduct;
import com.henfon.shop.catalog.mapper.CatalogProductMapper;
import com.henfon.shop.catalog.mapper.CatalogSkuMapper;
import com.henfon.shop.marketing.mapper.MarketingFlashSaleItemMapper;
import com.henfon.shop.marketing.mapper.MarketingFlashSaleMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
    private final MarketingFlashSalePortalService service = new MarketingFlashSalePortalService(activityMapper, itemMapper,
            productMapper, skuMapper);

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
}
