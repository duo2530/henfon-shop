package com.henfon.shop.ai.dto;

import com.henfon.shop.ai.entity.AiConversation;
import com.henfon.shop.ai.service.AiConversationService;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 客服工作台里的会话条目。
 *
 * 等待时长由服务端算好再下发，不让前端拿两个时间相减：前端时钟与服务器可能差几分钟，
 * 队列里排在第一位的会话显示出负数等待时长会很难解释。非排队状态为 null。
 *
 * memberName 与 lastMessage 是给队列读的：标题只是"买家对机器说的第一句话"，可能是
 * 「你是」这种片段，光看它认不出是谁在等、要办什么事。买家名字解决"是谁"，最近一条买家
 * 原话解决"要办什么"。
 *
 * @param conversationId 会话标识
 * @param memberId 买家会员 ID，未登录为空
 * @param memberName 买家显示名，未登录或会员记录已删时为空
 * @param memberAvatarUrl 买家头像的可访问地址，会员未设置头像时为空
 * @param title 会话标题，取首条提问
 * @param lastMessage 买家最近一条消息，客服接入前可用于判断来意；没有买家消息时为空
 * @param serviceMode 接待模式
 * @param agentId 接待客服管理员 ID
 * @param messageCount 消息条数
 * @param lastMessageAt 最近消息时间
 * @param agentRequestedAt 买家请求转人工的时间
 * @param waitingSeconds 已等待秒数，非排队状态为 null
 * @author Henfon
 * @date 2026-09-21
 */
public record AiAgentSessionView(String conversationId,
                                 Long memberId,
                                 String memberName,
                                 String memberAvatarUrl,
                                 String title,
                                 String lastMessage,
                                 String serviceMode,
                                 Long agentId,
                                 Integer messageCount,
                                 LocalDateTime lastMessageAt,
                                 LocalDateTime agentRequestedAt,
                                 Long waitingSeconds) {

    /**
     * 由会话实体构建，买家身份与最近消息留空。
     *
     * 买家侧接口用这个重载：买家不需要在响应里看到自己的名字与头像，省一次会员查询。
     *
     * @param conversation 会话
     * @return 视图
     * @author Henfon
     * @date 2026-09-21
     */
    public static AiAgentSessionView from(AiConversation conversation) {
        return from(conversation, null, null, null);
    }

    /**
     * 由会话实体构建，附带买家身份与最近消息。
     *
     * @param conversation 会话
     * @param memberName 买家显示名，未登录或会员记录已删时为空
     * @param memberAvatarUrl 买家头像的可访问地址，会员未设置头像时为空
     * @param lastMessage 买家最近一条消息
     * @return 视图
     * @author Henfon
     * @date 2026-09-22
     */
    public static AiAgentSessionView from(AiConversation conversation,
                                          String memberName,
                                          String memberAvatarUrl,
                                          String lastMessage) {
        return new AiAgentSessionView(conversation.getConversationId(),
                conversation.getMemberId(),
                memberName,
                memberAvatarUrl,
                conversation.getTitle(),
                lastMessage,
                conversation.getServiceMode(),
                conversation.getAgentId(),
                conversation.getMessageCount(),
                conversation.getLastMessageAt(),
                conversation.getAgentRequestedAt(),
                waitingSeconds(conversation.getServiceMode(), conversation.getAgentRequestedAt()));
    }

    /**
     * 计算已等待秒数。
     *
     * @param serviceMode 接待模式
     * @param requestedAt 请求转人工的时间
     * @return 秒数，非排队状态或时间缺失时返回 null
     * @author Henfon
     * @date 2026-09-21
     */
    static Long waitingSeconds(String serviceMode, LocalDateTime requestedAt) {
        if (!AiConversationService.SERVICE_WAITING.equals(serviceMode) || requestedAt == null) {
            return null;
        }
        long seconds = Duration.between(requestedAt, LocalDateTime.now()).getSeconds();
        return Math.max(seconds, 0L);
    }
}
