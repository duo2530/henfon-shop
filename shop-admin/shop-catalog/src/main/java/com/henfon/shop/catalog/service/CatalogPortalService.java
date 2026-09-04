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
import com.henfon.shop.integration.storage.MinioStorageService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

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
    private final MinioStorageService minioStorageService;

    /**
     * 创建门户商品查询服务。
     *
     * @param productMapper 商品数据访问对象
     * @param featureMapper 卖点数据访问对象
     * @param specMapper 参数数据访问对象
     * @param skuMapper SKU 数据访问对象
     * @param mediaMapper 媒体数据访问对象
     * @param minioStorageService MinIO 文件服务
     * @author Henfon
     * @date 2026-08-29
     */
    public CatalogPortalService(CatalogProductMapper productMapper,
                                CatalogProductFeatureMapper featureMapper,
                                CatalogProductSpecMapper specMapper,
                                CatalogSkuMapper skuMapper,
                                CatalogProductMediaMapper mediaMapper,
                                MinioStorageService minioStorageService) {
        this.productMapper = productMapper;
        this.featureMapper = featureMapper;
        this.specMapper = specMapper;
        this.skuMapper = skuMapper;
        this.mediaMapper = mediaMapper;
        this.minioStorageService = minioStorageService;
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
        IPage<CatalogProduct> result = productMapper.selectPage(new Page<>(safeCurrent, safeSize), wrapper);
        // 列表接口也动态刷新封面地址，避免数据库中持久化的预签名 URL 过期。
        refreshMainImageUrls(result.getRecords());
        return result;
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
                                .or().like(CatalogSku::getBarcode, skuKeyword)
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
        List<CatalogProductMedia> media = mediaMapper.selectList(new LambdaQueryWrapper<CatalogProductMedia>()
                .eq(CatalogProductMedia::getProductId, productId).orderByAsc(CatalogProductMedia::getSortNo));
        // 详情接口返回前动态生成预签名地址，客户端无需感知 URL 的有效期。
        refreshMediaUrls(media);
        refreshMainImageUrl(product, media);
        result.put("media", media);
        return result;
    }

    /**
     * 批量刷新商品列表中的封面访问地址。
     *
     * @param products 门户商品列表
     * @author Henfon
     * @date 2026-09-01
     */
    private void refreshMainImageUrls(List<CatalogProduct> products) {
        if (products == null || products.isEmpty()) {
            return;
        }
        List<Long> productIds = products.stream()
                .map(CatalogProduct::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (productIds.isEmpty()) {
            return;
        }
        List<CatalogProductMedia> mediaList = mediaMapper.selectList(new LambdaQueryWrapper<CatalogProductMedia>()
                .in(CatalogProductMedia::getProductId, productIds)
                .orderByAsc(CatalogProductMedia::getSortNo)
                .orderByAsc(CatalogProductMedia::getId));
        Map<Long, CatalogProductMedia> coverMedia = new HashMap<>();
        for (CatalogProductMedia media : mediaList) {
            if (media.getProductId() != null && isImageMedia(media)) {
                // 优先使用明确标记的封面，未标记时保留排序最靠前的一张。
                CatalogProductMedia existing = coverMedia.get(media.getProductId());
                if (existing == null || (existing.getIsCover() == null || existing.getIsCover() != 1)
                        && media.getIsCover() != null && media.getIsCover() == 1) {
                    coverMedia.put(media.getProductId(), media);
                }
            }
        }
        for (CatalogProduct product : products) {
            CatalogProductMedia cover = coverMedia.get(product.getId());
            if (cover != null) {
                product.setMainImageUrl(resolveMediaUrl(cover));
            }
        }
    }

    /**
     * 刷新商品详情中的所有媒体访问地址。
     *
     * @param media 商品媒体列表
     * @author Henfon
     * @date 2026-09-01
     */
    private void refreshMediaUrls(List<CatalogProductMedia> media) {
        if (media == null || media.isEmpty()) {
            return;
        }
        for (CatalogProductMedia item : media) {
            item.setMediaUrl(resolveMediaUrl(item));
        }
    }

    /**
     * 使用封面媒体覆盖商品主图地址。
     *
     * @param product 商品实体
     * @param media 媒体列表
     * @author Henfon
     * @date 2026-09-01
     */
    private void refreshMainImageUrl(CatalogProduct product, List<CatalogProductMedia> media) {
        if (product == null || media == null || media.isEmpty()) {
            return;
        }
        CatalogProductMedia cover = media.stream()
                .filter(this::isImageMedia)
                .filter(item -> item.getIsCover() != null && item.getIsCover() == 1)
                .findFirst()
                .orElseGet(() -> media.stream().filter(this::isImageMedia).findFirst().orElse(null));
        if (cover != null) {
            product.setMainImageUrl(cover.getMediaUrl());
        }
    }

    /**
     * 根据对象键动态生成 MinIO 预签名地址，并兼容历史外部地址。
     *
     * @param media 商品媒体
     * @return 当前有效的访问地址
     * @author Henfon
     * @date 2026-09-01
     */
    private String resolveMediaUrl(CatalogProductMedia media) {
        if (media == null) {
            return null;
        }
        String objectKey = media.getObjectKey();
        if (!StringUtils.hasText(objectKey)) {
            return media.getMediaUrl();
        }
        if (isExternalAddress(objectKey)) {
            // 兼容历史数据将外部 URL 误存到对象键字段的情况，不向 MinIO 发起无效签名请求。
            return media.getMediaUrl() != null ? media.getMediaUrl() : objectKey;
        }
        try {
            return minioStorageService.presign(objectKey.trim());
        } catch (BusinessException exception) {
            // MinIO 暂时不可用时返回数据库地址，避免门户商品整体查询失败。
            return media.getMediaUrl();
        }
    }

    /**
     * 判断字符串是否已经是外部访问地址。
     *
     * @param value 待判断字符串
     * @return 是否为 HTTP(S) 地址
     * @author Henfon
     * @date 2026-09-01
     */
    private boolean isExternalAddress(String value) {
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    /**
     * 判断媒体是否为商品图片。
     *
     * @param media 商品媒体
     * @return 是否为图片媒体
     * @author Henfon
     * @date 2026-09-01
     */
    private boolean isImageMedia(CatalogProductMedia media) {
        return media != null && "IMAGE".equalsIgnoreCase(media.getMediaType());
    }
}
