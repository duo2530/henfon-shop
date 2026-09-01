package com.henfon.shop.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.henfon.shop.integration.messaging.DomainEvent;
import com.henfon.shop.integration.messaging.RocketMqTopics;
import com.henfon.shop.payment.dto.PaymentRefundCreateRequest;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 退货入库事件退款单消费者。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Service
@RocketMQMessageListener(topic = RocketMqTopics.AFTER_SALE_RETURN_RECEIVED,
        consumerGroup = "${shop.rocketmq.consumer.after-sale-return-refund-group:shop-payment-after-sale-return-refund}")
public class PaymentAfterSaleReturnReceivedRefundListener implements RocketMQListener<DomainEvent> {

    private final ObjectMapper objectMapper;
    private final PaymentRefundService refundService;

    /**
     * 创建退货入库退款消费者。
     *
     * @param objectMapper JSON 解析器
     * @param refundService 退款服务
     * @author Henfon
     * @date 2026-09-01
     */
    public PaymentAfterSaleReturnReceivedRefundListener(ObjectMapper objectMapper, PaymentRefundService refundService) {
        this.objectMapper = objectMapper;
        this.refundService = refundService;
    }

    /**
     * 消费退货入库事件并幂等创建退款单。
     *
     * @param event 售后领域事件
     * @author Henfon
     * @date 2026-09-01
     */
    @Override
    public void onMessage(DomainEvent event) {
        if (event == null || event.payload() == null) {
            throw new IllegalArgumentException("退货入库事件不能为空");
        }
        JsonNode payload = event.payload() instanceof String text ? read(text) : objectMapper.valueToTree(event.payload());
        String afterSaleNo = requiredText(payload, "afterSaleNo");
        Long orderId = requiredLong(payload, "orderId");
        BigDecimal refundAmount = requiredAmount(payload, "refundAmount");
        refundService.create(new PaymentRefundCreateRequest(orderId, refundAmount,
                "退货入库确认", "AFTER_SALE_RETURN:" + afterSaleNo));
    }

    /**
     * 解析 JSON 文本。
     *
     * @param text JSON 文本
     * @return JSON 节点
     * @author Henfon
     * @date 2026-09-01
     */
    private JsonNode read(String text) {
        try {
            return objectMapper.readTree(text);
        } catch (Exception exception) {
            throw new IllegalArgumentException("退货入库事件载荷格式错误", exception);
        }
    }

    /**
     * 读取必填文本字段。
     *
     * @param payload 事件载荷
     * @param field 字段名
     * @return 字段值
     * @author Henfon
     * @date 2026-09-01
     */
    private String requiredText(JsonNode payload, String field) {
        String value = payload.path(field).asText(null);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("退货入库事件缺少" + field);
        return value;
    }

    /**
     * 读取订单 ID。
     *
     * @param payload 事件载荷
     * @param field 字段名
     * @return 订单 ID
     * @author Henfon
     * @date 2026-09-01
     */
    private Long requiredLong(JsonNode payload, String field) {
        if (!payload.hasNonNull(field) || !payload.path(field).canConvertToLong()) {
            throw new IllegalArgumentException("退货入库事件缺少" + field);
        }
        return payload.path(field).longValue();
    }

    /**
     * 读取退款金额。
     *
     * @param payload 事件载荷
     * @param field 字段名
     * @return 退款金额
     * @author Henfon
     * @date 2026-09-01
     */
    private BigDecimal requiredAmount(JsonNode payload, String field) {
        String value = payload.path(field).asText(null);
        try {
            BigDecimal amount = new BigDecimal(value == null ? "0" : value);
            if (amount.signum() <= 0) throw new IllegalArgumentException("退货入库事件退款金额必须大于0");
            return amount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("退货入库事件退款金额格式错误", exception);
        }
    }
}
