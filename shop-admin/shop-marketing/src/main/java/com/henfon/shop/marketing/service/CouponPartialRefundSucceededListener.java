package com.henfon.shop.marketing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.henfon.shop.integration.messaging.DomainEvent;
import com.henfon.shop.integration.messaging.RocketMqTopics;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 部分退款成功事件消费者，负责驱动优惠券金额分摊。
 *
 * @author Henfon
 * @date 2026-09-04
 */
@Service
@RocketMQMessageListener(topic = RocketMqTopics.PARTIAL_REFUND_SUCCEEDED,
        consumerGroup = "${shop.rocketmq.consumer.partial-refund-coupon-group:shop-marketing-partial-refund-coupon}")
public class CouponPartialRefundSucceededListener implements RocketMQListener<DomainEvent> {

    private final ObjectMapper objectMapper;
    private final MarketingPortalService marketingPortalService;

    /**
     * 创建部分退款优惠券分摊消费者。
     *
     * @param objectMapper JSON 解析器
     * @param marketingPortalService 门户营销服务
     * @author Henfon
     * @date 2026-09-04
     */
    public CouponPartialRefundSucceededListener(ObjectMapper objectMapper,
                                                MarketingPortalService marketingPortalService) {
        this.objectMapper = objectMapper;
        this.marketingPortalService = marketingPortalService;
    }

    /**
     * 消费部分退款事件并记录累计优惠券分摊。
     *
     * @param event 部分退款事件
     * @author Henfon
     * @date 2026-09-04
     */
    @Override
    public void onMessage(DomainEvent event) {
        if (event == null || event.payload() == null) {
            throw new IllegalArgumentException("部分退款成功事件不能为空");
        }
        try {
            JsonNode payload = event.payload() instanceof String text
                    ? objectMapper.readTree(text) : objectMapper.valueToTree(event.payload());
            Long memberId = requiredLong(payload, "memberId");
            Long orderId = requiredLong(payload, "orderId");
            BigDecimal refundAmount = requiredDecimal(payload, "refundAmount");
            BigDecimal paidAmount = requiredDecimal(payload, "paidAmount");
            // 服务内部按累计金额幂等更新分摊流水，重复消息无需额外去重表。
            marketingPortalService.allocatePartialRefund(memberId, orderId, refundAmount, paidAmount);
        } catch (Exception exception) {
            throw new IllegalArgumentException("部分退款成功事件载荷格式错误", exception);
        }
    }

    /**
     * 读取必填长整数字段。
     *
     * @param payload 事件载荷
     * @param field 字段名称
     * @return 字段值
     * @author Henfon
     * @date 2026-09-04
     */
    private Long requiredLong(JsonNode payload, String field) {
        if (payload == null || !payload.hasNonNull(field) || !payload.path(field).canConvertToLong()) {
            throw new IllegalArgumentException("部分退款事件缺少" + field);
        }
        return payload.path(field).longValue();
    }

    /**
     * 读取必填金额字段。
     *
     * @param payload 事件载荷
     * @param field 字段名称
     * @return 金额
     * @author Henfon
     * @date 2026-09-04
     */
    private BigDecimal requiredDecimal(JsonNode payload, String field) {
        if (payload == null || !payload.hasNonNull(field)) {
            throw new IllegalArgumentException("部分退款事件缺少" + field);
        }
        try {
            return new BigDecimal(payload.path(field).asText());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("部分退款事件金额格式错误：" + field, exception);
        }
    }
}
