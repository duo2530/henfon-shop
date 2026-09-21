package com.henfon.shop.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 客服对话的流式事件。
 *
 * 一个 SSE 连接上依次出现 meta、若干 delta、最后是 done 或 error。做成带 type 的单一结构
 * 而不是四种命名事件：前端只要一个解析分支，事件顺序与字段含义都写在一个类型里，改协议
 * 时不会漏改某一端。
 *
 * 序列化时跳过 null 字段。delta 事件一轮要发几十上百次，把用不上的字段也带上会让流式响应
 * 的体积翻几倍，而这些字段本来就只在特定事件里有意义。
 *
 * @param type 事件类型：meta 会话信息，delta 增量文本，done 正常结束，error 异常结束
 * @param conversationId 会话标识，首轮由服务端生成，meta 事件携带
 * @param content 增量文本，仅 delta 事件携带
 * @param refs 引用到的知识来源，仅 meta 事件携带
 * @param hit 本轮是否命中了知识库，false 表示回答没有资料依据、可引导转人工
 * @param finishReason 结束原因，仅 done 事件携带，length 表示被输出长度上限截断
 * @param code 异常分类，仅 error 事件携带，前端据此区分配额拒绝与模型故障
 * @param message 异常说明，仅 error 事件携带
 * @author Henfon
 * @date 2026-09-21
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiChatEvent(String type,
                          String conversationId,
                          String content,
                          List<Ref> refs,
                          Boolean hit,
                          String finishReason,
                          String code,
                          String message) {

    /**
     * 会话信息事件。
     *
     * @param conversationId 会话标识
     * @param refs 引用来源
     * @param hit 是否命中知识库
     * @return 事件
     * @author Henfon
     * @date 2026-09-21
     */
    public static AiChatEvent meta(String conversationId, List<Ref> refs, boolean hit) {
        return new AiChatEvent("meta", conversationId, null, refs, hit, null, null, null);
    }

    /**
     * 增量文本事件。
     *
     * @param content 增量文本
     * @return 事件
     * @author Henfon
     * @date 2026-09-21
     */
    public static AiChatEvent delta(String content) {
        return new AiChatEvent("delta", null, content, null, null, null, null, null);
    }

    /**
     * 正常结束事件。
     *
     * @param conversationId 会话标识
     * @param finishReason 结束原因
     * @return 事件
     * @author Henfon
     * @date 2026-09-21
     */
    public static AiChatEvent done(String conversationId, String finishReason) {
        return new AiChatEvent("done", conversationId, null, null, null, finishReason, null, null);
    }

    /**
     * 异常结束事件。
     *
     * @param code 异常分类
     * @param message 异常说明
     * @return 事件
     * @author Henfon
     * @date 2026-09-21
     */
    public static AiChatEvent error(String code, String message) {
        return new AiChatEvent("error", null, null, null, null, null, code, message);
    }
}
