package com.henfon.shop.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 客服会话实体。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Data
@TableName("ai_conversation")
public class AiConversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 会话标识，对外暴露，由服务端生成。 */
    private String conversationId;
    /** 入口渠道：PORTAL 门户买家，ADMIN 管理端助手。 */
    private String channel;
    /** 所属会员 ID，未登录会话为空。 */
    private Long memberId;
    /** 所属管理员 ID，管理端会话使用。 */
    private Long adminId;
    private String subjectType;
    private String subjectId;
    /** 会话标题，取首条用户提问的前若干字。 */
    private String title;
    private Integer messageCount;
    private LocalDateTime lastMessageAt;
    /** 会话状态：ACTIVE 进行中，TICKETED 已转人工，CLOSED 已结束。 */
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
