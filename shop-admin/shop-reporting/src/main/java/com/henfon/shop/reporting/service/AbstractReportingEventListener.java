package com.henfon.shop.reporting.service;

import com.henfon.shop.integration.messaging.DomainEvent;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 报表领域事件RocketMQ消费者基类。
 *
 * @author Henfon
 * @date 2026-09-01
 */
public abstract class AbstractReportingEventListener implements RocketMQListener<DomainEvent> {

    private static final Logger log = LoggerFactory.getLogger(AbstractReportingEventListener.class);

    private final ReportingEventProjectionService projectionService;
    private final String topic;

    /**
     * 创建报表事件消费者基类。
     *
     * @param projectionService 事件投影服务
     * @param topic RocketMQ主题
     * @author Henfon
     * @date 2026-09-01
     */
    protected AbstractReportingEventListener(ReportingEventProjectionService projectionService, String topic) {
        this.projectionService = projectionService;
        this.topic = topic;
    }

    /**
     * 接收领域事件并写入报表投影。
     *
     * @param event RocketMQ领域事件
     * @author Henfon
     * @date 2026-09-01
     */
    @Override
    public void onMessage(DomainEvent event) {
        try {
            projectionService.project(topic, event);
        } catch (RuntimeException exception) {
            // 继续抛出异常让RocketMQ执行重试和死信投递，避免事件静默丢失。
            log.warn("报表领域事件消费失败，topic={}，eventId={}", topic,
                    event == null ? "null" : event.eventId(), exception);
            throw exception;
        }
    }
}
