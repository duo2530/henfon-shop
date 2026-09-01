package com.henfon.shop.reporting.service;

import com.henfon.shop.integration.messaging.RocketMqTopics;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Service;

/**
 * 订单超时关闭事件报表投影消费者。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Service
@RocketMQMessageListener(topic = RocketMqTopics.ORDER_CLOSED_TIMEOUT,
        consumerGroup = "${shop.rocketmq.consumer.reporting-order-timeout-group:shop-reporting-order-timeout}")
public class ReportingOrderClosedTimeoutEventListener extends AbstractReportingEventListener {

    /**
     * 创建订单超时关闭事件消费者。
     *
     * @param projectionService 报表事件投影服务
     * @author Henfon
     * @date 2026-09-01
     */
    public ReportingOrderClosedTimeoutEventListener(ReportingEventProjectionService projectionService) {
        super(projectionService, RocketMqTopics.ORDER_CLOSED_TIMEOUT);
    }
}
