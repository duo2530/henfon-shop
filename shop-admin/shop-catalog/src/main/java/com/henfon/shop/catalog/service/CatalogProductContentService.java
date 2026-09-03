package com.henfon.shop.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.catalog.dto.CatalogProductContentSaveRequest;
import com.henfon.shop.catalog.dto.CatalogProductFeatureRequest;
import com.henfon.shop.catalog.dto.CatalogProductMediaRequest;
import com.henfon.shop.catalog.dto.CatalogProductSpecRequest;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品卖点、参数和媒体内容服务。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Service
public class CatalogProductContentService {

    private final CatalogProductMapper productMapper;
    private final CatalogProductFeatureMapper featureMapper;
    private final CatalogProductSpecMapper specMapper;
    private final CatalogProductMediaMapper mediaMapper;
    private final CatalogSkuMapper skuMapper;
    private final MinioStorageService minioStorageService;

    /**
     * 创建商品内容服务。
     *
     * @param productMapper 商品数据访问对象
     * @param featureMapper 卖点数据访问对象
     * @param specMapper 参数数据访问对象
     * @param mediaMapper 媒体数据访问对象
     * @param skuMapper SKU数据访问对象
     * @param minioStorageService MinIO 文件服务
     * @author Henfon
     * @date 2026-08-31
     */
    public CatalogProductContentService(CatalogProductMapper productMapper,
                                        CatalogProductFeatureMapper featureMapper,
                                        CatalogProductSpecMapper specMapper,
                                        CatalogProductMediaMapper mediaMapper,
                                        CatalogSkuMapper skuMapper,
                                        MinioStorageService minioStorageService) {
        this.productMapper = productMapper;
        this.featureMapper = featureMapper;
        this.specMapper = specMapper;
        this.mediaMapper = mediaMapper;
        this.skuMapper = skuMapper;
        this.minioStorageService = minioStorageService;
    }

    /**
     * 查询商品内容聚合数据。
     *
     * @param productId 商品ID
     * @return 卖点、参数和媒体列表
     * @author Henfon
     * @date 2026-08-31
     */
    public Map<String, Object> getContent(Long productId) {
        ensureProductExists(productId);
        // 统一按排序号返回，前端可以直接用于拖拽编辑和门户展示。
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("features", featureMapper.selectList(new LambdaQueryWrapper<CatalogProductFeature>()
                .eq(CatalogProductFeature::getProductId, productId)
                .orderByAsc(CatalogProductFeature::getSortNo)
                .orderByAsc(CatalogProductFeature::getId)));
        result.put("specs", specMapper.selectList(new LambdaQueryWrapper<CatalogProductSpec>()
                .eq(CatalogProductSpec::getProductId, productId)
                .orderByAsc(CatalogProductSpec::getSortNo)
                .orderByAsc(CatalogProductSpec::getId)));
        List<CatalogProductMedia> media = mediaMapper.selectList(new LambdaQueryWrapper<CatalogProductMedia>()
                .eq(CatalogProductMedia::getProductId, productId)
                .orderByAsc(CatalogProductMedia::getSortNo)
                .orderByAsc(CatalogProductMedia::getId));
        // 媒体地址与商品主图一样可能过期，优先按对象键重签，兼容旧数据中的历史地址。
        media.forEach(item -> {
            String reference = item.getObjectKey();
            if (reference == null || reference.isBlank()) {
                reference = item.getMediaUrl();
            }
            item.setMediaUrl(minioStorageService.resolveAccessUrl(reference));
        });
        result.put("media", media);
        return result;
    }

