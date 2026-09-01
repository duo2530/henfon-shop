package com.henfon.shop.reporting.service;

import com.henfon.shop.integration.messaging.RocketMqTopics;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Service;

/**
 * 退款成功事件报表投影消费者。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Service
@RocketMQMessageListener(topic = RocketMqTopics.REFUND_SUCCEEDED,
        consumerGroup = "${shop.rocketmq.consumer.reporting-refund-group:shop-reporting-refund}")
public class ReportingRefundSucceededEventListener extends AbstractReportingEventListener {

    /**
     * 创建退款成功事件消费者。
     *
     * @param projectionService 报表事件投影服务
     * @author Henfon
     * @date 2026-09-01
     */
    public ReportingRefundSucceededEventListener(ReportingEventProjectionService projectionService) {
        super(projectionService, RocketMqTopics.REFUND_SUCCEEDED);
    }
}
