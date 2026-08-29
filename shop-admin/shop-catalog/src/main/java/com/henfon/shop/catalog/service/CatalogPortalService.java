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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
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
     * @param current 页码
     * @param size 页大小
     * @return 商品分页结果
     * @author Henfon
     * @date 2026-08-29
     */
    public IPage<CatalogProduct> page(String keyword, Long categoryId, long current, long size) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<CatalogProduct> wrapper = new LambdaQueryWrapper<CatalogProduct>()
                .eq(CatalogProduct::getStatus, 1)
                .eq(categoryId != null, CatalogProduct::getCategoryId, categoryId)
                .and(StringUtils.hasText(keyword), q -> q.like(CatalogProduct::getProductName, keyword)
                        .or().like(CatalogProduct::getProductCode, keyword)
                        .or().like(CatalogProduct::getBrandName, keyword))
                .orderByDesc(CatalogProduct::getCreatedAt);
        return productMapper.selectPage(new Page<>(safeCurrent, safeSize), wrapper);
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
