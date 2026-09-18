package com.henfon.shop.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.henfon.shop.catalog.dto.CatalogProductSaveRequest;
import com.henfon.shop.catalog.entity.CatalogProduct;
import com.henfon.shop.catalog.entity.CatalogSku;
import com.henfon.shop.catalog.mapper.CatalogProductMapper;
import com.henfon.shop.catalog.mapper.CatalogSkuMapper;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.integration.storage.MinioStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 商品目录应用服务。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Service
public class CatalogProductService {

    private final CatalogProductMapper catalogProductMapper;
    private final CatalogSkuMapper catalogSkuMapper;
    private final MinioStorageService minioStorageService;
    private final CatalogCategoryService categoryService;

    /**
     * 创建商品目录服务。
     *
     * @param catalogProductMapper 商品数据访问对象
     * @param catalogSkuMapper SKU数据访问对象
     * @param minioStorageService MinIO 文件服务
     * @param categoryService 类目服务
     * @author Henfon
     * @date 2026-08-29
     */
    public CatalogProductService(CatalogProductMapper catalogProductMapper,
                                 CatalogSkuMapper catalogSkuMapper,
                                 MinioStorageService minioStorageService,
                                 CatalogCategoryService categoryService) {
        this.catalogProductMapper = catalogProductMapper;
        this.catalogSkuMapper = catalogSkuMapper;
        this.minioStorageService = minioStorageService;
        this.categoryService = categoryService;
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
        // 商品挂在类目树叶子节点上，按一级或二级类目筛选时要把整棵子树都算进来。
        List<Long> categoryScope = categoryService.subtreeCategoryIds(categoryId);
        LambdaQueryWrapper<CatalogProduct> wrapper = new LambdaQueryWrapper<CatalogProduct>()
                .in(!categoryScope.isEmpty(), CatalogProduct::getCategoryId, categoryScope)
                .eq(status != null, CatalogProduct::getStatus, status)
                .and(StringUtils.hasText(keyword), query -> query
                        .like(CatalogProduct::getProductName, keyword)
                        .or().like(CatalogProduct::getProductCode, keyword)
                        .or().like(CatalogProduct::getDefaultSkuCode, keyword))
                .orderByDesc(CatalogProduct::getCreatedAt);
        IPage<CatalogProduct> productPage = catalogProductMapper.selectPage(new Page<>(safeCurrent, safeSize), wrapper);
        // 商品主图可能是历史预签名地址，返回接口前统一生成当前有效地址。
        productPage.getRecords().forEach(product -> product.setMainImageUrl(
                minioStorageService.resolveAccessUrl(product.getMainImageUrl())));
        return productPage;
    }

