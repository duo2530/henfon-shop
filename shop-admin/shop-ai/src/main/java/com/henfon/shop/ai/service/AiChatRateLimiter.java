package com.henfon.shop.ai.service;

import com.henfon.shop.ai.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 门户客服对话的配额计数器。
 *
 * <p>计数放 Redis 而不是本地内存：多实例部署时额度要共享，否则起三个实例等于把配额放大三倍。</p>
 *
 * <p>Redis 不可用时放行（fail-open）。限流的职责是挡脚本，而对话链路本身有成本闸（单轮
 * {@code maxTokens} 上限、历史窗口条数上限）。为了限流组件故障而让整个客服不可用，是拿
 * 一个次要目标换掉主要目标。</p>
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Component
public class AiChatRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(AiChatRateLimiter.class);

    private static final String KEY_PREFIX = "shop:ai:portal-rate:";

    private static final long MINUTE_SECONDS = 60L;

    private static final long DAY_SECONDS = 86400L;

    private final StringRedisTemplate redisTemplate;

    private final AiProperties properties;

    /**
     * 创建配额计数器。
     *
     * @param redisTemplate Redis 字符串模板
     * @param properties AI 配置
     * @author Henfon
     * @date 2026-09-21
     */
    public AiChatRateLimiter(StringRedisTemplate redisTemplate, AiProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * 按会员维度计数。
     *
     * @param memberId 会员 ID
     * @return 是否放行
     * @author Henfon
     * @date 2026-09-21
     */
    public boolean allowMember(long memberId) {
        AiProperties.Limit limit = properties.getLimit();
        return allow("m" + memberId, limit.getMemberPerMinute(), limit.getMemberPerDay());
    }

    /**
     * 按访客来源 IP 计数。
     *
     * @param clientIp 来源 IP
     * @return 是否放行
     * @author Henfon
     * @date 2026-09-21
     */
    public boolean allowAnonymous(String clientIp) {
        String ip = StringUtils.hasText(clientIp) ? clientIp : "unknown";
        AiProperties.Limit limit = properties.getLimit();
        return allow("ip" + ip, limit.getAnonymousPerMinute(), limit.getAnonymousPerDay());
    }

    /**
     * 判断是否放行并计入配额。
     *
     * @param dimension 计数维度，会员与访客前缀不同
     * @param perMinute 每分钟上限，非正数表示不限制
     * @param perDay 每日上限，非正数表示不限制
     * @return 是否放行
     * @author Henfon
     * @date 2026-09-21
     */
    private boolean allow(String dimension, int perMinute, int perDay) {
        if (!properties.getLimit().isEnabled()) {
            return true;
        }
        try {
            if (exceeded(dimension + ":minute", perMinute, MINUTE_SECONDS)) {
                log.info("客服提问被配额拒绝，dimension={}，窗口=分钟，上限={}", dimension, perMinute);
                return false;
            }
            if (exceeded(dimension + ":day", perDay, DAY_SECONDS)) {
                log.info("客服提问被配额拒绝，dimension={}，窗口=天，上限={}", dimension, perDay);
                return false;
            }
            return true;
        } catch (RuntimeException exception) {
            log.warn("客服配额计数失败，本次提问放行，dimension={}", dimension, exception);
            return true;
        }
    }

    /**
     * 固定窗口计数，判断是否超出上限。
     *
     * 用固定窗口而不是滑动窗口：窗口边界处允许两倍瞬时流量，对"挡脚本"这个目标来说足够，
     * 换来的是不需要在 Redis 里维护有序集合。
     *
     * @param keySuffix 计数键后缀
     * @param quota 窗口内上限，非正数表示不限制
     * @param windowSeconds 窗口秒数
     * @return 是否已超出
     * @author Henfon
     * @date 2026-09-21
     */
    private boolean exceeded(String keySuffix, int quota, long windowSeconds) {
        if (quota <= 0) {
            return false;
        }
        String key = KEY_PREFIX + keySuffix;
        Long count = redisTemplate.opsForValue().increment(key);
        // 首次计数时设置过期，否则限流键会在 Redis 里永久堆积。
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }
        return count != null && count > quota;
    }
}
