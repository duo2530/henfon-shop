package com.henfon.shop.reporting.dto;

import java.math.BigDecimal;

/**
 * 商品销售排行项。
 *
 * @param rank 排名
 * @param productId 商品ID
 * @param productName 商品名称
 * @param categoryName 类目名称
 * @param salesVolume 销量（件）
 * @param salesAmount 销售额
 * @param orderCount 订单数
 * @author Henfon
 * @date 2026-09-01
 */
public record ReportingProductRankingItem(
        int rank,
        Long productId,
        String productName,
        String categoryName,
        long salesVolume,
        BigDecimal salesAmount,
        long orderCount) {
}
