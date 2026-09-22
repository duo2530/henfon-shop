package com.henfon.shop.ai.dto;

import java.util.List;

/**
 * 客服工作台里的会话详情。
 *
 * @param conversationId 会话标识
 * @param memberId 买家会员 ID，未登录为空
 * @param title 会话标题，取首条提问
 * @param serviceMode 接待模式
 * @param agentId 接待客服管理员 ID
 * @param messages 最近消息，按时间升序
 * @author Henfon
 * @date 2026-09-21
 */
public record AiAgentSessionDetail(String conversationId,
                                   Long memberId,
                                   String title,
                                   String serviceMode,
                                   Long agentId,
                                   List<AiAgentMessage> messages) {
}
