package com.henfon.shop.config;

import com.henfon.shop.common.web.RequestIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Web 层基础配置。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Configuration
public class WebConfiguration {

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
}
