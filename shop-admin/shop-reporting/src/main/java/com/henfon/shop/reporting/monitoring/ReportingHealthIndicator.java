package com.henfon.shop.reporting.monitoring;

import com.henfon.shop.reporting.mapper.ReportingEventProjectionMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 报表数据链路健康检查及运行指标采集器。
 *
 * @author Henfon
 * @date 2026-09-04
 */
@Component("reporting")
public class ReportingHealthIndicator implements HealthIndicator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportingHealthIndicator.class);

    private final ReportingEventProjectionMapper projectionMapper;
    private final long maxLagSeconds;
    private final AtomicLong projectionCount = new AtomicLong();
    private final AtomicLong anomalyCount = new AtomicLong();
    private final AtomicLong lagSeconds = new AtomicLong();
    private volatile LocalDateTime latestOccurredAt;
    private volatile LocalDateTime latestProjectedAt;

    /**
     * 创建报表健康检查器并注册 Micrometer 指标。
     *
     * @param projectionMapper 报表事件投影数据访问对象
     * @param meterRegistry 指标注册表
     * @param maxLagSeconds 允许的最大统计延迟秒数
     * @author Henfon
     * @date 2026-09-04
     */
    public ReportingHealthIndicator(ReportingEventProjectionMapper projectionMapper,
                                    MeterRegistry meterRegistry,
                                    @Value("${shop.reporting.health.max-lag-seconds:300}") long maxLagSeconds) {
        this.projectionMapper = projectionMapper;
        this.maxLagSeconds = Math.max(1L, maxLagSeconds);
        meterRegistry.gauge("shop.reporting.projection.count", projectionCount);
        meterRegistry.gauge("shop.reporting.anomaly.count", anomalyCount);
        meterRegistry.gauge("shop.reporting.lag.seconds", lagSeconds);
    }

    /**
     * 定时刷新投影数量、统计延迟和异常数据指标。
     *
     * @author Henfon
     * @date 2026-09-04
     */
    @Scheduled(fixedDelayString = "${shop.reporting.health.scan-ms:30000}",
            initialDelayString = "${shop.reporting.health.initial-delay-ms:10000}")
    public void refresh() {
        try {
            projectionCount.set(valueOrZero(projectionMapper.countActiveProjections()));
            anomalyCount.set(valueOrZero(projectionMapper.countAnomalies()));
            latestOccurredAt = projectionMapper.findLatestOccurredAt();
            latestProjectedAt = projectionMapper.findLatestProjectedAt();
            lagSeconds.set(calculateLagSeconds(latestOccurredAt, latestProjectedAt));
            // 延迟或异常记录达到阈值时输出结构化日志，交由日志平台触发通知。
            if (anomalyCount.get() > 0 || lagSeconds.get() > maxLagSeconds) {
                LOGGER.error("报表数据异常告警，projectionCount={}, anomalyCount={}, lagSeconds={}, maxLagSeconds={}",
                        projectionCount.get(), anomalyCount.get(), lagSeconds.get(), maxLagSeconds);
            }
        } catch (Exception exception) {
            // 监控查询失败不影响报表接口，保留上一轮指标并记录原因。
            LOGGER.warn("刷新报表监控指标失败", exception);
        }
    }

    /**
     * 返回报表数据链路健康状态。
     *
     * @return 报表健康状态及关键指标
     * @author Henfon
     * @date 2026-09-04
     */
    @Override
    public Health health() {
        boolean healthy = anomalyCount.get() == 0 && lagSeconds.get() <= maxLagSeconds;
        Health.Builder builder = healthy ? Health.up() : Health.down();
        return builder.withDetail("projectionCount", projectionCount.get())
                .withDetail("anomalyCount", anomalyCount.get())
                .withDetail("lagSeconds", lagSeconds.get())
                .withDetail("maxLagSeconds", maxLagSeconds)
                .withDetail("latestOccurredAt", latestOccurredAt)
                .withDetail("latestProjectedAt", latestProjectedAt)
                .build();
    }

    /**
     * 计算事件发生时间与投影时间之间的延迟。
     *
     * @param occurredAt 最近事件时间
     * @param projectedAt 最近投影时间
     * @return 延迟秒数
     * @author Henfon
     * @date 2026-09-04
     */
    private long calculateLagSeconds(LocalDateTime occurredAt, LocalDateTime projectedAt) {
        if (occurredAt == null) {
            return 0L;
        }
        LocalDateTime reference = projectedAt == null ? LocalDateTime.now() : projectedAt;
        return Math.max(0L, Duration.between(occurredAt, reference).getSeconds());
    }

    /**
     * 将数据库空聚合结果转换为零。
     *
     * @param value 数据库统计值
     * @return 非空数量
     * @author Henfon
     * @date 2026-09-04
     */
    private long valueOrZero(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }
}
