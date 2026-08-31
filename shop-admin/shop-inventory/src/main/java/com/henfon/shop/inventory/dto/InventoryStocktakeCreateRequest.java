package com.henfon.shop.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 创建库存盘点单请求。
 *
 * @author Henfon
 * @date 2026-08-31
 */
public record InventoryStocktakeCreateRequest(
        @NotNull @Positive Long warehouseId,
        @Size(max = 500) String remark
) {
}
