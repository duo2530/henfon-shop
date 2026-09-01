package com.henfon.shop.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.catalog.entity.CatalogProduct;
import com.henfon.shop.catalog.entity.CatalogProductFeature;
import com.henfon.shop.catalog.entity.CatalogProductMedia;
import com.henfon.shop.catalog.entity.CatalogProductSpec;
import com.henfon.shop.catalog.entity.CatalogSku;
import com.henfon.shop.catalog.mapper.CatalogProductFeatureMapper;
import com.henfon.shop.catalog.mapper.CatalogProductMapper;
import com.henfon.shop.catalog.mapper.CatalogProductMediaMapper;
import com.henfon.shop.catalog.mapper.CatalogProductSpecMapper;
import com.henfon.shop.catalog.mapper.CatalogSkuMapper;
import com.henfon.shop.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;

/**
 * 门户商品查询服务。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Service
public class CatalogPortalService {
    private final CatalogProductMapper productMapper;
    private final CatalogProductFeatureMapper featureMapper;
    private final CatalogProductSpecMapper specMapper;
    private final CatalogSkuMapper skuMapper;
    private final CatalogProductMediaMapper mediaMapper;

    /**
     * 创建门户商品查询服务。
     *
     * @param productMapper 商品数据访问对象
     * @param featureMapper 卖点数据访问对象
     * @param specMapper 参数数据访问对象
     * @param skuMapper SKU 数据访问对象
     * @param mediaMapper 媒体数据访问对象
     * @author Henfon
     * @date 2026-08-29
     */
    public CatalogPortalService(CatalogProductMapper productMapper,
                                CatalogProductFeatureMapper featureMapper,
                                CatalogProductSpecMapper specMapper,
                                CatalogSkuMapper skuMapper,
                                CatalogProductMediaMapper mediaMapper) {
        this.productMapper = productMapper;
        this.featureMapper = featureMapper;
        this.specMapper = specMapper;
        this.skuMapper = skuMapper;
        this.mediaMapper = mediaMapper;
    }

    /**
     * 分页查询门户上架商品。
     *
     * @param keyword 搜索关键字
     * @param categoryId 类目ID
     * @param minPrice 最低价格
     * @param maxPrice 最高价格
     * @param sortBy 排序方式
     * @param current 页码
     * @param size 页大小
     * @return 商品分页结果
     * @author Henfon
     * @date 2026-08-29
     */
    public IPage<CatalogProduct> page(String keyword, Long categoryId, BigDecimal minPrice,
                                      BigDecimal maxPrice, String sortBy, long current, long size) {
        return page(keyword, categoryId, minPrice, maxPrice, sortBy, null, current, size);
    }

    /**
     * 分页查询门户商品并支持 SKU 关键字筛选。
     *
     * @param keyword 商品名称、编码或品牌关键字
     * @param categoryId 类目ID
     * @param minPrice 最低价格
     * @param maxPrice 最高价格
     * @param sortBy 排序方式
     * @param skuKeyword SKU 编码、名称或属性关键字
     * @param current 页码
     * @param size 页大小
     * @return 商品分页结果
     * @author Henfon
     * @date 2026-09-01
     */
    public IPage<CatalogProduct> page(String keyword, Long categoryId, BigDecimal minPrice,
                                      BigDecimal maxPrice, String sortBy, String skuKeyword,
                                      long current, long size) {
        if ((minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0)) {
            throw new BusinessException("CATALOG_PRICE_RANGE_INVALID", "价格区间不能为负数");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BusinessException("CATALOG_PRICE_RANGE_INVALID", "最低价格不能高于最高价格");
        }
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<CatalogProduct> wrapper = new LambdaQueryWrapper<CatalogProduct>()
                .eq(CatalogProduct::getStatus, 1)
                .eq(categoryId != null, CatalogProduct::getCategoryId, categoryId)
                .ge(minPrice != null, CatalogProduct::getPrice, minPrice)
                .le(maxPrice != null, CatalogProduct::getPrice, maxPrice)
                .and(StringUtils.hasText(keyword), q -> q.like(CatalogProduct::getProductName, keyword)
                        .or().like(CatalogProduct::getProductCode, keyword)
                        .or().like(CatalogProduct::getBrandName, keyword));
        if (StringUtils.hasText(skuKeyword)) {
            Set<Long> skuProductIds = findSkuProductIds(skuKeyword.trim());
            // 无匹配 SKU 时直接返回空分页，避免构造无意义的 IN 条件。
            if (skuProductIds.isEmpty()) {
                return new Page<>(safeCurrent, safeSize, 0);
            }
            wrapper.in(CatalogProduct::getId, skuProductIds);
        }
        // 排序字段使用白名单映射，避免将前端参数直接拼接进SQL。
        switch (sortBy == null ? "featured" : sortBy) {
            case "price-asc" -> wrapper.orderByAsc(CatalogProduct::getPrice);
            case "price-desc" -> wrapper.orderByDesc(CatalogProduct::getPrice);
            case "sales" -> wrapper.orderByDesc(CatalogProduct::getSalesCount);
            case "newest" -> wrapper.orderByDesc(CatalogProduct::getCreatedAt);
            default -> wrapper.orderByDesc(CatalogProduct::getSalesCount)
                    .orderByDesc(CatalogProduct::getCreatedAt);
        }
        wrapper.orderByAsc(CatalogProduct::getId);
        return productMapper.selectPage(new Page<>(safeCurrent, safeSize), wrapper);
    }

    /**
     * 根据 SKU 编码、名称或属性 JSON 查找商品ID集合。
     *
     * @param skuKeyword SKU 查询关键字
     * @return 命中的商品ID集合
     * @author Henfon
     * @date 2026-09-01
     */
    private Set<Long> findSkuProductIds(String skuKeyword) {
        Set<Long> productIds = new HashSet<>();
        skuMapper.selectList(new LambdaQueryWrapper<CatalogSku>()
                        .eq(CatalogSku::getStatus, 1)
                        .and(query -> query.like(CatalogSku::getSkuCode, skuKeyword)
                                .or().like(CatalogSku::getSkuName, skuKeyword)
                                .or().like(CatalogSku::getAttributesJson, skuKeyword)))
                .forEach(sku -> {
                    if (sku.getProductId() != null) {
                        productIds.add(sku.getProductId());
                    }
                });
        return productIds;
    }

    /**
     * 查询商品详情及其 SKU、媒体、卖点和参数。
     *
     * @param productId 商品ID
     * @return 商品详情聚合对象，不存在时返回 null
     * @author Henfon
     * @date 2026-08-29
     */
    public Map<String, Object> detail(Long productId) {
        CatalogProduct product = productMapper.selectOne(new LambdaQueryWrapper<CatalogProduct>()
                .eq(CatalogProduct::getId, productId).eq(CatalogProduct::getStatus, 1));
        if (product == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("product", product);
        result.put("features", featureMapper.selectList(new LambdaQueryWrapper<CatalogProductFeature>()
                .eq(CatalogProductFeature::getProductId, productId).orderByAsc(CatalogProductFeature::getSortNo)));
        result.put("specs", specMapper.selectList(new LambdaQueryWrapper<CatalogProductSpec>()
                .eq(CatalogProductSpec::getProductId, productId).orderByAsc(CatalogProductSpec::getSortNo)));
        result.put("skus", skuMapper.selectList(new LambdaQueryWrapper<CatalogSku>()
                .eq(CatalogSku::getProductId, productId).eq(CatalogSku::getStatus, 1).orderByAsc(CatalogSku::getId)));
        result.put("media", mediaMapper.selectList(new LambdaQueryWrapper<CatalogProductMedia>()
                .eq(CatalogProductMedia::getProductId, productId).orderByAsc(CatalogProductMedia::getSortNo)));
        return result;
    }
}