    /**
     * 按主键批量查询商品。
     *
     * <p>调用方（如秒杀活动编辑）只持有商品主键，需要拿回名称与主图。主键去重后
     * 限制在 200 个以内，避免超长 IN 查询。</p>
     *
     * @param ids 商品ID集合
     * @return 命中的商品列表，未命中的ID自动忽略
     * @author Henfon
     * @date 2026-09-16
     */
    public List<CatalogProduct> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> safeIds = ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .limit(200)
                .toList();
        if (safeIds.isEmpty()) {
            return List.of();
        }
        List<CatalogProduct> products = catalogProductMapper.selectBatchIds(safeIds);
        // 与分页查询保持一致：主图统一换发当前有效的预签名地址。
        products.forEach(product -> product.setMainImageUrl(
                minioStorageService.resolveAccessUrl(product.getMainImageUrl())));
        return products;
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
        // 只持久化稳定对象键，避免商品主图在预签名过期后无法显示。
        product.setMainImageUrl(minioStorageService.normalizeReference(request.mainImageUrl()));
        product.setTagsCsv(request.tagsCsv());
        product.setStatus(request.status() == null ? 1 : request.status());
        // 新建商品若直接上架，视为已通过审核；草稿商品保持待审核状态。
        if (product.getId() == null) {
            product.setAuditStatus(product.getStatus() == 1 ? 1 : 0);
        }
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
     * 复制商品及其 SKU，生成草稿商品供后台二次编辑。
     *
     * @param id 原商品ID
     * @return 新商品ID
     * @author Henfon
     * @date 2026-09-04
     */
    @Transactional
    public Long copy(Long id) {
        CatalogProduct source = catalogProductMapper.selectById(id);
        if (source == null) {
            throw new BusinessException("CATALOG_PRODUCT_NOT_FOUND", "商品不存在");
        }
        // 复制商品时重置销售状态和销量，避免草稿直接影响线上数据。
        CatalogProduct target = new CatalogProduct();
        target.setCategoryId(source.getCategoryId());
        target.setCategoryName(source.getCategoryName());
        target.setProductName(source.getProductName() + "-副本");
        target.setProductCode(nextProductCode(source.getProductCode()));
        target.setBrandName(source.getBrandName());
        target.setShortDescription(source.getShortDescription());
        target.setDescription(source.getDescription());
        target.setPrice(source.getPrice());
        target.setMarketPrice(source.getMarketPrice());
        target.setCostPrice(source.getCostPrice());
        target.setWeightGram(source.getWeightGram());
        target.setCurrentStock(0);
        target.setSafetyStock(source.getSafetyStock());
        target.setSalesCount(0L);
        target.setMainImageUrl(source.getMainImageUrl());
        target.setTagsCsv(source.getTagsCsv());
        target.setStatus(0);
        target.setAuditStatus(0);
        target.setRemark(source.getRemark());
        catalogProductMapper.insert(target);

        // SKU 编码必须全局唯一，复制时统一追加副本后缀。
        List<CatalogSku> sourceSkus = catalogSkuMapper.selectList(new LambdaQueryWrapper<CatalogSku>()
                .eq(CatalogSku::getProductId, id).orderByAsc(CatalogSku::getId));
        for (CatalogSku sourceSku : sourceSkus) {
            CatalogSku targetSku = new CatalogSku();
            targetSku.setProductId(target.getId());
            targetSku.setSkuCode(nextSkuCode(sourceSku.getSkuCode()));
            targetSku.setSkuName(sourceSku.getSkuName());
            targetSku.setBarcode(sourceSku.getBarcode());
            targetSku.setAttributesJson(sourceSku.getAttributesJson());
            targetSku.setPrice(sourceSku.getPrice());
            targetSku.setMarketPrice(sourceSku.getMarketPrice());
            targetSku.setCostPrice(sourceSku.getCostPrice());
            targetSku.setStock(sourceSku.getStock());
            targetSku.setSafetyStock(sourceSku.getSafetyStock());
            targetSku.setStatus(sourceSku.getStatus());
            targetSku.setRemark(sourceSku.getRemark());
            catalogSkuMapper.insert(targetSku);
            if (target.getDefaultSkuCode() == null && sourceSku.getSkuCode().equals(source.getDefaultSkuCode())) {
                target.setDefaultSkuCode(targetSku.getSkuCode());
            }
        }
        // 无默认 SKU 时保持空值；有 SKU 时使用复制后的默认编码。
        catalogProductMapper.updateById(target);
        return target.getId();
    }

    /**
     * 生成不重复的商品编码。
     *
     * @param sourceCode 原商品编码
     * @return 新商品编码
     * @author Henfon
     * @date 2026-09-04
     */
    private String nextProductCode(String sourceCode) {
        String base = withCopySuffix(sourceCode);
        String candidate = base;
        int index = 1;
        while (catalogProductMapper.selectOne(new LambdaQueryWrapper<CatalogProduct>()
                .eq(CatalogProduct::getProductCode, candidate)) != null) {
            candidate = base + index++;
        }
        return candidate;
    }

