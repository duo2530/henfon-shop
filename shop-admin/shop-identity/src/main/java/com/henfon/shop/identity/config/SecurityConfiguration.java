package com.henfon.shop.identity.config;

import com.henfon.shop.identity.security.JwtAuthenticationFilter;
import com.henfon.shop.identity.security.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 管理端无状态 JWT 安全配置。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfiguration {

    /**
     * 配置管理端安全过滤链。
     *
     * @param http Spring Security HTTP 配置器
     * @param jwtAuthenticationFilter JWT 过滤器
     * @return 安全过滤链
     * @throws Exception 安全配置异常
     * @author Henfon
     * @date 2026-08-29
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/auth/login", "/api/admin/auth/refresh", "/api/admin/auth/logout",
                                "/actuator/health", "/error",
                                // 微信支付 V3 回调由平台直接调用，不携带商城 JWT，必须放行验签入口。
                                "/api/wx/pay/notify", "/api/wx/pay/notify/v3",
                                "/api/wx/pay/refund/notify", "/api/wx/pay/refund/notify/v3",
                                "/api/payment/invoices/callback").permitAll()
                        // 商品、内容、可领取优惠券和秒杀活动查询面向访客开放；会员数据和交易接口必须携带会员 JWT。
                        // 同时放行带尾斜杠的 GET 请求，兼容浏览器或网关规范化后的门户地址。
                        .requestMatchers("/api/portal/auth/**", "/api/portal/catalog/**", "/api/portal/content/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/portal/marketing/coupons", "/api/portal/marketing/coupons/",
                                "/api/portal/marketing/flash-sales", "/api/portal/marketing/flash-sales/")
                        .permitAll()
                        .requestMatchers("/api/portal/member/**", "/api/portal/trade/**",
                                "/api/portal/marketing/member-coupons").authenticated()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":\"AUTH_REQUIRED\",\"message\":\"请先登录\",\"data\":null}");
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":\"AUTH_FORBIDDEN\",\"message\":\"无权访问该资源\",\"data\":null}");
                        }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());
        return http.build();
    }

    /**
     * 配置开发环境跨域策略，生产环境应在网关层限制允许来源。
     *
     * @return CORS 配置源
     * @author Henfon
     * @date 2026-08-29
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(java.util.List.of("http://localhost:*", "http://127.0.0.1:*"));
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
