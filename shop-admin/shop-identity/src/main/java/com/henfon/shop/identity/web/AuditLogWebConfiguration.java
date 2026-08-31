package com.henfon.shop.identity.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 管理端审计拦截器 Web 配置。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Configuration
public class AuditLogWebConfiguration implements WebMvcConfigurer {

    private final AuditLogInterceptor auditLogInterceptor;

    /**
     * 创建审计拦截器配置。
     *
     * @param auditLogInterceptor 审计拦截器
     * @author Henfon
     * @date 2026-08-31
     */
    public AuditLogWebConfiguration(AuditLogInterceptor auditLogInterceptor) {
        this.auditLogInterceptor = auditLogInterceptor;
    }

    /**
     * 注册管理端审计拦截器。
     *
     * @param registry 拦截器注册器
     * @author Henfon
     * @date 2026-08-31
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditLogInterceptor).addPathPatterns("/api/admin/**");
    }
}
