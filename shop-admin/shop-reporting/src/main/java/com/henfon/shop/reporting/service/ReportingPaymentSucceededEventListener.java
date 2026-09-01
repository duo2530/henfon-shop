package com.henfon.shop.reporting.service;

import com.henfon.shop.integration.messaging.RocketMqTopics;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Service;

/**
 * 支付成功事件报表投影消费者。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Service
@RocketMQMessageListener(topic = RocketMqTopics.PAYMENT_SUCCEEDED,
        consumerGroup = "${shop.rocketmq.consumer.reporting-payment-group:shop-reporting-payment}")
public class ReportingPaymentSucceededEventListener extends AbstractReportingEventListener {

    /**
     * 创建支付成功事件消费者。
     *
     * @param projectionService 报表事件投影服务
     * @author Henfon
     * @date 2026-09-01
     */
    public ReportingPaymentSucceededEventListener(ReportingEventProjectionService projectionService) {
        super(projectionService, RocketMqTopics.PAYMENT_SUCCEEDED);
    }
}
