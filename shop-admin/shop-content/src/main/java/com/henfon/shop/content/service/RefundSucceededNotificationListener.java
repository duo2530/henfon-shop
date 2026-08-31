package com.henfon.shop.content.service;

import com.henfon.shop.integration.messaging.RocketMqTopics;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Service;

/**
 * 退款成功事件通知消费者。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Service
@RocketMQMessageListener(topic = RocketMqTopics.REFUND_SUCCEEDED,
        consumerGroup = "${shop.rocketmq.consumer.refund-group:shop-content-refund-notification}")
public class RefundSucceededNotificationListener extends AbstractContentNotificationRocketMqListener {

    /**
     * 创建退款成功通知消费者。
     *
     * @param eventHandler 领域事件处理器
     * @author Henfon
     * @date 2026-08-31
     */
    public RefundSucceededNotificationListener(ContentNotificationEventHandler eventHandler) {
        super(eventHandler);
    }
}
