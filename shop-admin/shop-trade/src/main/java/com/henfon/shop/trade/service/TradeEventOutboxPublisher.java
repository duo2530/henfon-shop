package com.henfon.shop.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.integration.messaging.DomainEvent;
import com.henfon.shop.integration.messaging.RocketMqEventPublisher;
import com.henfon.shop.trade.entity.TradeEventOutbox;
import com.henfon.shop.trade.mapper.TradeEventOutboxMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 交易领域事件 Outbox 发布任务。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Service
public class TradeEventOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(TradeEventOutboxPublisher.class);
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_PUBLISHED = 1;
    private static final int STATUS_DEAD = 2;
    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRY_COUNT = 10;

    private final TradeEventOutboxMapper outboxMapper;
    private final RocketMqEventPublisher eventPublisher;

    /**
     * 创建 Outbox 发布任务。
     *
     * @param outboxMapper Outbox 数据访问对象
     * @param eventPublisher RocketMQ 事件发布器
     * @author Henfon
     * @date 2026-08-30
     */
    public TradeEventOutboxPublisher(TradeEventOutboxMapper outboxMapper, RocketMqEventPublisher eventPublisher) {
        this.outboxMapper = outboxMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 扫描并发布待发送事件。
     *
     * @author Henfon
     * @date 2026-08-30
     */
    @Scheduled(fixedDelayString = "${shop.trade.outbox-scan-ms:5000}", initialDelayString = "${shop.trade.outbox-initial-delay-ms:10000}")
    public void publishPendingEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<TradeEventOutbox> events = outboxMapper.selectList(new LambdaQueryWrapper<TradeEventOutbox>()
                .eq(TradeEventOutbox::getStatus, STATUS_PENDING)
                .and(wrapper -> wrapper.isNull(TradeEventOutbox::getNextRetryAt)
                        .or().le(TradeEventOutbox::getNextRetryAt, now))
                .orderByAsc(TradeEventOutbox::getId)
                .last("LIMIT " + BATCH_SIZE));
        for (TradeEventOutbox event : events) {
            publishOne(event);
        }
    }

    /**
     * 发布单条事件并记录重试状态。
     *
     * @param event Outbox 事件
     * @author Henfon
     * @date 2026-08-30
     */
    private void publishOne(TradeEventOutbox event) {
        try {
            eventPublisher.publish(event.getTopic(), new DomainEvent(event.getEventId(), event.getEventType(),
                    event.getAggregateId(), Instant.now(), event.getPayload()));
            event.setStatus(STATUS_PUBLISHED);
            event.setPublishedAt(LocalDateTime.now());
            event.setLastError(null);
            outboxMapper.updateById(event);
        } catch (Exception exception) {
            int retryCount = event.getRetryCount() == null ? 0 : event.getRetryCount();
            retryCount++;
            event.setRetryCount(retryCount);
            event.setLastError(trimError(exception));
            if (retryCount >= MAX_RETRY_COUNT) {
                event.setStatus(STATUS_DEAD);
            } else {
                // 采用最多 32 分钟的指数退避，避免 RocketMQ 不可用时频繁打满日志和网络。
                long delayMinutes = 1L << Math.min(retryCount - 1, 5);
                event.setNextRetryAt(LocalDateTime.now().plusMinutes(delayMinutes));
            }
            outboxMapper.updateById(event);
            log.warn("交易事件发布失败，eventId={}, retryCount={}", event.getEventId(), retryCount, exception);
        }
    }

    /**
     * 截断异常文本，避免错误信息撑大 Outbox 行。
     *
     * @param exception 发布异常
     * @return 最多1000字符的错误信息
     * @author Henfon
     * @date 2026-08-30
     */
    private String trimError(Exception exception) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
