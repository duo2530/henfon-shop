package com.henfon.shop.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.catalog.entity.CatalogProduct;
import com.henfon.shop.catalog.entity.CatalogSku;
import com.henfon.shop.catalog.mapper.CatalogProductMapper;
import com.henfon.shop.catalog.mapper.CatalogSkuMapper;
import com.henfon.shop.integration.storage.MinioStorageService;
import com.henfon.shop.marketing.dto.MarketingFlashSalePortalResponse;
import com.henfon.shop.marketing.entity.MarketingFlashSale;
import com.henfon.shop.marketing.entity.MarketingFlashSaleItem;
import com.henfon.shop.marketing.mapper.MarketingFlashSaleItemMapper;
import com.henfon.shop.marketing.mapper.MarketingFlashSaleMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 门户秒杀活动查询服务。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Service
public class MarketingFlashSalePortalService {

    private final MarketingFlashSaleMapper activityMapper;
    private final MarketingFlashSaleItemMapper itemMapper;
    private final CatalogProductMapper productMapper;
    private final CatalogSkuMapper skuMapper;
    private final MinioStorageService minioStorageService;

    /**
     * 创建门户秒杀查询服务。
     *
     * @param activityMapper 秒杀活动数据访问对象
     * @param itemMapper 秒杀活动商品数据访问对象
     * @param productMapper 商品目录数据访问对象
     * @param skuMapper 商品SKU数据访问对象
     * @param minioStorageService MinIO 文件服务
     * @author Henfon
     * @date 2026-09-01
     */
    public MarketingFlashSalePortalService(MarketingFlashSaleMapper activityMapper,
                                           MarketingFlashSaleItemMapper itemMapper,
                                           CatalogProductMapper productMapper,
                                           CatalogSkuMapper skuMapper,
                                           MinioStorageService minioStorageService) {
        this.activityMapper = activityMapper;
        this.itemMapper = itemMapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.minioStorageService = minioStorageService;
    }

