package com.henfon.shop.trade.service;

import com.henfon.shop.catalog.entity.CatalogProduct;
import com.henfon.shop.catalog.entity.CatalogSku;
import com.henfon.shop.catalog.mapper.CatalogProductMapper;
import com.henfon.shop.catalog.mapper.CatalogSkuMapper;
import com.henfon.shop.trade.dto.TradeFreightQuoteRequest;
import com.henfon.shop.trade.dto.TradeFreightQuoteResponse;
import com.henfon.shop.trade.entity.TradeFreightTemplate;
import com.henfon.shop.trade.mapper.TradeFreightTemplateMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 交易运费试算服务测试。
 *
 * @author Henfon
 * @date 2026-09-01
 */
class TradeFreightServiceTest {

    private final TradeFreightTemplateMapper templateMapper = mock(TradeFreightTemplateMapper.class);
    private final CatalogProductMapper productMapper = mock(CatalogProductMapper.class);
    private final CatalogSkuMapper skuMapper = mock(CatalogSkuMapper.class);
    private final TradeFreightService service = new TradeFreightService(templateMapper, productMapper, skuMapper);

    /**
     * 首重不足时按首重计费，超过首重后按续重向上取整。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldCalculateBaseAndAdditionalWeightFee() {
        stubTemplate("99.00", "0.00");
        CatalogProduct product = product(1L, 1000);
        CatalogSku sku = sku(11L, 1L, 1800);
        when(productMapper.selectById(1L)).thenReturn(product);
        when(skuMapper.selectById(11L)).thenReturn(sku);

        TradeFreightQuoteResponse quote = service.quote(new TradeFreightQuoteRequest(
                List.of(new TradeFreightQuoteRequest.Item(1L, 11L, 2)),
                "北京市", "北京市", "朝阳区", new BigDecimal("50.00"), BigDecimal.ZERO));

        // 2 * 1.8kg = 3.6kg，首重 1kg 加 3 个续重单位，共 30 元。
        assertEquals(new BigDecimal("30.00"), quote.freightAmount());
        assertEquals(3600, quote.totalWeightGram());
    }

    /**
     * 达到包邮门槛时免除重量运费。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldFreeShippingWhenSubtotalReachesThreshold() {
        stubTemplate("99.00", "0.00");
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1000));

        TradeFreightQuoteResponse quote = service.quote(new TradeFreightQuoteRequest(
                List.of(new TradeFreightQuoteRequest.Item(1L, null, 1)),
                "北京市", "北京市", "朝阳区", new BigDecimal("99.00"), BigDecimal.ZERO));

        assertEquals(new BigDecimal("0.00"), quote.freightAmount());
        assertEquals(true, quote.freeShipping());
    }

    /**
     * 命中偏远地区时叠加偏远附加费。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldAddRemoteSurcharge() {
        stubTemplate("0.00", "12.00");
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1000));

        TradeFreightQuoteResponse quote = service.quote(new TradeFreightQuoteRequest(
                List.of(new TradeFreightQuoteRequest.Item(1L, null, 1)),
                "新疆维吾尔自治区", "乌鲁木齐市", "天山区", new BigDecimal("20.00"), BigDecimal.ZERO));

        assertEquals(new BigDecimal("27.00"), quote.freightAmount());
        assertEquals(true, quote.remoteArea());
    }

    /**
     * 构造运费模板测试桩。
     *
     * @param threshold 包邮门槛
     * @param surcharge 偏远附加费
     * @author Henfon
     * @date 2026-09-01
     */
    private void stubTemplate(String threshold, String surcharge) {
        TradeFreightTemplate template = new TradeFreightTemplate();
        template.setId(1L);
        template.setTemplateName("测试模板");
        template.setCarrierName("顺丰速运");
        template.setBaseWeightGram(1000);
        template.setBaseFee(new BigDecimal("15.00"));
        template.setAdditionalWeightGram(1000);
        template.setAdditionalFee(new BigDecimal("5.00"));
        template.setFreeShippingThreshold(new BigDecimal(threshold));
        template.setRemoteSurcharge(new BigDecimal(surcharge));
        template.setRemoteRegionsCsv("西藏,新疆,港澳台");
        template.setStatus(1);
        template.setIsDefault(1);
        when(templateMapper.selectOne(any())).thenReturn(template);
    }

    /**
     * 构造商品测试数据。
     *
     * @param id 商品ID
     * @param weightGram 商品重量
     * @return 商品实体
     * @author Henfon
     * @date 2026-09-01
     */
    private CatalogProduct product(Long id, int weightGram) {
        CatalogProduct product = new CatalogProduct();
        product.setId(id);
        product.setStatus(1);
        product.setWeightGram(weightGram);
        return product;
    }

    /**
     * 构造 SKU 测试数据。
     *
     * @param id SKU ID
     * @param productId 商品ID
     * @param weightGram SKU重量
     * @return SKU实体
     * @author Henfon
     * @date 2026-09-01
     */
    private CatalogSku sku(Long id, Long productId, int weightGram) {
        CatalogSku sku = new CatalogSku();
        sku.setId(id);
        sku.setProductId(productId);
        sku.setStatus(1);
        sku.setWeightGram(weightGram);
        return sku;
    }
}
