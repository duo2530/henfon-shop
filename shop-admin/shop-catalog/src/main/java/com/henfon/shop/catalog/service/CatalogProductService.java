package com.henfon.shop.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.catalog.dto.CatalogProductSaveRequest;
import com.henfon.shop.catalog.entity.CatalogProduct;
import com.henfon.shop.catalog.mapper.CatalogProductMapper;
import com.henfon.shop.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 商品目录应用服务。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Service
public class CatalogProductService {

    private final CatalogProductMapper catalogProductMapper;

    /**
     * 创建商品目录服务。
     *
     * @param catalogProductMapper 商品数据访问对象
     * @author Henfon
     * @date 2026-08-29
     */
    public CatalogProductService(CatalogProductMapper catalogProductMapper) {
        this.catalogProductMapper = catalogProductMapper;
    }

    /**
     * 分页查询商品。
     *
     * @param keyword 商品名称、编码或SKU关键字
     * @param categoryId 类目ID
     * @param status 商品状态
     * @param current 当前页
     * @param size 页大小
     * @return 商品分页结果
     * @author Henfon
     * @date 2026-08-29
     */
    public IPage<CatalogProduct> page(String keyword, Long categoryId, Integer status, long current, long size) {
        // 统一限制分页参数，避免异常参数导致数据库扫描过大。
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        LambdaQueryWrapper<CatalogProduct> wrapper = new LambdaQueryWrapper<CatalogProduct>()
                .eq(categoryId != null, CatalogProduct::getCategoryId, categoryId)
                .eq(status != null, CatalogProduct::getStatus, status)
                .and(StringUtils.hasText(keyword), query -> query
                        .like(CatalogProduct::getProductName, keyword)
                        .or().like(CatalogProduct::getProductCode, keyword)
                        .or().like(CatalogProduct::getDefaultSkuCode, keyword))
                .orderByDesc(CatalogProduct::getCreatedAt);
        return catalogProductMapper.selectPage(new Page<>(safeCurrent, safeSize), wrapper);
    }

    /**
     * 保存商品。
     *
     * @param request 商品保存请求
     * @return 商品ID
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public Long save(CatalogProductSaveRequest request) {
        CatalogProduct product = new CatalogProduct();
        product.setId(request.id());
        product.setCategoryId(request.categoryId());
        product.setCategoryName(request.categoryName());
        product.setProductName(request.productName());
        product.setProductCode(request.productCode());
        product.setDefaultSkuCode(request.defaultSkuCode());
        product.setBrandName(request.brandName());
        product.setShortDescription(request.shortDescription());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setMarketPrice(request.marketPrice() == null ? request.price() : request.marketPrice());
        product.setCostPrice(request.costPrice() == null ? BigDecimal.ZERO : request.costPrice());
        product.setCurrentStock(request.currentStock() == null ? 0 : request.currentStock());
        product.setSafetyStock(request.safetyStock() == null ? 0 : request.safetyStock());
        product.setMainImageUrl(request.mainImageUrl());
        product.setTagsCsv(request.tagsCsv());
        product.setStatus(request.status() == null ? 1 : request.status());
        product.setRemark(request.remark());
        if (product.getId() == null) {
            // 新商品默认作为上架商品写入，后续可由审核流程改为草稿状态。
            product.setSalesCount(0L);
            catalogProductMapper.insert(product);
        } else {
            ensureProductExists(product.getId());
            catalogProductMapper.updateById(product);
        }
        return product.getId();
    }

    /**
     * 删除商品。
     *
     * @param id 商品ID
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public void delete(Long id) {
        // 使用逻辑删除保留商品审计记录。
        ensureProductExists(id);
        catalogProductMapper.deleteById(id);
    }

    /**
     * 校验商品存在。
     *
     * @param id 商品ID
     * @author Henfon
     * @date 2026-08-29
     */
    private void ensureProductExists(Long id) {
        if (catalogProductMapper.selectById(id) == null) {
            throw new BusinessException("CATALOG_PRODUCT_NOT_FOUND", "商品不存在");
        }
    }
}
