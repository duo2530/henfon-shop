package com.henfon.shop.marketing.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 门户展示的进行中秒杀活动及商品明细。
 *
 * @author Henfon
 * @date 2026-09-01
 */
public record MarketingFlashSalePortalResponse(
        Long id,
        String activityCode,
        String activityName,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Integer limitPerMember,
        List<Item> items
) {

    /**
     * 门户展示的秒杀商品库存摘要。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    public record Item(
            Long id,
            Long productId,
            Long skuId,
            String productName,
            String skuName,
            String imageUrl,
            BigDecimal originalPrice,
            String attributesJson,
            BigDecimal activityPrice,
            Integer totalStock,
            Integer soldStock,
            Integer remainingStock,
            Integer limitPerMember
    ) {
    }
}
