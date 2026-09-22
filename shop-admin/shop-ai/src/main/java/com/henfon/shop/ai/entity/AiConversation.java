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
    /** 会话状态：ACTIVE 进行中，TICKETED 已提交工单，CLOSED 已结束。 */
    private String status;
    /**
     * 接待模式：AI 智能客服，WAITING 等待人工接入，HUMAN 人工接待中。
     *
     * 与 status 分工不重叠：status 管会话的生命周期，本字段管当下由谁接待。人工会话结束后
     * 退回 AI，买家可以继续问智能客服，不必新开会话。
     */
    private String serviceMode;
    /** 接管会话的管理员 ID，未接管为空。会话结束后保留，用于回溯由谁服务过。 */
    private Long agentId;
    /** 买家请求转人工的时间，等待队列按它计算已等待时长。 */
    private LocalDateTime agentRequestedAt;
    /** 客服接入时间。 */
    private LocalDateTime agentJoinedAt;
    /** 人工会话结束时间。 */
    private LocalDateTime agentEndedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
