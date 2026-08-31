package com.henfon.shop.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 商品参数保存请求。
 *
 * @author Henfon
 * @date 2026-08-31
 */
public record CatalogProductSpecRequest(
        @NotBlank(message = "参数名称不能为空") @Size(max = 128, message = "参数名称不能超过128个字符") String specName,
        @NotBlank(message = "参数值不能为空") @Size(max = 500, message = "参数值不能超过500个字符") String specValue,
        @Min(value = 0, message = "参数排序号不能为负数") Integer sortNo
) {
}
