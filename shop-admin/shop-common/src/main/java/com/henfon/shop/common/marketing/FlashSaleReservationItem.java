package com.henfon.shop.common.marketing;

/**
 * 秒杀订单商品预占参数。
 *
 * @param productId 商品ID
 * @param skuId SKU ID
 * @param quantity 购买数量
 * @author Henfon
 * @date 2026-09-01
 */
public record FlashSaleReservationItem(Long productId, Long skuId, int quantity) {
}
