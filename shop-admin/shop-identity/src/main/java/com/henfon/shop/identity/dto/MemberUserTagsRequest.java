package com.henfon.shop.identity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 会员标签绑定请求。
 *
 * @author Henfon
 * @date 2026-08-30
 */
public record MemberUserTagsRequest(
        @NotNull @Size(max = 20) List<@Size(max = 64) String> tags
) {
}
