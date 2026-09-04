package com.henfon.shop.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** 供应商SKU供货关系保存请求。 @author Henfon @date 2026-09-04 */
public record InventorySupplierStockSaveRequest(
        Long id,
        @NotNull @Positive Long productId,
        @NotNull @Positive Long skuId,
        @NotNull @DecimalMin("0.00") BigDecimal supplyPrice,
        @NotNull @Min(1) Integer minOrderQuantity,
        @NotNull @Min(0) Integer status,
        @Size(max = 500) String remark,
        @Min(0) Integer version) {
}
