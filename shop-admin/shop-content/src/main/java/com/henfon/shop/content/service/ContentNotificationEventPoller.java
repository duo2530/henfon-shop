package com.henfon.shop.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.henfon.shop.content.entity.TradeEventOutboxNotificationRecord;
import com.henfon.shop.content.mapper.TradeEventOutboxNotificationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 交易 Outbox 到会员站内通知的轻量消费者骨架。
 *
 * <p>消费者通过通知幂等键重复执行安全落库，后续可替换为 RocketMQ 正式消费者而不改变通知服务契约。</p>
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Service
public class ContentNotificationEventPoller {

    private static final Logger log = LoggerFactory.getLogger(ContentNotificationEventPoller.class);

    private final TradeEventOutboxNotificationMapper eventMapper;
    private final ContentNotificationService notificationService;
    private final ObjectMapper objectMapper;

    /**
     * 创建交易事件通知消费者。
     *
     * @param eventMapper Outbox 事件读取器
     * @param notificationService 通知应用服务
     * @param objectMapper JSON 解析器
     * @author Henfon
     * @date 2026-08-31
     */
    public ContentNotificationEventPoller(TradeEventOutboxNotificationMapper eventMapper,
                                           ContentNotificationService notificationService,
                                           ObjectMapper objectMapper) {
        this.eventMapper = eventMapper;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    /**
     * 定时扫描交易事件并落库会员通知。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Scheduled(fixedDelayString = "${shop.notification.poll-interval-ms:5000}")
    public void poll() {
        try {
            List<TradeEventOutboxNotificationRecord> events = eventMapper.findNotificationEvents(100);
            for (TradeEventOutboxNotificationRecord event : events) {
                consume(event);
            }
        } catch (Exception exception) {
            // 通知消费失败不阻断交易主链路，下一轮扫描会继续重试。
            log.warn("站内通知 Outbox 扫描失败", exception);
        }
    }

    /**
     * 转换单个交易事件为会员通知。
     *
     * @param event 交易 Outbox 事件
     * @author Henfon
     * @date 2026-08-31
     */
    private void consume(TradeEventOutboxNotificationRecord event) {
        try {
            JsonNode payload = objectMapper.readTree(event.payload());
            Long memberId = longValue(payload, "memberId");
            Long orderId = longValue(payload, "orderId");
            if (memberId == null) {
                return;
            }
            String orderNo = text(payload, "orderNo", "未知订单");
            String eventType = event.eventType() == null ? "UNKNOWN" : event.eventType().toUpperCase();
            String title;
            String content;
            switch (eventType) {
                case "PAYMENT_SUCCEEDED" -> {
                    title = "支付成功";
                    content = "订单 " + orderNo + " 已支付成功。";
                }
                case "ORDER_SHIPPED" -> {
                    title = "订单已发货";
                    content = "订单 " + orderNo + " 已发货，物流轨迹将持续更新。";
                }
                case "REFUND_SUCCEEDED" -> {
                    title = "退款成功";
                    content = "订单 " + orderNo + " 退款已完成。";
                }
                default -> {
                    title = "售后进度更新";
                    content = "订单 " + orderNo + " 的售后状态已更新，请进入订单查看详情。";
                }
            }
            notificationService.saveEvent(new com.henfon.shop.content.dto.NotificationEventRequest(
                    // 与 RocketMQ 消费者使用同一幂等键，兼容轮询兜底时不会重复生成通知。
                    memberId, orderId, orderNo, eventType, title, content, "ROCKETMQ:" + event.eventId()));
        } catch (Exception exception) {
            // 单条脏事件跳过并记录日志，避免阻塞同批次其他会员通知。
            log.warn("交易事件转换站内通知失败，eventId={}", event.eventId(), exception);
        }
    }

    /**
     * 读取 JSON 数字字段。
     *
     * @param payload JSON 载荷
     * @param field 字段名称
     * @return 数字值
     * @author Henfon
     * @date 2026-08-31
     */
    private Long longValue(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }

    /**
     * 读取 JSON 文本字段并提供默认值。
     *
     * @param payload JSON 载荷
     * @param field 字段名称
     * @param defaultValue 默认文本
     * @return 文本值
     * @author Henfon
     * @date 2026-08-31
     */
    private String text(JsonNode payload, String field, String defaultValue) {
        JsonNode value = payload.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? defaultValue : value.asText();
    }
}
