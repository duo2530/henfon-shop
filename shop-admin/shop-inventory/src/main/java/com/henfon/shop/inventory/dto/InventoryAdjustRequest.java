package com.henfon.shop.inventory.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 库存调整请求。
 *
 * @param changeQuantity 调整数量，正数入库、负数出库
 * @param remark 调整原因
 * @author Henfon
 * @date 2026-08-30
 */
public record InventoryAdjustRequest(@NotNull Integer changeQuantity, String remark) {
}
