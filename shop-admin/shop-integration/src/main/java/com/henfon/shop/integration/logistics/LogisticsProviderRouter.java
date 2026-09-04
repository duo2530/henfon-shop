package com.henfon.shop.integration.logistics;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 物流服务商路由器，提供优先级选择及失败熔断能力。
 *
 * @author Henfon
 * @date 2026-09-04
 */
@Component
@Primary
public class LogisticsProviderRouter implements LogisticsProvider {
    private final LogisticsProviderRoutingProperties properties;
    private final Map<String, LogisticsProvider> providers = new ConcurrentHashMap<>();
    private final Map<String, HealthState> states = new ConcurrentHashMap<>();
    private final Clock clock;

    /** 创建物流服务商路由器。 @param properties 路由配置 @param kuaidi100 快递100服务商 @author Henfon @date 2026-09-04 */
    public LogisticsProviderRouter(LogisticsProviderRoutingProperties properties,
                                   Kuaidi100LogisticsProvider kuaidi100) {
        this(properties, kuaidi100, Clock.systemUTC());
    }

    /** 创建可注入时钟的路由器，便于测试熔断冷却。 @param properties 路由配置 @param kuaidi100 快递100服务商 @param clock 时钟 @author Henfon @date 2026-09-04 */
    LogisticsProviderRouter(LogisticsProviderRoutingProperties properties,
                            Kuaidi100LogisticsProvider kuaidi100, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        providers.put(kuaidi100.providerCode(), kuaidi100);
    }

    /** 返回当前路由器编码。 @return router @author Henfon @date 2026-09-04 */
    @Override
    public String providerCode() { return "router"; }

    /** 判断至少存在一个可用服务商。 @return 是否可用 @author Henfon @date 2026-09-04 */
    @Override
    public boolean enabled() {
        return selectProvider() != null;
    }

    /** 按配置优先级查询物流，失败后自动切换备用服务商。 @param logisticsCompany 物流公司 @param trackingNo 运单号 @return 查询结果 @author Henfon @date 2026-09-04 */
    @Override
    public LogisticsTrackResult query(String logisticsCompany, String trackingNo) {
        List<String> order = properties.getProviders() == null || properties.getProviders().isEmpty()
                ? List.of("kuaidi100") : properties.getProviders();
        LogisticsTrackResult last = LogisticsTrackResult.failure(providerCode(), trackingNo, "暂无可用物流服务商");
        for (String code : order) {
            LogisticsProvider provider = providers.get(code);
            if (provider == null || !provider.enabled() || isOpen(code)) continue;
            // 单个服务商失败会累积熔断计数，随后尝试下一个备用服务商。
            LogisticsTrackResult result = provider.query(logisticsCompany, trackingNo);
            if (result.success()) { states.computeIfAbsent(code, k -> new HealthState()).reset(); return result; }
            last = result;
            if (states.computeIfAbsent(code, k -> new HealthState()).recordFailure(threshold())) {
                states.get(code).openedAt = clock.millis();
            }
        }
        return last;
    }

    /** 返回有效的熔断阈值。 @return 失败次数 @author Henfon @date 2026-09-04 */
    private int threshold() { return Math.max(1, properties.getFailureThreshold()); }

    /** 检查服务商是否处于熔断冷却期。 @param code 服务商编码 @return 是否熔断 @author Henfon @date 2026-09-04 */
    private boolean isOpen(String code) {
        HealthState state = states.get(code);
        if (state == null || state.openedAt == 0) return false;
        if (clock.millis() - state.openedAt >= Math.max(1, properties.getOpenDurationSeconds()) * 1000L) {
            state.openedAt = 0;
            state.failures.set(0);
            return false;
        }
        return true;
    }

    /** 选择首个可用服务商。 @return 服务商或空值 @author Henfon @date 2026-09-04 */
    private LogisticsProvider selectProvider() {
        for (String code : properties.getProviders()) {
            LogisticsProvider provider = providers.get(code);
            if (provider != null && provider.enabled() && !isOpen(code)) return provider;
        }
        return null;
    }

    private static final class HealthState {
        private final AtomicInteger failures = new AtomicInteger();
        private volatile long openedAt;
        private boolean recordFailure(int threshold) { return failures.incrementAndGet() >= threshold; }
        private void reset() { failures.set(0); openedAt = 0; }
    }
}
