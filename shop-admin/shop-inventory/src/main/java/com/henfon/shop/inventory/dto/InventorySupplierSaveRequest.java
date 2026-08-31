package com.henfon.shop.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 后台供应商保存请求。
 *
 * @author Henfon
 * @date 2026-08-31
 */
public record InventorySupplierSaveRequest(
        Long id,
        @NotBlank @Size(max = 64) String supplierCode,
        @NotBlank @Size(max = 128) String supplierName,
        @Size(max = 64) String contactName,
        @Size(max = 32) String contactPhone,
        @Size(max = 255) String address,
        @NotNull @Min(0) @Max(1) Integer status,
        @Size(max = 500) String remark,
        @Min(0) Integer version
) {
}
