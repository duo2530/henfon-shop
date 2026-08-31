package com.henfon.shop.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.henfon.shop.integration.messaging.DomainEvent;
import com.henfon.shop.integration.messaging.RocketMqTopics;
import com.henfon.shop.payment.dto.PaymentRefundCreateRequest;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 售后审核通过退款单创建消费者。
 *
 * <p>使用独立消费组，不影响会员通知消费者；退款单创建以售后单号作为幂等键，
 * 事件重复投递时由 {@link PaymentRefundService} 复用原退款单。</p>
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Service
@RocketMQMessageListener(topic = RocketMqTopics.AFTER_SALE_APPROVED,
        consumerGroup = "${shop.rocketmq.consumer.after-sale-refund-group:shop-payment-after-sale-refund}")
public class PaymentAfterSaleApprovedRefundListener implements RocketMQListener<DomainEvent> {

    private static final Logger log = LoggerFactory.getLogger(PaymentAfterSaleApprovedRefundListener.class);

    private final ObjectMapper objectMapper;
    private final PaymentRefundService refundService;

    /**
     * 创建售后退款单消费者。
     *
     * @param objectMapper JSON 解析器
     * @param refundService 退款单应用服务
     * @author Henfon
     * @date 2026-08-31
     */
    public PaymentAfterSaleApprovedRefundListener(ObjectMapper objectMapper, PaymentRefundService refundService) {
        this.objectMapper = objectMapper;
        this.refundService = refundService;
    }

    /**
     * 消费售后审核通过事件并创建退款单。
     *
     * @param event 售后领域事件
     * @author Henfon
     * @date 2026-08-31
     */
    @Override
    public void onMessage(DomainEvent event) {
        if (event == null || event.payload() == null) {
            throw new IllegalArgumentException("售后审核事件不能为空");
        }
        try {
            JsonNode payload = toJson(event.payload());
            // 退货退款/换货审核暂不创建原路退款单，等待退货入库流程完成后再发起。
            if (payload.path("afterSaleType").asInt(0) != 1) {
                return;
            }
            String afterSaleNo = requiredText(payload, "afterSaleNo");
            Long orderId = requiredLong(payload, "orderId");
            BigDecimal refundAmount = requiredAmount(payload, "refundAmount");
            refundService.create(new PaymentRefundCreateRequest(orderId, refundAmount,
                    "售后审核通过", "AFTER_SALE:" + afterSaleNo));
        } catch (RuntimeException exception) {
            // 异常继续抛出，交由 RocketMQ 重试和死信队列处理，避免审核事件静默丢失。
            log.warn("售后审核退款单创建失败，eventId={}", event.eventId(), exception);
            throw exception;
        }
    }

    /**
     * 将事件载荷规范化为 JSON 节点。
     *
     * @param payload 事件载荷
     * @return JSON 节点
     * @author Henfon
     * @date 2026-08-31
     */
    private JsonNode toJson(Object payload) {
        try {
            return payload instanceof String text ? objectMapper.readTree(text) : objectMapper.valueToTree(payload);
        } catch (Exception exception) {
            throw new IllegalArgumentException("售后审核事件载荷格式错误", exception);
        }
    }

    /**
     * 读取必填文本字段。
     *
     * @param payload JSON 载荷
     * @param field 字段名
     * @return 字段文本
     * @author Henfon
     * @date 2026-08-31
     */
    private String requiredText(JsonNode payload, String field) {
        String value = payload.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("售后审核事件缺少" + field);
        }
        return value;
    }

    /**
     * 读取必填订单ID。
     *
     * @param payload JSON 载荷
     * @param field 字段名
     * @return 订单ID
     * @author Henfon
     * @date 2026-08-31
     */
    private Long requiredLong(JsonNode payload, String field) {
        if (!payload.hasNonNull(field) || !payload.path(field).canConvertToLong()) {
            throw new IllegalArgumentException("售后审核事件缺少" + field);
        }
        return payload.path(field).longValue();
    }

    /**
     * 读取并校验退款金额。
     *
     * @param payload JSON 载荷
     * @param field 字段名
     * @return 退款金额
     * @author Henfon
     * @date 2026-08-31
     */
    private BigDecimal requiredAmount(JsonNode payload, String field) {
        String value = payload.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("售后审核事件缺少" + field);
        }
        try {
            BigDecimal amount = new BigDecimal(value);
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("售后审核事件退款金额必须大于0");
            }
            return amount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("售后审核事件退款金额格式错误", exception);
        }
    }
}
