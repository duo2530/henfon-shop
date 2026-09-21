package com.henfon.shop.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 配置属性的注册入口。
 *
 * <p>单独放一个不带条件的配置类，而不是把 {@code @EnableConfigurationProperties} 挂在
 * {@link AiConfiguration} 上：后者受 {@code shop.ai.enabled} 控制，属性注册写在里面时，
 * AI 关闭会让 {@code AiProperties} 这个 Bean 一并消失。任何始终注册的组件（例如配额计数器
 * {@code AiChatRateLimiter}）一旦依赖它，就会以「找不到 Bean」直接启动失败——报错指向的是
 * 依赖方，而根因在配置类的条件上，排查容易跑偏。</p>
 *
 * <p>属性对象只是配置载体，注册它不会建立任何指向百炼或 Qdrant 的连接，所以始终注册没有副作用；
 * AI 开关仍然只由 {@link AiConfiguration} 等类上的条件控制。</p>
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiPropertiesConfiguration {
}
