package com.henfon.shop.config;

import com.henfon.shop.common.web.RequestIdFilter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层基础配置。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    private final MeterRegistry meterRegistry;

    /**
     * 创建 Web 层基础配置。
     *
     * @param meterRegistry Micrometer 指标注册表
     * @author Henfon
     * @date 2026-08-31
     */
    public WebConfiguration(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 注册请求标识过滤器。
     *
     * @return 请求标识过滤器注册信息
     * @author Henfon
     * @date 2026-08-29
     */
    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        // 过滤器覆盖所有接口，保证管理端和门户端都能关联日志。
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestIdFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        return registration;
    }

    /**
     * 注册 API 指标拦截器。
     *
     * @param registry MVC 拦截器注册表
     * @author Henfon
     * @date 2026-08-31
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 指标按归一化路径聚合，避免将订单号等动态值写入标签。
        registry.addInterceptor(new ApiMetricsInterceptor(meterRegistry));
    }
}
