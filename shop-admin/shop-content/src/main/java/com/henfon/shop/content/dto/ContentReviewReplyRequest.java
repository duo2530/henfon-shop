package com.henfon.shop.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 评价商家回复请求。
 *
 * @author Henfon
 * @date 2026-08-30
 */
public record ContentReviewReplyRequest(
        @NotBlank(message = "回复内容不能为空")
        @Size(max = 2000, message = "回复内容不能超过2000个字符")
        String replyContent
) {
}
