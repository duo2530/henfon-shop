package com.henfon.shop.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 客服转人工工单实体。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Data
@TableName("ai_ticket")
public class AiTicket {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 工单编号，形如 AI20260921-0001，插入后由主键回填。 */
    private String ticketNo;
    /** 来源会话标识，用户直接提交留言时为空。 */
    private String conversationId;
    private Long memberId;
    /** 联系方式，手机号或邮箱。 */
    private String contact;
    /** 用户原始问题。 */
    private String question;
    /** AI 对问题的归类摘要，便于运营快速判断归属。 */
    private String aiSummary;
    /** 处理状态：PENDING 待处理，PROCESSING 处理中，CLOSED 已关闭。 */
    private String status;
    private Long handlerId;
    /** 处理人名称快照，避免账号改名后历史工单无法辨认。 */
    private String handlerName;
    /** 处理备注，仅运营可见。 */
    private String handleNote;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
