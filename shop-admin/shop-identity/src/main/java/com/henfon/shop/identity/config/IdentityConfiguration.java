package com.henfon.shop.identity.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 身份模块基础配置。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Configuration
@EnableConfigurationProperties({MemberPasswordResetProperties.class, MemberSmsProperties.class})
public class IdentityConfiguration {

    /**
     * 创建 BCrypt 密码编码器。
     *
     * @return 密码编码器
     * @author Henfon
     * @date 2026-08-29
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        // 密码只保存不可逆哈希，登录认证阶段使用同一编码器校验。
        return new BCryptPasswordEncoder();
    }

    /**
     * 创建 MyBatis-Plus 数据库方言与乐观锁拦截器。
     *
     * @return MyBatis-Plus 拦截器
     * @author Henfon
     * @date 2026-08-29
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件统一使用 MySQL8 方言，乐观锁插件负责处理实体 @Version 字段。
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