    /**
     * 查询当前时间窗口内仍有可售商品的秒杀活动。
     *
     * @return 门户秒杀活动列表
     * @author Henfon
     * @date 2026-09-01
     */
    public List<MarketingFlashSalePortalResponse> activeFlashSales() {
        LocalDateTime now = LocalDateTime.now();
        List<MarketingFlashSale> activities = activityMapper.selectList(new LambdaQueryWrapper<MarketingFlashSale>()
                .eq(MarketingFlashSale::getStatus, 1)
                .le(MarketingFlashSale::getStartAt, now)
                .ge(MarketingFlashSale::getEndAt, now)
                .orderByAsc(MarketingFlashSale::getStartAt)
                .orderByAsc(MarketingFlashSale::getId));
        if (activities == null || activities.isEmpty()) {
            return Collections.emptyList();
        }

        // 一次性加载所有活动商品，避免门户首页按活动逐个查询造成 N+1 请求。
        List<Long> activityIds = activities.stream()
                .map(MarketingFlashSale::getId)
                .filter(Objects::nonNull)
                .toList();
        if (activityIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<MarketingFlashSaleItem> items = itemMapper.selectList(new LambdaQueryWrapper<MarketingFlashSaleItem>()
                .in(MarketingFlashSaleItem::getActivityId, activityIds)
                .eq(MarketingFlashSaleItem::getStatus, 1)
                .apply("sold_stock < total_stock")
                .orderByAsc(MarketingFlashSaleItem::getId));
        List<MarketingFlashSaleItem> availableItems = items == null ? Collections.emptyList() : items;
        Map<Long, CatalogProduct> productsById = loadProducts(availableItems);
        Map<Long, CatalogSku> skusById = loadSkus(availableItems);
        Map<Long, List<MarketingFlashSaleItem>> itemsByActivity = availableItems
                .stream()
                .filter(item -> item.getActivityId() != null)
                .filter(item -> isSellableItem(item, productsById, skusById))
                .collect(Collectors.groupingBy(MarketingFlashSaleItem::getActivityId,
                        LinkedHashMap::new, Collectors.toList()));

        List<MarketingFlashSalePortalResponse> result = new ArrayList<>();
        for (MarketingFlashSale activity : activities) {
            List<MarketingFlashSaleItem> activityItems = itemsByActivity.getOrDefault(activity.getId(), Collections.emptyList());
            if (activityItems.isEmpty()) {
                // 活动虽然在时间窗口内，但商品售罄时不对门户展示。
                continue;
            }
            List<MarketingFlashSalePortalResponse.Item> responseItems = activityItems.stream()
                    .map(item -> toResponseItem(item, productsById.get(item.getProductId()),
                            item.getSkuId() == null ? null : skusById.get(item.getSkuId())))
                    .toList();
            result.add(new MarketingFlashSalePortalResponse(activity.getId(), activity.getActivityCode(),
                    activity.getActivityName(), activity.getStartAt(), activity.getEndAt(),
                    activity.getLimitPerMember(), responseItems));
        }
        return result;
    }

    /**
     * 将活动商品实体转换为门户库存摘要。
     *
     * @param item 活动商品实体
     * @param product 商品目录实体
     * @param sku 商品SKU实体
     * @return 门户活动商品
     * @author Henfon
     * @date 2026-09-01
     */
    private MarketingFlashSalePortalResponse.Item toResponseItem(MarketingFlashSaleItem item,
                                                                  CatalogProduct product,
                                                                  CatalogSku sku) {
        int totalStock = item.getTotalStock() == null ? 0 : item.getTotalStock();
        int soldStock = item.getSoldStock() == null ? 0 : item.getSoldStock();
        // 数据库约束保证已售不超过总库存，额外兜底避免异常历史数据向前端返回负库存。
        int remainingStock = Math.max(totalStock - soldStock, 0);
        // 商品主图在库中可能是历史预签名地址，返回前统一换发当前有效地址，避免门户展示失效图片。
        String mainImageUrl = minioStorageService.resolveAccessUrl(product.getMainImageUrl());
        return new MarketingFlashSalePortalResponse.Item(item.getId(), item.getProductId(), item.getSkuId(),
                product.getProductName(), sku == null ? null : sku.getSkuName(), mainImageUrl,
                sku == null ? product.getPrice() : sku.getPrice(), sku == null ? null : sku.getAttributesJson(),
                item.getActivityPrice(), totalStock, soldStock, remainingStock, item.getLimitPerMember());
    }

    /**
     * 批量加载秒杀商品对应的商品目录，避免门户按商品逐个请求详情。
     *
     * @param items 秒杀活动商品
     * @return 商品ID到商品实体的映射
     * @author Henfon
     * @date 2026-09-12
     */
    private Map<Long, CatalogProduct> loadProducts(List<MarketingFlashSaleItem> items) {
        List<Long> productIds = items.stream().map(MarketingFlashSaleItem::getProductId)
                .filter(Objects::nonNull).distinct().toList();
        if (productIds.isEmpty()) return Collections.emptyMap();
        List<CatalogProduct> products = productMapper.selectList(new LambdaQueryWrapper<CatalogProduct>()
                .in(CatalogProduct::getId, productIds)
                .eq(CatalogProduct::getStatus, 1));
        return (products == null ? Collections.<CatalogProduct>emptyList() : products).stream()
                .collect(Collectors.toMap(CatalogProduct::getId, product -> product, (left, right) -> left));
    }

    /**
     * 批量加载秒杀商品对应的启用 SKU，保证门户获得独立于商品分页的 SKU 快照。
     *
     * @param items 秒杀活动商品
     * @return SKU ID到SKU实体的映射
     * @author Henfon
     * @date 2026-09-12
     */
    private Map<Long, CatalogSku> loadSkus(List<MarketingFlashSaleItem> items) {
        List<Long> skuIds = items.stream().map(MarketingFlashSaleItem::getSkuId)
                .filter(Objects::nonNull).distinct().toList();
        if (skuIds.isEmpty()) return Collections.emptyMap();
        List<CatalogSku> skus = skuMapper.selectList(new LambdaQueryWrapper<CatalogSku>()
                .in(CatalogSku::getId, skuIds)
                .eq(CatalogSku::getStatus, 1));
        return (skus == null ? Collections.<CatalogSku>emptyList() : skus).stream()
                .collect(Collectors.toMap(CatalogSku::getId, sku -> sku, (left, right) -> left));
    }

    /**
     * 判断秒杀明细是否仍关联可售商品和正确归属的启用 SKU。
     *
     * @param item 秒杀商品明细
     * @param productsById 商品映射
     * @param skusById SKU映射
     * @return 是否可以展示和购买
     * @author Henfon
     * @date 2026-09-12
     */
    private boolean isSellableItem(MarketingFlashSaleItem item, Map<Long, CatalogProduct> productsById,
                                   Map<Long, CatalogSku> skusById) {
        CatalogProduct product = productsById.get(item.getProductId());
        if (product == null) return false;
        if (item.getSkuId() == null) return true;
        CatalogSku sku = skusById.get(item.getSkuId());
        return sku != null && Objects.equals(product.getId(), sku.getProductId());
    }
}
