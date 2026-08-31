package com.henfon.shop.content.entity;

/**
 * 通知消费者读取的交易 Outbox 事件投影。
 *
 * @param id Outbox 主键
 * @param eventId 事件唯一标识
 * @param eventType 事件类型
 * @param payload 事件载荷
 * @author Henfon
 * @date 2026-08-31
 */
public record TradeEventOutboxNotificationRecord(Long id, String eventId, String eventType, String payload) {
}
