package com.henfon.shop.integration.messaging;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.util.ClassUtils;

/**
 * RocketMQ 监听器启动策略配置。
 *
 * <p>开发机或自动化测试未启动 NameServer 时，RocketMQ 消费者不应阻断整个应用启动；
 * 生产环境仍保持监听器默认开启，并可通过环境变量主动开启开发环境监听。</p>
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Configuration
public class RocketMqListenerStartupConfiguration implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

    private static final Logger log = LoggerFactory.getLogger(RocketMqListenerStartupConfiguration.class);
    private static final String CONSUMER_ENABLED_PROPERTY = "shop.rocketmq.consumer-enabled";
    private static final String ENABLED_PROPERTY = "SHOP_ROCKETMQ_CONSUMER_ENABLED";

    private Environment environment;

    /**
     * 注入 Spring 环境配置。
     *
     * @param environment Spring 环境
     * @author Henfon
     * @date 2026-09-01
     */
    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    /**
     * 在 Bean 实例化前按环境移除不可用的 RocketMQ 监听器。
     *
     * @param registry Spring Bean 定义注册表
     * @author Henfon
     * @date 2026-09-01
     */
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        // 生产环境默认保留监听器，开发环境可通过环境变量恢复真实消费。
        if (environment.acceptsProfiles(Profiles.of("prod")) || isConsumerEnabled()) {
            return;
        }
        int removedCount = removeRocketMqListenerDefinitions(registry);
        log.info("RocketMQ 消费监听器未启用，已跳过 {} 个监听器 Bean；如需开启请设置 {}=true",
                removedCount, ENABLED_PROPERTY);
    }

    /**
     * 读取 RocketMQ 消费监听器统一开关。
     *
     * @return 是否开启消费监听器
     * @author Henfon
     * @date 2026-09-01
     */
    private boolean isConsumerEnabled() {
        // 优先读取 Spring 配置，兼容 IDEA 环境变量直接覆盖配置文件的场景。
        String configuredValue = environment.getProperty(CONSUMER_ENABLED_PROPERTY);
        if (configuredValue == null || configuredValue.isBlank()) {
            configuredValue = environment.getProperty(ENABLED_PROPERTY, "false");
        }
        return Boolean.parseBoolean(configuredValue);
    }

    /**
     * 遍历并移除声明了 RocketMQ 监听注解的 Bean 定义。
     *
     * @param registry Spring Bean 定义注册表
     * @return 移除的 Bean 数量
     * @author Henfon
     * @date 2026-09-01
     */
    private int removeRocketMqListenerDefinitions(BeanDefinitionRegistry registry) {
        int removedCount = 0;
        for (String beanName : registry.getBeanDefinitionNames()) {
            BeanDefinition definition = registry.getBeanDefinition(beanName);
            String beanClassName = definition.getBeanClassName();
            if (beanClassName == null || !hasRocketMqListener(beanClassName)) {
                continue;
            }
            registry.removeBeanDefinition(beanName);
            removedCount++;
        }
        return removedCount;
    }

    /**
     * 判断 Bean 类型是否声明了 RocketMQ 监听注解。
     *
     * @param beanClassName Bean 类型名称
     * @return 是否为 RocketMQ 监听器
     * @author Henfon
     * @date 2026-09-01
     */
    private boolean hasRocketMqListener(String beanClassName) {
        try {
            Class<?> beanType = ClassUtils.forName(beanClassName, getClass().getClassLoader());
            return beanType.isAnnotationPresent(RocketMQMessageListener.class);
        } catch (ClassNotFoundException | LinkageError exception) {
            // 非业务 Bean 可能由特殊类加载器提供，无法解析时保留原定义交给 Spring 处理。
            log.debug("无法解析 Bean 类型，跳过 RocketMQ 监听器判断：{}", beanClassName, exception);
            return false;
        }
    }

    /**
     * 预留 BeanFactory 后处理扩展点。
     *
     * @param beanFactory Spring Bean 工厂
     * @author Henfon
     * @date 2026-09-01
     */
    @Override
    public void postProcessBeanFactory(org.springframework.beans.factory.config.ConfigurableListableBeanFactory beanFactory)
            throws BeansException {
        // 当前仅需在 BeanDefinition 阶段过滤监听器，不修改 BeanFactory。
    }
}
