package com.henfon.shop.integration.logistics;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 物流服务商路由与熔断配置。
 *
 * @author Henfon
 * @date 2026-09-04
 */
@ConfigurationProperties(prefix = "shop.integration.logistics.routing")
@Component
public class LogisticsProviderRoutingProperties {
    private List<String> providers = new ArrayList<>(List.of("kuaidi100"));
    private int failureThreshold = 3;
    private long openDurationSeconds = 60;

    /** 读取服务商优先级列表。 @return 服务商编码列表 @author Henfon @date 2026-09-04 */
    public List<String> getProviders() { return providers; }
    /** 设置服务商优先级列表。 @param providers 服务商编码列表 @author Henfon @date 2026-09-04 */
    public void setProviders(List<String> providers) { this.providers = providers; }
    /** 读取熔断失败阈值。 @return 连续失败次数 @author Henfon @date 2026-09-04 */
    public int getFailureThreshold() { return failureThreshold; }
    /** 设置熔断失败阈值。 @param failureThreshold 连续失败次数 @author Henfon @date 2026-09-04 */
    public void setFailureThreshold(int failureThreshold) { this.failureThreshold = failureThreshold; }
    /** 读取熔断冷却时间。 @return 秒数 @author Henfon @date 2026-09-04 */
    public long getOpenDurationSeconds() { return openDurationSeconds; }
    /** 设置熔断冷却时间。 @param openDurationSeconds 秒数 @author Henfon @date 2026-09-04 */
    public void setOpenDurationSeconds(long openDurationSeconds) { this.openDurationSeconds = openDurationSeconds; }
}
