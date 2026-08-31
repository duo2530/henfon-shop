package com.henfon.shop.content.service;

import com.henfon.shop.integration.messaging.DomainEvent;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 会员通知 RocketMQ 消费者基类。
 *
 * <p>子类只声明主题和消费组，统一处理日志并把异常继续抛给 RocketMQ，
 * 让客户端按重试策略投递，最终由 RocketMQ 维护死信消息。</p>
 *
 * @author Henfon
 * @date 2026-08-31
 */
public abstract class AbstractContentNotificationRocketMqListener implements RocketMQListener<DomainEvent> {

    private static final Logger log = LoggerFactory.getLogger(AbstractContentNotificationRocketMqListener.class);

    private final ContentNotificationEventHandler eventHandler;

    /**
     * 创建通知事件消费者基类。
     *
     * @param eventHandler 领域事件处理器
     * @author Henfon
     * @date 2026-08-31
     */
    protected AbstractContentNotificationRocketMqListener(ContentNotificationEventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }

    /**
     * 接收领域事件并委托给通知处理器。
     *
     * @param event RocketMQ 领域事件
     * @author Henfon
     * @date 2026-08-31
     */
    @Override
    public void onMessage(DomainEvent event) {
        try {
            eventHandler.handle(event);
        } catch (RuntimeException exception) {
            // 保留 eventId 便于根据 RocketMQ 重试日志定位，异常继续抛出以触发重试/DLQ。
            String eventId = event == null ? "null" : event.eventId();
            log.warn("交易领域事件通知消费失败，eventId={}", eventId, exception);
            throw exception;
        }
    }
}
