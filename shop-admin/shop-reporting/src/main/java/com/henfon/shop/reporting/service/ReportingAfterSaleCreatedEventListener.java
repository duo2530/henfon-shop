package com.henfon.shop.reporting.service;

import com.henfon.shop.integration.messaging.RocketMqTopics;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Service;

/**
 * 售后创建事件报表投影消费者。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Service
@RocketMQMessageListener(topic = RocketMqTopics.AFTER_SALE_CREATED,
        consumerGroup = "${shop.rocketmq.consumer.reporting-after-sale-group:shop-reporting-after-sale}")
public class ReportingAfterSaleCreatedEventListener extends AbstractReportingEventListener {

    /**
     * 创建售后创建事件消费者。
     *
     * @param projectionService 报表事件投影服务
     * @author Henfon
     * @date 2026-09-01
     */
    public ReportingAfterSaleCreatedEventListener(ReportingEventProjectionService projectionService) {
        super(projectionService, RocketMqTopics.AFTER_SALE_CREATED);
    }
}
