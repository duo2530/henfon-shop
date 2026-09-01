package com.henfon.shop.marketing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.henfon.shop.integration.messaging.DomainEvent;
import com.henfon.shop.integration.messaging.RocketMqTopics;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 全额退款成功事件消费者，负责回滚订单已核销优惠券。
 *
 * <p>仅消费订单状态机发布的全额退款成功事件，部分退款不会提前返还优惠券；
 * 回滚服务自身通过核销流水和状态校验保证重复消息幂等。</p>
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Service
@RocketMQMessageListener(topic = RocketMqTopics.REFUND_SUCCEEDED,
        consumerGroup = "${shop.rocketmq.consumer.refund-coupon-group:shop-marketing-refund-coupon}")
public class CouponRefundSucceededListener implements RocketMQListener<DomainEvent> {

    private static final Logger log = LoggerFactory.getLogger(CouponRefundSucceededListener.class);

    private final ObjectMapper objectMapper;
    private final MarketingPortalService marketingPortalService;

    /**
     * 创建优惠券退款联动消费者。
     *
     * @param objectMapper JSON 解析器
     * @param marketingPortalService 门户营销服务
     * @author Henfon
     * @date 2026-09-01
     */
    public CouponRefundSucceededListener(ObjectMapper objectMapper,
                                         MarketingPortalService marketingPortalService) {
        this.objectMapper = objectMapper;
        this.marketingPortalService = marketingPortalService;
    }

    /**
     * 消费全额退款事件并幂等回滚优惠券。
     *
     * @param event 退款领域事件
     * @author Henfon
     * @date 2026-09-01
     */
    @Override
    public void onMessage(DomainEvent event) {
        if (event == null || event.payload() == null) {
            throw new IllegalArgumentException("退款成功事件不能为空");
        }
        try {
            JsonNode payload = parsePayload(event.payload());
            Long memberId = requiredLong(payload, "memberId");
            Long orderId = requiredLong(payload, "orderId");
            // rollback 没有优惠券使用记录时返回空，有记录时通过回滚流水抵御重复投递。
            marketingPortalService.rollback(memberId, orderId);
        } catch (RuntimeException exception) {
            // 保留事件编号并继续抛出，让 RocketMQ 按策略重试并进入死信队列。
            log.warn("退款成功优惠券回滚失败，eventId={}", event.eventId(), exception);
            throw exception;
        }
    }

    /**
     * 将事件载荷转换为 JSON 节点。
     *
     * @param payload 原始事件载荷
     * @return JSON 节点
     * @author Henfon
     * @date 2026-09-01
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
            throw new IllegalArgumentException("退款成功事件载荷格式错误", exception);
        }
    }

    /**
     * 读取必填长整数字段。
     *
     * @param payload 事件载荷
     * @param field 字段名
     * @return 字段值
     * @author Henfon
     * @date 2026-09-01
     */
    private Long requiredLong(JsonNode payload, String field) {
        if (payload == null || !payload.hasNonNull(field) || !payload.path(field).canConvertToLong()) {
            throw new IllegalArgumentException("退款成功事件缺少" + field);
        }
        return payload.path(field).longValue();
    }
}
