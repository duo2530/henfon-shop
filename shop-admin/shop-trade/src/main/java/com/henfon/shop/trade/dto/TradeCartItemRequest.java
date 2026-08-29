package com.henfon.shop.trade.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 购物车明细请求。
 *
 * @param memberId 会员ID
 * @param productId 商品ID
 * @param skuId SKU ID
 * @param quantity 数量
 * @param selected 是否选中
 * @author Henfon
 * @date 2026-08-29
 */
public record TradeCartItemRequest(@NotNull Long memberId, @NotNull Long productId, Long skuId,
                                   @NotNull @Min(1) Integer quantity, Integer selected) {
}
