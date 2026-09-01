package com.henfon.shop.content.service;

import com.henfon.shop.integration.messaging.RocketMqTopics;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Service;

/**
 * 售后退货入库事件通知消费者。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Service
@RocketMQMessageListener(topic = RocketMqTopics.AFTER_SALE_RETURN_RECEIVED,
        consumerGroup = "${shop.rocketmq.consumer.after-sale-group:shop-content-after-sale-notification}")
public class AfterSaleReturnReceivedNotificationListener extends AbstractContentNotificationRocketMqListener {

    /**
     * 创建售后退货入库通知消费者。
     *
     * @param eventHandler 领域事件处理器
     * @author Henfon
     * @date 2026-09-01
     */
    public AfterSaleReturnReceivedNotificationListener(ContentNotificationEventHandler eventHandler) {
        super(eventHandler);
    }
}
