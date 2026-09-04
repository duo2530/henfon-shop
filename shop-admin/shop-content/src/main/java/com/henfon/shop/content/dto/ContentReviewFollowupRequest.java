package com.henfon.shop.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 会员商品评价追评请求。
 *
 * @author Henfon
 * @date 2026-09-04
 */
public record ContentReviewFollowupRequest(
        @NotBlank(message = "追评内容不能为空")
        @Size(max = 2000, message = "追评内容不能超过2000个字符")
        String content
) {
}
