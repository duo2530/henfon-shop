package com.henfon.shop.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 商品卖点、参数和媒体聚合保存请求。
 *
 * @author Henfon
 * @date 2026-08-31
 */
public record CatalogProductContentSaveRequest(
        @NotNull(message = "卖点列表不能为空") @Size(max = 50, message = "卖点最多支持50条") List<@Valid CatalogProductFeatureRequest> features,
        @NotNull(message = "参数列表不能为空") @Size(max = 100, message = "参数最多支持100条") List<@Valid CatalogProductSpecRequest> specs,
        @NotNull(message = "媒体列表不能为空") @Size(max = 100, message = "媒体最多支持100条") List<@Valid CatalogProductMediaRequest> media
) {
}
