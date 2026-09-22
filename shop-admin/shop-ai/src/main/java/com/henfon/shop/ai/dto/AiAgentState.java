package com.henfon.shop.ai.dto;

import com.henfon.shop.ai.entity.AiConversation;

/**
 * 买家侧看到的会话接待状态。
 *
 * 只给买家需要的字段：他要知道现在是谁在接待、排队排了多久，不需要知道接待客服的管理员 ID。
 * 买家刷新页面后用它恢复上一次的会话，避免聊到一半刷新就断掉。
 *
 * @param conversationId 会话标识
 * @param serviceMode 接待模式
 * @param title 会话标题
 * @param messageCount 消息条数
 * @param waitingSeconds 已等待秒数，非排队状态为 null
 * @author Henfon
 * @date 2026-09-21
 */
public record AiAgentState(String conversationId,
                           String serviceMode,
                           String title,
                           Integer messageCount,
                           Long waitingSeconds) {

    /**
     * 由会话实体构建。
     *
     * @param conversation 会话
     * @return 状态
     * @author Henfon
     * @date 2026-09-21
     */
    public static AiAgentState from(AiConversation conversation) {
        return new AiAgentState(conversation.getConversationId(),
                conversation.getServiceMode(),
                conversation.getTitle(),
                conversation.getMessageCount(),
                AiAgentSessionView.waitingSeconds(conversation.getServiceMode(), conversation.getAgentRequestedAt()));
    }
}
