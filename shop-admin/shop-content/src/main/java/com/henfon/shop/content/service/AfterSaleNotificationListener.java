package com.henfon.shop.content.service;

import com.henfon.shop.integration.messaging.RocketMqTopics;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Service;

/**
 * 售后生命周期事件通知消费者。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Service
@RocketMQMessageListener(topic = RocketMqTopics.AFTER_SALE_CREATED,
        consumerGroup = "${shop.rocketmq.consumer.after-sale-group:shop-content-after-sale-notification}")
public class AfterSaleNotificationListener extends AbstractContentNotificationRocketMqListener {

    /**
     * 创建售后通知消费者。
     *
     * @param eventHandler 领域事件处理器
     * @author Henfon
     * @date 2026-08-31
     */
    public AfterSaleNotificationListener(ContentNotificationEventHandler eventHandler) {
        super(eventHandler);
    }
}
