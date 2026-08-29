package com.henfon.shop.integration.messaging;

import java.time.Instant;

/**
 * 统一领域事件信封，后续用于 Outbox 和 RocketMQ 消息发布。
 *
 * @param eventId 事件唯一标识
 * @param eventType 事件类型
 * @param aggregateId 聚合根标识
 * @param occurredAt 事件发生时间
 * @param payload 事件载荷
 * @author Henfon
 * @date 2026-08-29
 */
public record DomainEvent(String eventId, String eventType, String aggregateId,
                          Instant occurredAt, Object payload) {
}
