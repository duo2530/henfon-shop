package com.henfon.shop.reporting.service;

import com.henfon.shop.integration.messaging.RocketMqTopics;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Service;

/**
 * 订单发货事件报表投影消费者。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Service
@RocketMQMessageListener(topic = RocketMqTopics.ORDER_SHIPPED,
        consumerGroup = "${shop.rocketmq.consumer.reporting-order-shipped-group:shop-reporting-order-shipped}")
public class ReportingOrderShippedEventListener extends AbstractReportingEventListener {

    /**
     * 创建订单发货事件消费者。
     *
     * @param projectionService 报表事件投影服务
     * @author Henfon
     * @date 2026-09-01
     */
    public ReportingOrderShippedEventListener(ReportingEventProjectionService projectionService) {
        super(projectionService, RocketMqTopics.ORDER_SHIPPED);
    }
}
