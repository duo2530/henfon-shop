package com.henfon.shop.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 商品卖点保存请求。
 *
 * @author Henfon
 * @date 2026-08-31
 */
public record CatalogProductFeatureRequest(
        @NotBlank(message = "卖点文案不能为空") @Size(max = 500, message = "卖点文案不能超过500个字符") String featureText,
        @Min(value = 0, message = "卖点排序号不能为负数") Integer sortNo
) {
}
