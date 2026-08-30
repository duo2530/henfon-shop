package com.henfon.shop.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 商品 SKU 保存请求。
 *
 * @author Henfon
 * @date 2026-08-30
 */
public record CatalogSkuSaveRequest(
        Long id,
        @NotBlank @Size(max = 64) String skuCode,
        @NotBlank @Size(max = 200) String skuName,
        @Size(max = 2000) String attributesJson,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        @DecimalMin("0.00") BigDecimal marketPrice,
        @DecimalMin("0.00") BigDecimal costPrice,
        @NotNull @PositiveOrZero Integer stock,
        @NotNull @PositiveOrZero Integer safetyStock,
        @NotNull Integer status,
        String remark
) {
}
