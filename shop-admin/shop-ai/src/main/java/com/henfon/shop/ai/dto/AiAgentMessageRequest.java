package com.henfon.shop.ai.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 人工会话里发送一条消息。
 *
 * 买家与客服共用这个请求体：两边的区别在服务端按接口判断，不让调用方自己声明身份——
 * 身份来自令牌，不应该由请求体决定。
 *
 * @param conversationId 会话标识
 * @param content 消息正文
 * @author Henfon
 * @date 2026-09-21
 */
public record AiAgentMessageRequest(@NotBlank(message = "会话标识不能为空") String conversationId,
                                    @NotBlank(message = "消息内容不能为空") String content) {
}