    /**
     * 覆盖保存商品卖点、参数和媒体内容。
     *
     * @param productId 商品ID
     * @param request 聚合保存请求
     * @return 保存后的内容聚合数据
     * @author Henfon
     * @date 2026-08-31
     */
    @Transactional
    public Map<String, Object> replaceContent(Long productId, CatalogProductContentSaveRequest request) {
        ensureProductExists(productId);
        validateMedia(productId, request.media());

        // 使用逻辑删除后整体重建，保证前端拖拽排序和删除操作在一个事务内完成。
        featureMapper.delete(new LambdaQueryWrapper<CatalogProductFeature>()
                .eq(CatalogProductFeature::getProductId, productId));
        specMapper.delete(new LambdaQueryWrapper<CatalogProductSpec>()
                .eq(CatalogProductSpec::getProductId, productId));
        mediaMapper.delete(new LambdaQueryWrapper<CatalogProductMedia>()
                .eq(CatalogProductMedia::getProductId, productId));

        for (int index = 0; index < request.features().size(); index++) {
            CatalogProductFeatureRequest item = request.features().get(index);
            CatalogProductFeature feature = new CatalogProductFeature();
            feature.setProductId(productId);
            feature.setFeatureText(item.featureText().trim());
            feature.setSortNo(item.sortNo() == null ? index : item.sortNo());
            featureMapper.insert(feature);
        }
        for (int index = 0; index < request.specs().size(); index++) {
            CatalogProductSpecRequest item = request.specs().get(index);
            CatalogProductSpec spec = new CatalogProductSpec();
            spec.setProductId(productId);
            spec.setSpecName(item.specName().trim());
            spec.setSpecValue(item.specValue().trim());
            spec.setSortNo(item.sortNo() == null ? index : item.sortNo());
            specMapper.insert(spec);
        }

        CatalogProduct product = productMapper.selectById(productId);
        String coverUrl = null;
        for (int index = 0; index < request.media().size(); index++) {
            CatalogProductMediaRequest item = request.media().get(index);
            CatalogProductMedia media = new CatalogProductMedia();
            media.setProductId(productId);
            media.setSkuId(item.skuId());
            media.setMediaType(item.mediaType().trim().toUpperCase());
            media.setObjectKey(minioStorageService.normalizeReference(item.objectKey().trim()));
            media.setMediaUrl(item.mediaUrl() == null ? null : minioStorageService.normalizeReference(item.mediaUrl().trim()));
            media.setIsCover(item.coverFlag());
            media.setSortNo(item.sortNo() == null ? index : item.sortNo());
            media.setRemark(item.remark() == null ? null : item.remark().trim());
            mediaMapper.insert(media);
            if (Integer.valueOf(1).equals(item.coverFlag()) && coverUrl == null) {
                coverUrl = media.getObjectKey();
            }
        }

        // 主图是商品列表的快速展示字段，与媒体封面保持一致，避免两套数据不一致。
        product.setMainImageUrl(coverUrl);
        if (productMapper.updateById(product) == 0) {
            throw new BusinessException("CATALOG_PRODUCT_CONCURRENT", "商品已被其他操作修改，请刷新后重试");
        }
        return getContent(productId);
    }

    /**
     * 校验媒体与商品、SKU的归属关系。
     *
     * @param productId 商品ID
     * @param mediaItems 媒体列表
     * @author Henfon
     * @date 2026-08-31
     */
    private void validateMedia(Long productId, List<CatalogProductMediaRequest> mediaItems) {
        // 封面和SKU归属在事务写入前校验，避免产生无法展示的媒体记录。
        long coverCount = mediaItems.stream().filter(item -> Integer.valueOf(1).equals(item.coverFlag())).count();
        if (coverCount > 1) {
            throw new BusinessException("CATALOG_MEDIA_COVER_DUPLICATE", "一个商品最多只能设置一个媒体封面");
        }
        for (CatalogProductMediaRequest item : mediaItems) {
            String objectKey = item.objectKey().trim();
            if (objectKey.startsWith("/") || objectKey.contains("..")) {
                throw new BusinessException("CATALOG_MEDIA_OBJECT_KEY_INVALID", "媒体对象键格式不合法");
            }
            if (item.skuId() != null) {
                CatalogSku sku = skuMapper.selectById(item.skuId());
                if (sku == null || !productId.equals(sku.getProductId())) {
                    throw new BusinessException("CATALOG_MEDIA_SKU_INVALID", "媒体关联的SKU不属于当前商品");
                }
            }
        }
    }

    /**
     * 校验商品存在。
     *
     * @param productId 商品ID
     * @author Henfon
     * @date 2026-08-31
     */
    private void ensureProductExists(Long productId) {
        // 商品内容不能脱离商品主记录单独存在。
        if (productMapper.selectById(productId) == null) {
            throw new BusinessException("CATALOG_PRODUCT_NOT_FOUND", "商品不存在");
        }
    }
}
