package com.henfon.shop.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 买家提交会话评价。
 *
 * 评分必填、标签与留言可空：把"写点什么"设成必填会让大量买家直接关掉弹窗，
 * 评价率比多几句文字更有价值。
 *
 * @param conversationId 被评价的会话标识
 * @param score 满意度评分，1-5 星
 * @param tags 评价标签，可空
 * @param comment 评价留言，可空
 * @author Henfon
 * @date 2026-09-21
 */
public record AiRatingRequest(@NotBlank(message = "会话标识不能为空") String conversationId,
                              @NotNull(message = "请先选择评分")
                              @Min(value = 1, message = "评分最低 1 星")
                              @Max(value = 5, message = "评分最高 5 星") Integer score,
                              List<@Size(max = 16, message = "单个标签过长") String> tags,
                              @Size(max = 200, message = "评价留言不能超过 200 个字符") String comment) {
}
