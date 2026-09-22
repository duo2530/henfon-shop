package com.henfon.shop.ai.dto;

import com.henfon.shop.ai.entity.AiTicket;

import java.time.LocalDateTime;

/**
 * 买家看到的工单。
 *
 * 不复用 AiTicket 实体直接下发：实体里带着 handle_note（内部处理备注）与 handler_id 这类
 * 只该在后台流通的字段，靠"序列化时忽略"来遮挡，只要有人日后加一个字段就又漏了；单独一个
 * 记录只用正面列举的方式表达"买家能看什么"，新增列不会自动外泄。
 *
 * @param id 工单 ID
 * @param ticketNo 工单编号
 * @param question 提交的问题原文
 * @param status 处理状态
 * @param reply 客服回复内容，未回复为空
 * @param repliedAt 回复时间，未回复为空
 * @param createdAt 提交时间
 * @param updatedAt 最近更新时间
 * @author Henfon
 * @date 2026-09-21
 */
public record AiTicketPortalView(Long id,
                                 String ticketNo,
                                 String question,
                                 String status,
                                 String reply,
                                 LocalDateTime repliedAt,
                                 LocalDateTime createdAt,
                                 LocalDateTime updatedAt) {

    /**
     * 由工单实体构建。
     *
     * @param ticket 工单
     * @return 买家视图
     * @author Henfon
     * @date 2026-09-21
     */
    public static AiTicketPortalView from(AiTicket ticket) {
        return new AiTicketPortalView(ticket.getId(),
                ticket.getTicketNo(),
                ticket.getQuestion(),
                ticket.getStatus(),
                ticket.getReplyContent(),
                ticket.getRepliedAt(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt());
    }
}
