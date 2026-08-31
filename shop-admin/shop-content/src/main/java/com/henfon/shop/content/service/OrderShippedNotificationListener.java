package com.henfon.shop.content.service;

import com.henfon.shop.integration.messaging.RocketMqTopics;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Service;

/**
 * 订单发货事件通知消费者。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Service
@RocketMQMessageListener(topic = RocketMqTopics.ORDER_SHIPPED,
        consumerGroup = "${shop.rocketmq.consumer.shipped-group:shop-content-shipped-notification}")
public class OrderShippedNotificationListener extends AbstractContentNotificationRocketMqListener {

    /**
     * 创建订单发货通知消费者。
     *
     * @param eventHandler 领域事件处理器
     * @author Henfon
     * @date 2026-08-31
     */
    public OrderShippedNotificationListener(ContentNotificationEventHandler eventHandler) {
        super(eventHandler);
    }
}