    /**
     * 生成不重复的 SKU 编码。
     *
     * @param sourceCode 原 SKU 编码
     * @return 新 SKU 编码
     * @author Henfon
     * @date 2026-09-04
     */
    private String nextSkuCode(String sourceCode) {
        String base = withCopySuffix(sourceCode);
        String candidate = base;
        int index = 1;
        while (catalogSkuMapper.selectOne(new LambdaQueryWrapper<CatalogSku>()
                .eq(CatalogSku::getSkuCode, candidate)) != null) {
            candidate = base + index++;
        }
        return candidate;
    }

    /**
     * 追加副本后缀并限制编码长度，避免超出数据库字段上限。
     *
     * @param sourceCode 原编码
     * @return 带副本后缀的编码
     * @author Henfon
     * @date 2026-09-04
     */
    private String withCopySuffix(String sourceCode) {
        String suffix = "-COPY";
        String normalized = sourceCode == null ? "PRODUCT" : sourceCode.trim();
        int maxPrefixLength = Math.max(1, 64 - suffix.length());
        if (normalized.length() > maxPrefixLength) {
            normalized = normalized.substring(0, maxPrefixLength);
        }
        return normalized + suffix;
    }

    /**
     * 修改商品状态并校验上下架条件。
     *
     * @param id 商品ID
     * @param status 目标状态：0草稿、1上架、2下架
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1 && status != 2)) {
            throw new BusinessException("CATALOG_PRODUCT_STATUS_INVALID", "商品状态必须为0、1或2");
        }
        ensureProductExists(id);
        CatalogProduct product = catalogProductMapper.selectById(id);
        if (status == 1) {
            List<CatalogSku> enabledSkus = catalogSkuMapper.selectList(new LambdaQueryWrapper<CatalogSku>()
                    .eq(CatalogSku::getProductId, id)
                    .eq(CatalogSku::getStatus, 1));
            if (enabledSkus.isEmpty()) {
                throw new BusinessException("CATALOG_PRODUCT_SKU_REQUIRED", "商品至少需要一个启用的SKU才能上架");
            }
            if (enabledSkus.stream().allMatch(sku -> sku.getStock() == null || sku.getStock() <= 0)) {
                throw new BusinessException("CATALOG_PRODUCT_STOCK_REQUIRED", "商品库存不足，无法上架");
            }
        }
        product.setStatus(status);
        if (catalogProductMapper.updateById(product) == 0) {
            throw new BusinessException("CATALOG_PRODUCT_CONCURRENT", "商品已被其他操作修改，请刷新后重试");
        }
    }

    /**
     * 审核商品并在通过时自动上架。
     *
     * @param id 商品ID
     * @param approved 是否通过
     * @param remark 审核备注
     * @author Henfon
     * @date 2026-09-04
     */
    @Transactional
    public void audit(Long id, boolean approved, String remark) {
        CatalogProduct product = catalogProductMapper.selectById(id);
        if (product == null) {
            throw new BusinessException("CATALOG_PRODUCT_NOT_FOUND", "商品不存在");
        }
        // 审核通过复用上架校验，确保商品具备可销售 SKU 和库存。
        if (approved) {
            updateStatus(id, 1);
            product = catalogProductMapper.selectById(id);
            product.setAuditStatus(1);
        } else {
            product.setAuditStatus(2);
            product.setStatus(2);
        }
        product.setAuditRemark(remark);
        catalogProductMapper.updateById(product);
    }

    /**
     * 批量修改商品上下架状态。
     *
     * @param ids 商品ID列表
     * @param status 目标状态
     * @author Henfon
     * @date 2026-09-04
     */
    @Transactional
    public void batchUpdateStatus(List<Long> ids, Integer status) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("CATALOG_PRODUCT_IDS_REQUIRED", "商品ID列表不能为空");
        }
        if (ids.size() > 100) {
            throw new BusinessException("CATALOG_PRODUCT_IDS_TOO_MANY", "单次最多处理100件商品");
        }
        // 逐个执行状态校验，任一失败则事务整体回滚，避免出现部分成功。
        ids.stream().distinct().forEach(id -> updateStatus(id, status));
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
