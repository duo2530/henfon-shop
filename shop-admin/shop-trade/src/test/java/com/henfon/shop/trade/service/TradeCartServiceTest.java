package com.henfon.shop.trade.service;

import com.henfon.shop.catalog.entity.CatalogProduct;
import com.henfon.shop.catalog.mapper.CatalogProductMapper;
import com.henfon.shop.catalog.mapper.CatalogSkuMapper;
import com.henfon.shop.trade.entity.TradeCartItem;
import com.henfon.shop.trade.dto.TradeCartMergeItemRequest;
import com.henfon.shop.trade.mapper.TradeCartItemMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 购物车库存收敛测试。
 *
 * @author Henfon
 * @date 2026-09-04
 */
class TradeCartServiceTest {

    private final TradeCartItemMapper cartMapper = mock(TradeCartItemMapper.class);
    private final CatalogProductMapper productMapper = mock(CatalogProductMapper.class);
    private final CatalogSkuMapper skuMapper = mock(CatalogSkuMapper.class);
    private final TradeCartService service = new TradeCartService(cartMapper, productMapper, skuMapper);

    /**
     * 查询购物车时将超过当前库存的数量收敛为可售库存。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldNormalizeQuantityWhenStockDrops() {
        TradeCartItem item = new TradeCartItem();
        item.setId(10L);
        item.setProductId(20L);
        item.setQuantity(5);
        CatalogProduct product = new CatalogProduct();
        product.setId(20L);
        product.setStatus(1);
        product.setCurrentStock(3);
        when(cartMapper.selectList(any())).thenReturn(List.of(item));
        when(productMapper.selectById(20L)).thenReturn(product);
        when(cartMapper.updateById(org.mockito.ArgumentMatchers.any(TradeCartItem.class))).thenReturn(1);

        List<TradeCartItem> result = service.list(99L);

        assertEquals(3, result.get(0).getQuantity());
        verify(cartMapper).updateById(org.mockito.ArgumentMatchers.any(TradeCartItem.class));
    }

    /**
     * 合并本地购物车时合并同规格数量并限制在当前库存内。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Test
    void shouldMergeLocalItemsAndCapQuantityByStock() {
        CatalogProduct product = new CatalogProduct();
        product.setId(20L);
        product.setStatus(1);
        product.setCurrentStock(5);
        TradeCartItem existing = new TradeCartItem();
        existing.setId(11L);
        existing.setMemberId(99L);
        existing.setProductId(20L);
        existing.setQuantity(3);
        when(productMapper.selectById(20L)).thenReturn(product);
        when(cartMapper.selectOne(any())).thenReturn(existing);
        when(cartMapper.updateById(any(TradeCartItem.class))).thenReturn(1);

        int merged = service.merge(99L, List.of(new TradeCartMergeItemRequest(20L, null, 4, 1)));

        assertEquals(1, merged);
        assertEquals(5, existing.getQuantity());
        verify(cartMapper).updateById(existing);
    }
}
