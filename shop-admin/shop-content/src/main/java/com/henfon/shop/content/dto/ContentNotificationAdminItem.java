package com.henfon.shop.content.dto;

import java.time.LocalDateTime;

/**
 * 后台通知中心列表项。
 *
 * <p>通知本体是会员维度的投递记录，后台列表额外带出收件会员信息与运营已读状态，
 * 便于运营核对通知是否正常触达。</p>
 *
 * @param id 通知ID
 * @param memberId 收件会员ID
 * @param memberName 收件会员显示名
 * @param memberAccount 收件会员账号
 * @param orderId 关联订单ID
 * @param businessId 关联业务单号
 * @param eventType 事件类型
 * @param title 通知标题
 * @param content 通知内容
 * @param readStatus 会员已读状态：0未读，1已读
 * @param readAt 会员阅读时间
 * @param adminReadStatus 运营已读状态：0未读，1已读
 * @param adminReadAt 运营阅读时间
 * @param createdAt 通知产生时间
 * @author Henfon
 * @date 2026-09-18
 */
public record ContentNotificationAdminItem(
        Long id,
        Long memberId,
        String memberName,
        String memberAccount,
        Long orderId,
        String businessId,
        String eventType,
        String title,
        String content,
        Integer readStatus,
        LocalDateTime readAt,
        Integer adminReadStatus,
        LocalDateTime adminReadAt,
        LocalDateTime createdAt) {
}
