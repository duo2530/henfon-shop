package com.henfon.shop.reporting.service;

import com.henfon.shop.integration.messaging.RocketMqTopics;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Service;

/**
 * 退款审核事件报表投影消费者。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Service
@RocketMQMessageListener(topic = RocketMqTopics.REFUND_APPROVED,
        consumerGroup = "${shop.rocketmq.consumer.reporting-refund-group:shop-reporting-refund}")
public class ReportingRefundApprovedEventListener extends AbstractReportingEventListener {

    /**
     * 创建退款审核事件消费者。
     *
     * @param projectionService 报表事件投影服务
     * @author Henfon
     * @date 2026-09-01
     */
    public ReportingRefundApprovedEventListener(ReportingEventProjectionService projectionService) {
        super(projectionService, RocketMqTopics.REFUND_APPROVED);
    }
}
