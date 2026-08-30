package com.henfon.shop.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 后台商品类目保存请求。
 *
 * @author Henfon
 * @date 2026-08-30
 */
public record CatalogCategorySaveRequest(
        Long id,
        Long parentId,
        @NotBlank @Size(max = 128) String categoryName,
        @NotBlank @Size(max = 64) String categoryCode,
        Integer sortNo,
        @NotNull Integer status,
        @Size(max = 512) String iconUrl,
        @Size(max = 500) String remark
) {
}
