package com.henfon.shop.ai.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 客服回复一条消息。
 *
 * 会话标识走路径而不是请求体：客服工作台正在接待哪条会话是当前操作上下文，
 * 放在路径上与「结束会话」「订阅消息」的写法一致，不会出现两处不一致的情况。
 *
 * @param content 回复正文
 * @author Henfon
 * @date 2026-09-21
 */
public record AiAgentReplyRequest(@NotBlank(message = "回复内容不能为空") String content) {
}
