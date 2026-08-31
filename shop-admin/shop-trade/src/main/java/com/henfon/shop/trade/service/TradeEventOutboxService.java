package com.henfon.shop.trade.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.henfon.shop.integration.messaging.RocketMqTopics;
import com.henfon.shop.trade.entity.TradeEventOutbox;
import com.henfon.shop.trade.entity.TradeAfterSale;
import com.henfon.shop.trade.entity.TradeOrder;
import com.henfon.shop.trade.mapper.TradeEventOutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 交易领域事件 Outbox 应用服务。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Service
public class TradeEventOutboxService {

    private static final int STATUS_PENDING = 0;

    private final TradeEventOutboxMapper outboxMapper;

    /**
     * 创建交易事件 Outbox 服务。
     *
     * @param outboxMapper Outbox 数据访问对象
     * @author Henfon
     * @date 2026-08-30
     */
    public TradeEventOutboxService(TradeEventOutboxMapper outboxMapper) {
        this.outboxMapper = outboxMapper;
    }

    /**
     * 在当前业务事务中记录订单状态事件。
     *
     * @param order 订单实体
     * @param eventType 事件类型
     * @param topic RocketMQ 主题
     * @author Henfon
     * @date 2026-08-30
     */
    @Transactional
    public void recordOrderEvent(TradeOrder order, String eventType, String topic) {
        // Outbox 与订单状态共用数据库事务，业务提交成功后事件一定可被扫描到。
        TradeEventOutbox event = new TradeEventOutbox();
        event.setEventId(IdWorker.getIdStr());
        event.setEventType(eventType);
        event.setAggregateType("TRADE_ORDER");
        event.setAggregateId(String.valueOf(order.getId()));
        event.setTopic(topic);
        event.setPayload(buildOrderPayload(order));
        event.setStatus(STATUS_PENDING);
        event.setRetryCount(0);
        event.setNextRetryAt(LocalDateTime.now());
        outboxMapper.insert(event);
    }

    /**
     * 在当前售后事务中记录售后领域事件。
     *
     * @param afterSale 售后单
     * @param eventType 事件类型
     * @param topic RocketMQ 主题
     * @author Henfon
     * @date 2026-08-31
     */
    @Transactional
    public void recordAfterSaleEvent(TradeAfterSale afterSale, String eventType, String topic) {
        // 售后状态与 Outbox 同事务提交，确保通知消费者不会观察到半成品状态。
        TradeEventOutbox event = new TradeEventOutbox();
        event.setEventId(IdWorker.getIdStr());
        event.setEventType(eventType);
        event.setAggregateType("TRADE_AFTER_SALE");
        event.setAggregateId(String.valueOf(afterSale.getId()));
        event.setTopic(topic);
        event.setPayload(buildAfterSalePayload(afterSale));
        event.setStatus(STATUS_PENDING);
        event.setRetryCount(0);
        event.setNextRetryAt(LocalDateTime.now());
        outboxMapper.insert(event);
    }

    /**
     * 记录订单创建事件。
     *
     * @param order 新订单
     * @author Henfon
     * @date 2026-08-30
     */
    public void recordOrderCreated(TradeOrder order) {
        recordOrderEvent(order, "ORDER_CREATED", RocketMqTopics.ORDER_CREATED);
    }

    /**
     * 记录订单取消事件。
     *
     * @param order 已取消订单
     * @param timeout 是否因超时关闭
     * @author Henfon
     * @date 2026-08-30
     */
    public void recordOrderCancelled(TradeOrder order, boolean timeout) {
        recordOrderEvent(order, timeout ? "ORDER_CLOSED_TIMEOUT" : "ORDER_CANCELLED",
                timeout ? RocketMqTopics.ORDER_CLOSED_TIMEOUT : RocketMqTopics.ORDER_CANCELLED);
    }

    /**
     * 记录订单发货事件。
     *
     * @param order 已发货订单
     * @author Henfon
     * @date 2026-08-30
     */
    public void recordOrderShipped(TradeOrder order) {
        recordOrderEvent(order, "ORDER_SHIPPED", RocketMqTopics.ORDER_SHIPPED);
    }

    /**
     * 记录订单完成事件。
     *
     * @param order 已完成订单
     * @author Henfon
     * @date 2026-08-30
     */
    public void recordOrderCompleted(TradeOrder order) {
        recordOrderEvent(order, "ORDER_COMPLETED", RocketMqTopics.ORDER_COMPLETED);
    }

    /**
     * 构造订单事件载荷。
     *
     * @param order 订单实体
     * @return JSON 字符串
     * @author Henfon
     * @date 2026-08-30
     */
    private String buildOrderPayload(TradeOrder order) {
        // 当前事件只携带状态消费所需字段，避免将收货电话等敏感信息写入消息。
        return "{\"orderId\":" + order.getId()
                + ",\"orderNo\":\"" + escape(order.getOrderNo()) + "\""
                + ",\"memberId\":" + (order.getMemberId() == null ? "null" : order.getMemberId())
                + ",\"orderStatus\":" + order.getOrderStatus()
                + ",\"paymentStatus\":" + order.getPaymentStatus() + "}";
    }

    /**
     * 构造售后事件载荷。
     *
     * @param afterSale 售后单实体
     * @return JSON 字符串
     * @author Henfon
     * @date 2026-08-31
     */
    private String buildAfterSalePayload(TradeAfterSale afterSale) {
        // 载荷仅保留通知和后续异步处理所需字段，不携带售后原因等可能含隐私的信息。
        return "{\"afterSaleId\":" + afterSale.getId()
                + ",\"afterSaleNo\":\"" + escape(afterSale.getAfterSaleNo()) + "\""
                + ",\"orderId\":" + (afterSale.getOrderId() == null ? "null" : afterSale.getOrderId())
                + ",\"memberId\":" + (afterSale.getMemberId() == null ? "null" : afterSale.getMemberId())
                + ",\"afterSaleType\":" + (afterSale.getAfterSaleType() == null ? "null" : afterSale.getAfterSaleType())
                + ",\"status\":" + (afterSale.getStatus() == null ? "null" : afterSale.getStatus())
                + ",\"refundAmount\":" + (afterSale.getRefundAmount() == null ? "0" : afterSale.getRefundAmount())
                + "}";
    }

    /**
     * 转义 JSON 字符串中的特殊字符。
     *
     * @param value 原始文本
     * @return 转义后的文本
     * @author Henfon
     * @date 2026-08-30
     */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
