package com.henfon.shop.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.henfon.shop.content.dto.NotificationEventRequest;
import com.henfon.shop.integration.messaging.DomainEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * RocketMQ 交易领域事件通知处理器。
 *
 * <p>处理器不维护本地消费状态，通知表的唯一幂等键负责抵御重复投递；处理异常向上抛出，
 * 由 RocketMQ 消费线程触发重试，超过重试上限后进入 RocketMQ 死信队列。</p>
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Service
public class ContentNotificationEventHandler {

    private final ContentNotificationService notificationService;
    private final ObjectMapper objectMapper;

    /**
     * 创建领域事件通知处理器。
     *
     * @param notificationService 通知落库服务
     * @param objectMapper JSON 解析器
     * @author Henfon
     * @date 2026-08-31
     */
    public ContentNotificationEventHandler(ContentNotificationService notificationService,
                                           ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    /**
     * 消费交易领域事件并创建会员站内通知。
     *
     * @param event RocketMQ 领域事件
     * @author Henfon
     * @date 2026-08-31
     */
    public void handle(DomainEvent event) {
        if (event == null || !StringUtils.hasText(event.eventId())) {
            throw new IllegalArgumentException("领域事件 eventId 不能为空");
        }
        JsonNode payload = parsePayload(event.payload());
        Long memberId = longValue(payload, "memberId");
        if (memberId == null) {
            // 缺少会员归属属于不可自动修复的脏消息，抛出异常交给重试/DLQ 记录。
            throw new IllegalArgumentException("领域事件缺少 memberId，eventId=" + event.eventId());
        }
        Long orderId = longValue(payload, "orderId");
        String eventType = normalizeEventType(event.eventType());
        String orderNo = text(payload, "orderNo", "未知订单");
        String businessId = text(payload, "afterSaleNo", orderNo);
        String title = title(eventType);
        String content = content(eventType, payload, orderNo, businessId);
        // 与 Outbox 轮询兜底消费者共用事件幂等键，避免两条链路同时消费产生重复通知。
        notificationService.saveEvent(new NotificationEventRequest(memberId, orderId, businessId,
                eventType, title, content, "ROCKETMQ:" + event.eventId()));
    }

    /**
     * 将 RocketMQ 载荷转换为 JSON 节点。
     *
     * @param payload 原始载荷
     * @return JSON 节点
     * @author Henfon
     * @date 2026-08-31
     */
    private JsonNode parsePayload(Object payload) {
        try {
            if (payload instanceof JsonNode jsonNode) {
                return jsonNode;
            }
            if (payload instanceof String text) {
                return objectMapper.readTree(text);
            }
            return objectMapper.valueToTree(payload);
        } catch (Exception exception) {
            throw new IllegalArgumentException("领域事件 payload 不是有效 JSON", exception);
        }
    }

    /**
     * 规范化事件类型。
     *
     * @param eventType 原始事件类型
     * @return 大写事件类型
     * @author Henfon
     * @date 2026-08-31
     */
    private String normalizeEventType(String eventType) {
        return StringUtils.hasText(eventType) ? eventType.trim().toUpperCase() : "UNKNOWN";
    }

    /**
     * 获取通知标题。
     *
     * @param eventType 事件类型
     * @return 通知标题
     * @author Henfon
     * @date 2026-08-31
     */
    private String title(String eventType) {
        return switch (eventType) {
            case "PAYMENT_SUCCEEDED" -> "支付成功";
            case "ORDER_SHIPPED" -> "订单已发货";
            case "REFUND_SUCCEEDED" -> "退款成功";
            case "AFTER_SALE_CREATED" -> "售后申请已提交";
            case "AFTER_SALE_APPROVED" -> "售后审核通过";
            case "AFTER_SALE_REJECTED" -> "售后申请被驳回";
            case "AFTER_SALE_CANCELLED" -> "售后申请已取消";
            case "AFTER_SALE_COMPLETED" -> "售后处理完成";
            default -> "订单进度更新";
        };
    }

    /**
     * 构造通知正文。
     *
     * @param eventType 事件类型
     * @param payload 事件载荷
     * @param orderNo 订单号
     * @param businessId 业务单号
     * @return 通知正文
     * @author Henfon
     * @date 2026-08-31
     */
    private String content(String eventType, JsonNode payload, String orderNo, String businessId) {
        return switch (eventType) {
            case "PAYMENT_SUCCEEDED" -> "订单 " + orderNo + " 已支付成功。";
            case "ORDER_SHIPPED" -> "订单 " + orderNo + " 已发货，物流轨迹将持续更新。";
            case "REFUND_SUCCEEDED" -> "订单 " + orderNo + " 退款已完成。";
            case "AFTER_SALE_CREATED" -> "售后单 " + businessId + " 已提交，等待商家审核。";
            case "AFTER_SALE_APPROVED" -> "售后单 " + businessId + " 已审核通过，平台将继续处理。";
            case "AFTER_SALE_REJECTED" -> "售后单 " + businessId + " 未通过审核，请查看售后备注。";
            case "AFTER_SALE_CANCELLED" -> "售后单 " + businessId + " 已取消。";
            case "AFTER_SALE_COMPLETED" -> "售后单 " + businessId + " 已处理完成。";
            default -> "订单 " + orderNo + " 的状态已更新。";
        };
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
        JsonNode value = payload == null ? null : payload.get(field);
        return value == null || value.isNull() || !value.canConvertToLong() ? null : value.asLong();
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
        JsonNode value = payload == null ? null : payload.get(field);
        return value == null || value.isNull() || !StringUtils.hasText(value.asText())
                ? defaultValue : value.asText().trim();
    }
}
