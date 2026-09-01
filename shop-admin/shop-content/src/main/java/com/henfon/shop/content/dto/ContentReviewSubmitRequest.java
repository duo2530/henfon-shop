package com.henfon.shop.content.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 门户会员提交评价请求。
 *
 * @author Henfon
 * @date 2026-08-30
 */
public record ContentReviewSubmitRequest(
        @NotNull(message = "评分不能为空")
        @Min(value = 1, message = "评分不能低于1分")
        @Max(value = 5, message = "评分不能高于5分")
        Integer rating,
        @NotBlank(message = "评价内容不能为空")
        @Size(max = 2000, message = "评价内容不能超过2000个字符")
        String reviewContent,
        @Size(max = 500, message = "购买规格不能超过500个字符")
        String variantSummary
) {
}
