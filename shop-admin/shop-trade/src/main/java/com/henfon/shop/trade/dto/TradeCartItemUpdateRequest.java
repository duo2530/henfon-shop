package com.henfon.shop.trade.dto;

import jakarta.validation.constraints.Min;

/**
 * 购物车明细更新请求。
 *
 * @param quantity 数量
 * @param selected 是否选中
 * @author Henfon
 * @date 2026-08-29
 */
public record TradeCartItemUpdateRequest(@Min(1) Integer quantity, Integer selected) {
}
