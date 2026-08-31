package com.henfon.shop.identity.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 管理员登录失败计数与短期锁定服务。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Service
public class LoginFailureTracker {

    private static final String FAILURE_PREFIX = "shop:admin:login:failure:";
    private static final String LOCK_PREFIX = "shop:admin:login:locked:";
    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;

    /**
     * 创建管理员登录失败跟踪器。
     *
     * @param redisTemplate Redis 字符串模板
     * @author Henfon
     * @date 2026-08-31
     */
    public LoginFailureTracker(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 判断账号是否处于失败锁定窗口。
     *
     * @param tenantId 租户ID
     * @param username 登录账号
     * @return 是否锁定
     * @author Henfon
     * @date 2026-08-31
     */
    public boolean isLocked(long tenantId, String username) {
        return username != null && Boolean.TRUE.equals(redisTemplate.hasKey(lockKey(tenantId, username)));
    }

    /**
     * 记录一次登录失败，达到阈值后锁定账号。
     *
     * @param tenantId 租户ID
     * @param username 登录账号
     * @return 当前窗口内失败次数
     * @author Henfon
     * @date 2026-08-31
     */
    public long recordFailure(long tenantId, String username) {
        if (username == null || username.isBlank()) {
            return 0L;
        }
        String key = failureKey(tenantId, username);
        Long count = redisTemplate.opsForValue().increment(key);
        // 首次失败设置窗口过期时间，避免失败计数永久占用 Redis。
        if (count != null && count == 1L) {
            redisTemplate.expire(key, WINDOW);
        }
        if (count != null && count >= MAX_FAILURES) {
            redisTemplate.opsForValue().set(lockKey(tenantId, username), "1", WINDOW);
        }
        return count == null ? 0L : count;
    }

    /**
     * 登录成功后清理失败计数和锁定状态。
     *
     * @param tenantId 租户ID
     * @param username 登录账号
     * @author Henfon
     * @date 2026-08-31
     */
    public void reset(long tenantId, String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        // 删除操作幂等，允许成功登录并发触发多次清理。
        redisTemplate.delete(failureKey(tenantId, username));
        redisTemplate.delete(lockKey(tenantId, username));
    }

    /**
     * 构造登录失败计数键。
     *
     * @param tenantId 租户ID
     * @param username 登录账号
     * @return Redis 键
     * @author Henfon
     * @date 2026-08-31
     */
    private String failureKey(long tenantId, String username) {
        return FAILURE_PREFIX + tenantId + ":" + username.trim().toLowerCase();
    }

    /**
     * 构造账号锁定状态键。
     *
     * @param tenantId 租户ID
     * @param username 登录账号
     * @return Redis 键
     * @author Henfon
     * @date 2026-08-31
     */
    private String lockKey(long tenantId, String username) {
        return LOCK_PREFIX + tenantId + ":" + username.trim().toLowerCase();
    }
}
