package com.henfon.shop.catalog.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 商品批量上下架请求。
 *
 * @author Henfon
 * @date 2026-09-04
 */
public record CatalogProductBatchStatusRequest(
        @NotEmpty(message = "商品ID列表不能为空") List<Long> ids,
        @NotNull(message = "目标状态不能为空") Integer status) {
}
