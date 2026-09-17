package com.henfon.shop.marketing.dto;

import lombok.Data;

/**
 * 秒杀活动商品的库存聚合结果。
 *
 * <p>对应 {@code MarketingFlashSaleItemMapper#summarizeStock(Long)} 的查询结果，
 * 由数据库一次性汇总活动商品数量与库存，避免把明细读进内存累加。</p>
 *
 * @author Henfon
 * @date 2026-09-17
 */
@Data
public class MarketingFlashSaleStockSummary {

    /** 活动商品数量。 */
    private Long itemCount;

    /** 活动总库存。 */
    private Long totalStock;

    /** 已售活动库存。 */
    private Long soldStock;
}
