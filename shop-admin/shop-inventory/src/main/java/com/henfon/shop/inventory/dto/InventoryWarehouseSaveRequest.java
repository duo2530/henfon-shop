package com.henfon.shop.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 后台仓库保存请求。
 *
 * @author Henfon
 * @date 2026-08-31
 */
public record InventoryWarehouseSaveRequest(
        Long id,
        @NotBlank @Size(max = 64) String warehouseCode,
        @NotBlank @Size(max = 128) String warehouseName,
        @NotNull @Min(0) @Max(1) Integer status,
        @NotNull @Min(0) @Max(1) Integer isDefault,
        @Size(max = 500) String remark,
        @Min(0) Integer version
) {
}
