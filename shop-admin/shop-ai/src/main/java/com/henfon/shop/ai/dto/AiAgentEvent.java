package com.henfon.shop.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.henfon.shop.ai.entity.AiConversation;

import java.util.List;

/**
 * 人工会话的长连接事件。
 *
 * 与 AI 对话的流式事件分开定义：那边推的是模型逐字生成的增量文本，这边推的是完整的消息
 * 与接待状态变化，字段含义与生命周期都不同，混在一个类型里会让前端出现大量用不上的判断。
 *
 * 事件顺序：订阅成功先来一条 snapshot（含最近若干条消息与当前接待模式），之后是若干
 * message、state 与 ended，idle 与 ping 穿插其中，连接结束即流结束。前端只需按 type 分支。
 *
 * @param type 事件类型：snapshot 订阅快照，message 新消息，state 接待状态变化，ended 人工接待结束，idle 空闲催办，ping 心跳
 * @param conversationId 会话标识
 * @param serviceMode 当前接待模式，snapshot、state、ended 与 idle 携带
 * @param agentId 接待客服管理员 ID，snapshot、state、ended 与 idle 携带
 * @param messages 最近消息，仅 snapshot 携带
 * @param message 新消息，仅 message 携带
 * @param idleMinutes 已空闲的分钟数，仅 idle 携带
 * @param productDraft 商品卡片草稿，仅工作台推送商品时携带，服务端据此组装卡片再变成 message
 * @author Henfon
 * @date 2026-09-21
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiAgentEvent(String type,
                           String conversationId,
                           String serviceMode,
                           Long agentId,
                           List<AiAgentMessage> messages,
                           AiAgentMessage message,
                           Long idleMinutes,
                           AiAgentProductDraft productDraft) {

    /**
     * 订阅快照。
     *
     * @param conversation 会话
     * @param messages 最近消息
     * @return 事件
     * @author Henfon
     * @date 2026-09-21
     */
    public static AiAgentEvent snapshot(AiConversation conversation, List<AiAgentMessage> messages) {
        return new AiAgentEvent("snapshot", conversation.getConversationId(), conversation.getServiceMode(),
                conversation.getAgentId(), messages, null, null, null);
    }

    /**
     * 新消息。
     *
     * @param conversation 会话
     * @param message 消息
     * @return 事件
     * @author Henfon
     * @date 2026-09-21
     */
    public static AiAgentEvent message(AiConversation conversation, AiAgentMessage message) {
        return new AiAgentEvent("message", conversation.getConversationId(), null, null, null, message, null, null);
    }

    /**
     * 工作台推送商品卡片。
     *
     * 卡片由服务端按商品主键查库组装，而不是让客服手填标题和图片地址——价格与图片都要经过
     * 对象存储换发才能显示，手填的结果多半是过期链接。
     *
     * @param conversation 会话
     * @param message 已落库的卡片消息
     * @param draft 客服提交的商品卡片草稿
     * @return 事件
     * @author Henfon
     * @date 2026-09-22
     */
    public static AiAgentEvent product(AiConversation conversation, AiAgentMessage message, AiAgentProductDraft draft) {
        return new AiAgentEvent("product", conversation.getConversationId(), null, null, null, message, null, draft);
    }

    /**
     * 接待状态变化，客服接入或结束会话时告知另一方。
     *
     * @param conversation 会话
     * @return 事件
     * @author Henfon
     * @date 2026-09-21
     */
    public static AiAgentEvent state(AiConversation conversation) {
        return new AiAgentEvent("state", conversation.getConversationId(), conversation.getServiceMode(),
                conversation.getAgentId(), null, null, null, null);
    }

    /**
     * 人工接待结束。
     *
     * 与 state 分开：state 是"谁在接待"的变化，客服接入时会推；ended 只表示这次人工服务
     * 结束，买家侧凭它弹评价。混用会让前端只能靠比对 serviceMode 才能区分，判断散在各处。
     *
     * @param conversation 会话
     * @return 事件
     * @author Henfon
     * @date 2026-09-21
     */
    public static AiAgentEvent ended(AiConversation conversation) {
        return new AiAgentEvent("ended", conversation.getConversationId(), conversation.getServiceMode(),
                conversation.getAgentId(), null, null, null, null);
    }

    /**
     * 空闲催办。
     *
     * 人工会话长时间没有新消息时推给客服，提示这单还挂着。刻意不动服务模式：催办只负责
     * 提醒，回收由 {@link #ended(AiConversation)} 在更长的阈值上做——客服正看着买家打字时
     * 会话被系统抢走，比"彻底没人管"更让人恼火。
     *
     * 与 error 分开：前端对 error 的既定处理是显示"连接异常"，而这条是正常的业务提醒。
     *
     * @param conversation 会话
     * @param idleMinutes 已空闲的分钟数
     * @return 事件
     * @author Henfon
     * @date 2026-09-22
     */
    public static AiAgentEvent idle(AiConversation conversation, long idleMinutes) {
        return new AiAgentEvent("idle", conversation.getConversationId(), conversation.getServiceMode(),
                conversation.getAgentId(), null, null, idleMinutes, null);
    }

    /**
     * 心跳。
     *
     * @param conversationId 会话标识
     * @return 事件
     * @author Henfon
     * @date 2026-09-21
     */
    public static AiAgentEvent ping(String conversationId) {
        return new AiAgentEvent("ping", conversationId, null, null, null, null, null, null);
    }
}
