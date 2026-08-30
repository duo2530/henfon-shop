package com.henfon.shop.inventory.dto;

/**
 * 订单库存预占明细。
 *
 * @param productId 商品ID
 * @param skuId SKU ID
 * @param quantity 预占数量
 * @author Henfon
 * @date 2026-08-30
 */
public record InventoryReservationItem(Long productId, Long skuId, int quantity) {
}
