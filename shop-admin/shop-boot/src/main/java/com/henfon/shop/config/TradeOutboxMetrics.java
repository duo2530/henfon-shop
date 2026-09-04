package com.henfon.shop.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.trade.entity.TradeEventOutbox;
import com.henfon.shop.trade.mapper.TradeEventOutboxMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 交易 Outbox 运行指标采集器，供 Actuator 和部署平台配置死信告警。
 *
 * @author Henfon
 * @date 2026-09-04
 */
@Component
public class TradeOutboxMetrics {

    private static final Logger log = LoggerFactory.getLogger(TradeOutboxMetrics.class);
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_DEAD = 2;

    private final TradeEventOutboxMapper outboxMapper;
    private final AtomicLong pendingCount = new AtomicLong();
    private final AtomicLong deadCount = new AtomicLong();
    private final AtomicLong oldestDeadAgeSeconds = new AtomicLong();

    /**
     * 创建 Outbox 指标采集器并注册 Gauge 指标。
     *
     * @param outboxMapper Outbox 数据访问对象
     * @param meterRegistry Micrometer 指标注册表
     * @author Henfon
     * @date 2026-09-04
     */
    public TradeOutboxMetrics(TradeEventOutboxMapper outboxMapper, MeterRegistry meterRegistry) {
        this.outboxMapper = outboxMapper;
        meterRegistry.gauge("shop.trade.outbox.pending", pendingCount);
        meterRegistry.gauge("shop.trade.outbox.dead", deadCount);
        meterRegistry.gauge("shop.trade.outbox.oldest_dead_age_seconds", oldestDeadAgeSeconds);
    }

    /**
     * 定时刷新 Outbox 待发送、死信数量及最老死信年龄。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Scheduled(fixedDelayString = "${shop.trade.outbox-metrics-scan-ms:30000}",
            initialDelayString = "${shop.trade.outbox-metrics-initial-delay-ms:15000}")
    public void refresh() {
        try {
            pendingCount.set(countByStatus(STATUS_PENDING));
            deadCount.set(countByStatus(STATUS_DEAD));
            oldestDeadAgeSeconds.set(findOldestDeadAgeSeconds());
        } catch (Exception exception) {
            // 数据库短暂不可用时保留上一轮值，避免监控线程异常影响业务调度。
            log.warn("刷新交易 Outbox 监控指标失败", exception);
        }
    }

    /**
     * 统计指定状态的 Outbox 事件数量。
     *
     * @param status Outbox 状态
     * @return 事件数量
     * @author Henfon
     * @date 2026-09-04
     */
    private long countByStatus(int status) {
        // 使用数据库聚合统计，避免将全部 Outbox 事件加载到应用内存。
        Long count = outboxMapper.selectCount(new LambdaQueryWrapper<TradeEventOutbox>()
                .eq(TradeEventOutbox::getStatus, status));
        return count == null ? 0L : count;
    }

    /**
     * 计算最老死信事件距当前时间的秒数。
     *
     * @return 最老死信年龄，暂无死信时返回0
     * @author Henfon
     * @date 2026-09-04
     */
    private long findOldestDeadAgeSeconds() {
        // 仅查询最早一条死信，控制指标刷新对数据库的额外开销。
        List<TradeEventOutbox> events = outboxMapper.selectList(new LambdaQueryWrapper<TradeEventOutbox>()
                .eq(TradeEventOutbox::getStatus, STATUS_DEAD)
                .orderByAsc(TradeEventOutbox::getCreatedAt)
                .last("LIMIT 1"));
        if (events.isEmpty() || events.get(0).getCreatedAt() == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(events.get(0).getCreatedAt(), LocalDateTime.now()).getSeconds());
    }
}
