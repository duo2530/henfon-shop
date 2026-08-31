package com.henfon.shop.content.service;

import com.henfon.shop.integration.messaging.RocketMqTopics;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Service;

/**
 * 支付成功事件通知消费者。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Service
@RocketMQMessageListener(topic = RocketMqTopics.PAYMENT_SUCCEEDED,
        consumerGroup = "${shop.rocketmq.consumer.payment-group:shop-content-payment-notification}")
public class PaymentSucceededNotificationListener extends AbstractContentNotificationRocketMqListener {

    /**
     * 创建支付成功通知消费者。
     *
     * @param eventHandler 领域事件处理器
     * @author Henfon
     * @date 2026-08-31
     */
    public PaymentSucceededNotificationListener(ContentNotificationEventHandler eventHandler) {
        super(eventHandler);
    }
}
