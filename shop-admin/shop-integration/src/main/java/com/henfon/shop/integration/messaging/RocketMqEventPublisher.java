package com.henfon.shop.integration.messaging;

import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * RocketMQ 领域事件发布器。
 *
 * <p>当前模块化单体通过该适配器发布异步事件，业务模块不直接依赖 RocketMQ 客户端细节。</p>
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Service
public class RocketMqEventPublisher {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 创建 RocketMQ 事件发布器。
     *
     * @param rocketMQTemplate Spring RocketMQ 模板
     * @author Henfon
     * @date 2026-08-29
     */
    public RocketMqEventPublisher(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /**
     * 同步发布一个领域事件。
     *
     * @param topic RocketMQ 主题
     * @param event 领域事件
     * @return RocketMQ 发送结果
     * @author Henfon
     * @date 2026-08-29
     */
    public SendResult publish(String topic, DomainEvent event) {
        // 事件信封携带唯一 ID，消费端应以 eventId 实现幂等。
        Message<DomainEvent> message = MessageBuilder.withPayload(event).build();
        return rocketMQTemplate.syncSend(topic, message);
    }
}
