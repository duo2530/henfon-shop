package com.henfon.shop.ai.dto;

import com.henfon.shop.ai.entity.AiMessage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

/**
 * 门户客服窗口的一条聊天记录。
 *
 * 只保留角色、正文与时间三项（外加发送方）。把 ai_message 整行返回给前端会顺带带出工具调用
 * 快照与 token 用量——那些是排查用的内部信息，客服窗口不需要，也不该出现在买家浏览器里。
 *
 * 发送方单独下发而不是由 role 推断：人工客服的回复以 ASSISTANT 角色落库，只给 role 的话
 * 买家就分不出"这句话是真人说的还是机器说的"——而这正是人工介入后买家最需要看清的一件事。
 *
 * 序号也一并下发：门户在人工服务结束后要把记录读回来（长连接那时已经断开），而读回来的
 * 记录要能和界面上已有的消息对齐去重，靠正文比对会在重复提问时误判。
 *
 * @param sequence 会话内序号，递增
 * @param role 角色：USER 买家，ASSISTANT 客服
 * @param sender 发送方：MEMBER 买家，AI 智能客服，AGENT 人工客服
 * @param content 消息正文
 * @param createdAt 消息时间
 * @param cards 随消息一起下发的商品卡片，没有时为空列表
 * @author Henfon
 * @date 2026-09-21
 */
public record AiChatMessage(Integer sequence, String role, String sender, String content,
                            LocalDateTime createdAt, List<AiProductCard> cards) {

    /**
     * 由存储记录构造。
     *
     * sender 为空的历史数据（本批之前没有人工客服）按角色兜底推断。
     *
     * @param message 消息记录
     * @return 聊天记录
     * @author Henfon
     * @date 2026-09-21
     */
    public static AiChatMessage from(AiMessage message) {
        return withCards(message, List.of());
    }

    /**
     * 由存储记录构造，并带上商品卡片。
     *
     * @param message 消息记录
     * @param cards 商品卡片
     * @return 聊天记录
     * @author Henfon
     * @date 2026-09-22
     */
    public static AiChatMessage withCards(AiMessage message, List<AiProductCard> cards) {
        String sender = message.getSender();
        if (sender == null || sender.isBlank()) {
            sender = AiMessage.ROLE_USER.equals(message.getRole())
                    ? AiMessage.SENDER_MEMBER : AiMessage.SENDER_AI;
        }
        return new AiChatMessage(message.getSequence(), message.getRole(), sender, message.getContent(),
                message.getCreatedAt(), cards == null ? List.of() : cards);
    }

    /**
     * 由存储记录构造，商品卡片从 tool_payload 里解析。
     *
     * 卡片与正文分开两列存：正文进 content 供模型与买家阅读，卡片进 tool_payload 供前端渲染。
     * 买家刷新页面时消息从库里读回，正文能直接取、卡片得解一遍 JSON，读路径不能漏——否则
     * 刷新之后那几条推荐就只剩一句"我给你找了几款"，卡片全没了。
     *
     * @param message 消息记录
     * @param cardResolver 按消息记录解析卡片的函数
     * @return 聊天记录
     * @author Henfon
     * @date 2026-09-22
     */
    public static AiChatMessage from(AiMessage message, Function<AiMessage, List<AiProductCard>> cardResolver) {
        List<AiProductCard> cards = cardResolver == null ? List.of() : cardResolver.apply(message);
        return withCards(message, cards);
    }
}
