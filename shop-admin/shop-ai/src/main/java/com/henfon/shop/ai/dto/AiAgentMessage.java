package com.henfon.shop.ai.dto;

import com.henfon.shop.ai.entity.AiMessage;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

/**
 * 人工会话里的一条消息。
 *
 * from 表示发送方，取值 MEMBER 买家 / AI 智能客服 / AGENT 人工客服。三者用同一个结构，
 * 前端按它决定气泡样式与是否显示"客服"标识——买家需要一眼看出这句话是真人说的还是机器说的。
 *
 * @param sequence 会话内序号，同时作为前端列表的稳定标识
 * @param from 发送方
 * @param content 正文
 * @param createdAt 发送时间
 * @param cards 随消息一起下发的商品卡片，没有时为空列表
 * @author Henfon
 * @date 2026-09-21
 */
public record AiAgentMessage(Integer sequence,
                             String from,
                             String content,
                             LocalDateTime createdAt,
                             List<AiProductCard> cards) {

    /**
     * 由存储记录构建。
     *
     * sender 为空的历史数据按角色兜底推断：本批之前没有人工客服，ASSISTANT 一律是模型输出。
     *
     * @param record 消息记录
     * @return 消息视图
     * @author Henfon
     * @date 2026-09-21
     */
    public static AiAgentMessage from(AiMessage record) {
        return new AiAgentMessage(record.getSequence(), resolveSender(record), record.getContent(),
                record.getCreatedAt(), List.of());
    }

    /**
     * 由存储记录构建，并带上商品卡片。
     *
     * @param record 消息记录
     * @param cards 商品卡片
     * @return 消息视图
     * @author Henfon
     * @date 2026-09-22
     */
    public static AiAgentMessage withCards(AiMessage record, List<AiProductCard> cards) {
        return new AiAgentMessage(record.getSequence(), resolveSender(record), record.getContent(),
                record.getCreatedAt(), cards == null ? List.of() : cards);
    }

    /**
     * 由存储记录构建，商品卡片从 tool_payload 里解析。
     *
     * 卡片与正文分开两列存：正文进 content 供模型与人工阅读，卡片进 tool_payload 供前端渲染。
     * 刷新页面后消息从库里读回，正文能直接取、卡片得解一遍 JSON，读路径不能漏。
     *
     * @param record 消息记录
     * @param cardResolver 按消息记录解析卡片的函数
     * @return 消息视图
     * @author Henfon
     * @date 2026-09-22
     */
    public static AiAgentMessage from(AiMessage record, Function<AiMessage, List<AiProductCard>> cardResolver) {
        List<AiProductCard> cards = cardResolver == null ? List.of() : cardResolver.apply(record);
        return withCards(record, cards);
    }

    /**
     * 推断发送方。
     *
     * @param record 消息记录
     * @return 发送方
     * @author Henfon
     * @date 2026-09-21
     */
    private static String resolveSender(AiMessage record) {
        if (StringUtils.hasText(record.getSender())) {
            return record.getSender();
        }
        return AiMessage.ROLE_USER.equals(record.getRole()) ? AiMessage.SENDER_MEMBER : AiMessage.SENDER_AI;
    }

    /**
     * 判断是否对买家可见。
     *
     * 工具调用与系统消息不给买家看：前者正文为空，后者是内部状态变更记录，渲染出来就是
     * 一串空气泡。
     *
     * @param record 消息记录
     * @return 可见返回 true
     * @author Henfon
     * @date 2026-09-21
     */
    public static boolean visible(AiMessage record) {
        return AiMessage.ROLE_USER.equals(record.getRole()) || AiMessage.ROLE_ASSISTANT.equals(record.getRole());
    }
}
