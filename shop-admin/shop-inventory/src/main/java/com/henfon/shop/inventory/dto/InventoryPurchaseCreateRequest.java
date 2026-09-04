package com.henfon.shop.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建采购单请求。
 *
 * @author Henfon
 * @date 2026-09-04
 */
public record InventoryPurchaseCreateRequest(
        @NotNull @Positive Long supplierId,
        @NotNull @Positive Long warehouseId,
        @NotEmpty List<@Valid Item> items,
        @Size(max = 500) String remark) {
    /**
     * 采购明细请求。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    public record Item(@NotNull @Positive Long productId, @NotNull @Positive Long skuId,
                       @NotNull @Positive Integer quantity, @NotNull BigDecimal unitPrice,
                       @Size(max = 500) String remark) {
    }
}
