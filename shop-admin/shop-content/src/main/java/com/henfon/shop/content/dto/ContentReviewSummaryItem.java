package com.henfon.shop.content.dto;

/**
 * 商品评价统计项。
 *
 * <p>门户列表与详情需要展示评价条数和平均分，这里按商品聚合返回，避免逐条查询。</p>
 *
 * @param productId 商品ID
 * @param reviewCount 已审核通过的评价条数
 * @param avgRating 已审核通过评价的平均分，保留一位小数
 * @author Henfon
 * @date 2026-09-18
 */
public record ContentReviewSummaryItem(Long productId, long reviewCount, double avgRating) {
}
