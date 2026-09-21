package com.henfon.shop.ai.dto;

import com.henfon.shop.ai.entity.AiMessage;

import java.time.LocalDateTime;

/**
 * 门户客服窗口的一条聊天记录。
 *
 * 只保留角色、正文与时间三项。把 ai_message 整行返回给前端会顺带带出工具调用快照与
 * token 用量——那些是排查用的内部信息，客服窗口不需要，也不该出现在买家浏览器里。
 *
 * @param role 角色：USER 买家，ASSISTANT 客服
 * @param content 消息正文
 * @param createdAt 消息时间
 * @author Henfon
 * @date 2026-09-21
 */
public record AiChatMessage(String role, String content, LocalDateTime createdAt) {

    /**
     * 由存储记录构造。
     *
     * @param message 消息记录
     * @return 聊天记录
     * @author Henfon
     * @date 2026-09-21
     */
    public static AiChatMessage from(AiMessage message) {
        return new AiChatMessage(message.getRole(), message.getContent(), message.getCreatedAt());
    }
}
