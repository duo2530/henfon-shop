package com.henfon.shop.content.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 内容通知模块配置入口。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Configuration
@EnableConfigurationProperties(EmailNotificationProperties.class)
public class ContentNotificationConfiguration {
}
