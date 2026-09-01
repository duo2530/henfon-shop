package com.henfon.shop.integration.logistics;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 物流集成模块配置入口。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Configuration
@EnableConfigurationProperties(Kuaidi100Properties.class)
public class LogisticsIntegrationConfiguration {
}
