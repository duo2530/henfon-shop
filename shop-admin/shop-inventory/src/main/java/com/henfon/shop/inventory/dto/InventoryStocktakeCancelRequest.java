package com.henfon.shop.inventory.dto;

import jakarta.validation.constraints.Size;

/**
 * 取消库存盘点请求。
 *
 * @author Henfon
 * @date 2026-09-04
 */
public record InventoryStocktakeCancelRequest(@Size(max = 500) String remark) {
}
