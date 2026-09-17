package com.henfon.shop.marketing.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * 门户秒杀与下单接口的按来源限流过滤器。
 *
 * <p>抢购、普通下单、管理端与健康检查共用同一个 Tomcat 线程池与 Hikari 连接池，没有业务隔离，
 * 这里在最外层加一道闸门，保证任何单一来源都不能把连接池占满。计数放 Redis 是为了多实例
 * 部署时共享配额，写法沿用 LoginFailureTracker 的 increment + expire。Redis 异常时放行
 * （fail-open）：安全性由下单事务里的条件更新保证，限流只负责挡住洪峰。</p>
 *
 * @author Henfon
 * @date 2026-09-17
 */
@Component
public class PortalFlashSaleRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PortalFlashSaleRateLimitFilter.class);

    private static final String KEY_PREFIX = "shop:marketing:portal-rate:";

    private final StringRedisTemplate redisTemplate;

    @Value("${shop.marketing.flash-sale-rate-limit-enabled:false}")
    private boolean enabled;

    @Value("${shop.marketing.flash-sale-rate-limit-limit:120}")
    private long limit;

    @Value("${shop.marketing.flash-sale-rate-limit-window-seconds:60}")
    private long windowSeconds;

    /**
     * 创建门户秒杀限流过滤器。
     *
     * @param redisTemplate Redis 字符串模板
     * @author Henfon
     * @date 2026-09-17
     */
    public PortalFlashSaleRateLimitFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 按来源 IP 与读写类别累计请求数，超限直接返回 429。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException IO 异常
     * @author Henfon
     * @date 2026-09-17
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!enabled || !shouldLimit(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String key = KEY_PREFIX + clientIp(request) + ":" + classify(request);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            // 首次计数时设置窗口过期，避免限流键永久占用 Redis。
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(Math.max(windowSeconds, 1)));
            }
            if (count != null && count > limit) {
                writeTooManyRequests(response);
                return;
            }
        } catch (RuntimeException exception) {
            // 限流组件自身故障不应造成门户不可用，放行后由业务链路兜底。
            log.warn("门户秒杀限流计数失败，本次请求放行，key={}", key, exception);
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 判断请求是否属于限流范围：秒杀列表查询与门户下单。
     *
     * @param request HTTP 请求
     * @return 是否需要限流
     * @author Henfon
     * @date 2026-09-17
     */
    private boolean shouldLimit(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!StringUtils.hasText(path)) {
            return false;
        }
        return path.startsWith("/api/portal/marketing/flash-sales")
                || path.startsWith("/api/portal/trade/orders");
    }

    /**
     * 区分读写配额：一次下单提交比一次列表轮询昂贵得多，两者不共用同一个额度。
     *
     * @param request HTTP 请求
     * @return 计数类别
     * @author Henfon
     * @date 2026-09-17
     */
    private String classify(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod()) ? "write" : "read";
    }

    /**
     * 取客户端 IP，优先使用反向代理透传的 X-Forwarded-For 首个地址。
     *
     * @param request HTTP 请求
     * @return 客户端 IP
     * @author Henfon
     * @date 2026-09-17
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            String first = forwarded.split(",")[0].trim();
            if (StringUtils.hasText(first)) {
                return first;
            }
        }
        String remoteAddress = request.getRemoteAddr();
        return StringUtils.hasText(remoteAddress) ? remoteAddress : "unknown";
    }

    /**
     * 输出限流响应，格式与安全过滤链的 401/403 保持一致。
     *
     * @param response HTTP 响应
     * @throws IOException IO 异常
     * @author Henfon
     * @date 2026-09-17
     */
    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"PORTAL_RATE_LIMITED\",\"message\":\"当前访问过于频繁，请稍后重试\",\"data\":null}");
    }
}
