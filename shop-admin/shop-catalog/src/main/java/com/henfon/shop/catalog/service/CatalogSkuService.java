package com.henfon.shop.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.catalog.dto.CatalogSkuSaveRequest;
import com.henfon.shop.catalog.entity.CatalogProduct;
import com.henfon.shop.catalog.entity.CatalogSku;
import com.henfon.shop.catalog.mapper.CatalogProductMapper;
import com.henfon.shop.catalog.mapper.CatalogSkuMapper;
import com.henfon.shop.common.exception.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品 SKU 应用服务。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Service
public class CatalogSkuService {

    private final CatalogSkuMapper skuMapper;
    private final CatalogProductMapper productMapper;

    /**
     * 创建 SKU 服务。
     *
     * @param skuMapper SKU 数据访问对象
     * @param productMapper 商品数据访问对象
     * @author Henfon
     * @date 2026-08-30
     */
    public CatalogSkuService(CatalogSkuMapper skuMapper, CatalogProductMapper productMapper) {
        this.skuMapper = skuMapper;
        this.productMapper = productMapper;
    }

    /**
     * 查询商品的全部未删除 SKU。
     *
     * @param productId 商品ID
     * @return SKU列表
     * @author Henfon
     * @date 2026-08-30
     */
    public List<CatalogSku> listByProduct(Long productId) {
        requireProduct(productId);
        return skuMapper.selectList(new LambdaQueryWrapper<CatalogSku>()
                .eq(CatalogSku::getProductId, productId)
                .orderByAsc(CatalogSku::getId));
    }

    /**
     * 保存或更新商品 SKU，并刷新商品库存和默认价格快照。
     *
     * @param productId 商品ID
     * @param request SKU保存请求
     * @return SKU ID
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public Long save(Long productId, CatalogSkuSaveRequest request) {
        CatalogProduct product = requireProduct(productId);
        if (request.status() != 0 && request.status() != 1) {
            throw new BusinessException("CATALOG_SKU_STATUS_INVALID", "SKU状态必须为0或1");
        }
        CatalogSku sku = new CatalogSku();
        sku.setId(request.id());
        sku.setProductId(productId);
        sku.setSkuCode(request.skuCode().trim());
        sku.setSkuName(request.skuName().trim());
        sku.setAttributesJson(trimToNull(request.attributesJson()));
        sku.setPrice(request.price());
        sku.setMarketPrice(request.marketPrice() == null ? request.price() : request.marketPrice());
        sku.setCostPrice(request.costPrice() == null ? BigDecimal.ZERO : request.costPrice());
        sku.setStock(request.stock());
        sku.setSafetyStock(request.safetyStock());
        sku.setStatus(request.status());
        sku.setRemark(trimToNull(request.remark()));
        try {
            if (sku.getId() == null) {
                skuMapper.insert(sku);
            } else {
                CatalogSku existing = requireSku(sku.getId());
                if (!productId.equals(existing.getProductId())) {
                    throw new BusinessException("CATALOG_SKU_PRODUCT_MISMATCH", "SKU不属于当前商品");
                }
                sku.setVersion(existing.getVersion());
                if (skuMapper.updateById(sku) == 0) {
                    throw new BusinessException("CATALOG_SKU_CONCURRENT", "SKU已被其他操作修改，请刷新后重试");
                }
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("CATALOG_SKU_CODE_EXISTS", "SKU编码已存在");
        }
        refreshProductSnapshot(product);
        return sku.getId();
    }

    /**
     * 修改 SKU 启用状态。
     *
     * @param productId 商品ID
     * @param skuId SKU ID
     * @param status 目标状态
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public void updateStatus(Long productId, Long skuId, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("CATALOG_SKU_STATUS_INVALID", "SKU状态必须为0或1");
        }
        CatalogSku sku = requireSku(skuId);
        if (!productId.equals(sku.getProductId())) {
            throw new BusinessException("CATALOG_SKU_PRODUCT_MISMATCH", "SKU不属于当前商品");
        }
        sku.setStatus(status);
        if (skuMapper.updateById(sku) == 0) {
            throw new BusinessException("CATALOG_SKU_CONCURRENT", "SKU已被其他操作修改，请刷新后重试");
        }
        refreshProductSnapshot(requireProduct(productId));
    }

    /**
     * 逻辑删除 SKU。
     *
     * @param productId 商品ID
     * @param skuId SKU ID
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public void delete(Long productId, Long skuId) {
        CatalogSku sku = requireSku(skuId);
        if (!productId.equals(sku.getProductId())) {
            throw new BusinessException("CATALOG_SKU_PRODUCT_MISMATCH", "SKU不属于当前商品");
        }
        if (skuMapper.deleteById(skuId) == 0) {
            throw new BusinessException("CATALOG_SKU_NOT_FOUND", "SKU不存在");
        }
        refreshProductSnapshot(requireProduct(productId));
    }

    /**
     * 查询并校验商品。
     *
     * @param productId 商品ID
     * @return 商品实体
     * @author Henfon
     * @date 2026-08-30
     */
    private CatalogProduct requireProduct(Long productId) {
        CatalogProduct product = productId == null ? null : productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException("CATALOG_PRODUCT_NOT_FOUND", "商品不存在");
        }
        return product;
    }

    /**
     * 查询并校验 SKU。
     *
     * @param skuId SKU ID
     * @return SKU实体
     * @author Henfon
     * @date 2026-08-30
     */
    private CatalogSku requireSku(Long skuId) {
        CatalogSku sku = skuId == null ? null : skuMapper.selectById(skuId);
        if (sku == null) {
            throw new BusinessException("CATALOG_SKU_NOT_FOUND", "SKU不存在");
        }
        return sku;
    }

    /**
     * 刷新商品的库存、价格和默认SKU编码快照。
     *
     * @param product 商品实体
     * @author Henfon
     * @date 2026-08-30
     */
    private void refreshProductSnapshot(CatalogProduct product) {
        List<CatalogSku> skus = skuMapper.selectList(new LambdaQueryWrapper<CatalogSku>()
                .eq(CatalogSku::getProductId, product.getId())
                .eq(CatalogSku::getStatus, 1)
                .orderByAsc(CatalogSku::getId));
        int stock = skus.stream().map(CatalogSku::getStock).filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum();
        CatalogSku defaultSku = skus.stream().filter(item -> item.getSkuCode().equals(product.getDefaultSkuCode())).findFirst().orElseGet(() -> skus.isEmpty() ? null : skus.get(0));
        product.setCurrentStock(stock);
        if (defaultSku != null) {
            product.setDefaultSkuCode(defaultSku.getSkuCode());
            product.setPrice(defaultSku.getPrice());
            product.setMarketPrice(defaultSku.getMarketPrice());
            product.setCostPrice(defaultSku.getCostPrice());
            product.setSafetyStock(defaultSku.getSafetyStock());
        } else {
            // 所有 SKU 停用或删除后清空商品快照，避免门户继续展示已失效价格。
            product.setDefaultSkuCode(null);
            product.setPrice(BigDecimal.ZERO);
            product.setMarketPrice(BigDecimal.ZERO);
            product.setCostPrice(BigDecimal.ZERO);
            product.setSafetyStock(0);
        }
        productMapper.updateById(product);
    }

    /**
     * 清理可选文本。
     *
     * @param value 原始文本
     * @return 清理后的文本或空值
     * @author Henfon
     * @date 2026-08-30
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
