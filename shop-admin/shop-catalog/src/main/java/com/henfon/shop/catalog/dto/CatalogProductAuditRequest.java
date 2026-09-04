package com.henfon.shop.catalog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 商品审核请求。
 *
 * @author Henfon
 * @date 2026-09-04
 */
public record CatalogProductAuditRequest(
        @NotNull(message = "审核结果不能为空") Boolean approved,
        @Size(max = 500, message = "审核备注长度不能超过500个字符") String remark) {
}
