package com.henfon.shop.trade.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 登录后合并本地购物车明细请求。
 *
 * @param productId 商品ID
 * @param skuId SKU ID
 * @param quantity 合并数量
 * @param selected 选中状态
 * @author Henfon
 * @date 2026-09-04
 */
public record TradeCartMergeItemRequest(@NotNull Long productId, Long skuId,
                                        @NotNull @Min(1) Integer quantity, Integer selected) {
}
