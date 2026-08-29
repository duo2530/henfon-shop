package com.henfon.shop.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 商品保存请求。
 *
 * @author Henfon
 * @date 2026-08-29
 */
public record CatalogProductSaveRequest(
        Long id,
        Long categoryId,
        String categoryName,
        @NotBlank @Size(max = 200) String productName,
        @NotBlank @Size(max = 64) String productCode,
        @Size(max = 64) String defaultSkuCode,
        @Size(max = 128) String brandName,
        @Size(max = 500) String shortDescription,
        String description,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        @DecimalMin("0.00") BigDecimal marketPrice,
        @DecimalMin("0.00") BigDecimal costPrice,
        Integer currentStock,
        Integer safetyStock,
        String mainImageUrl,
        String tagsCsv,
        Integer status,
        String remark
) {
}
