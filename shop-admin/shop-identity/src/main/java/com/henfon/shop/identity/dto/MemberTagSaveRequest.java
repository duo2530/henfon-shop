package com.henfon.shop.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 保存会员标签请求。
 *
 * @author Henfon
 * @date 2026-08-30
 */
public record MemberTagSaveRequest(
        @NotBlank @Size(max = 64) String tagName,
        Integer sortNo,
        Integer status
) {
}
