package com.henfon.shop.content.service;

import com.henfon.shop.integration.messaging.RocketMqTopics;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Service;

/**
 * 售后审核通过事件通知消费者。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Service
@RocketMQMessageListener(topic = RocketMqTopics.AFTER_SALE_APPROVED,
        consumerGroup = "${shop.rocketmq.consumer.after-sale-group:shop-content-after-sale-notification}")
public class AfterSaleApprovedNotificationListener extends AbstractContentNotificationRocketMqListener {

    /**
     * 创建售后审核通过通知消费者。
     *
     * @param eventHandler 领域事件处理器
     * @author Henfon
     * @date 2026-08-31
     */
    public AfterSaleApprovedNotificationListener(ContentNotificationEventHandler eventHandler) {
        super(eventHandler);
    }
}
