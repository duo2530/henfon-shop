package com.henfon.shop.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 客服消息明细实体，同时作为 ChatMemory 的持久化存储。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Data
@TableName("ai_message")
public class AiMessage {

    /** 买家消息。取值与 Spring AI 的 MessageType 名称一致，读写共用一套口径。 */
    public static final String ROLE_USER = "USER";

    /** 客服回复。 */
    public static final String ROLE_ASSISTANT = "ASSISTANT";

    /** 系统消息。 */
    public static final String ROLE_SYSTEM = "SYSTEM";

    /** 工具返回消息。 */
    public static final String ROLE_TOOL = "TOOL";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String conversationId;
    /** 会话内消息序号，从 1 递增，读回上下文按它排序。 */
    private Integer sequence;
    /** 消息角色：USER 用户，ASSISTANT 助手，TOOL 工具，SYSTEM 系统。 */
    private String role;
    private String content;
    private String toolName;
    /** 工具调用参数与返回结果的 JSON 快照，排查错误回答时使用。 */
    private String toolPayload;
    private String model;
    private Integer tokensIn;
    private Integer tokensOut;
    private Integer latencyMs;
    private LocalDateTime createdAt;
}
