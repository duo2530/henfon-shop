package com.henfon.shop.common.marketing;

import java.math.BigDecimal;

/**
 * 秒杀订单商品预占参数。
 *
 * @param productId 商品ID
 * @param skuId SKU ID
 * @param quantity 购买数量
 * @param unitPrice 客户端提交的活动成交价
 * @author Henfon
 * @date 2026-09-01
 */
public record FlashSaleReservationItem(Long productId, Long skuId, int quantity, BigDecimal unitPrice) {
}
