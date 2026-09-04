package com.henfon.shop.identity.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

/**
 * 会员密码登录失败计数与短期锁定服务。
 *
 * @author Henfon
 * @date 2026-09-04
 */
@Service
public class MemberLoginFailureTracker {

    private static final String FAILURE_PREFIX = "shop:member:login:failure:";
    private static final String LOCK_PREFIX = "shop:member:login:locked:";
    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;

    /**
     * 创建会员登录失败跟踪器。
     *
     * @param redisTemplate Redis 字符串模板
     * @author Henfon
     * @date 2026-09-04
     */
    public MemberLoginFailureTracker(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 判断会员账号是否处于锁定窗口。
     *
     * @param account 登录账号
     * @return 是否锁定
     * @author Henfon
     * @date 2026-09-04
     */
    public boolean isLocked(String account) {
        return account != null && !account.isBlank()
                && Boolean.TRUE.equals(redisTemplate.hasKey(lockKey(account)));
    }

    /**
     * 记录一次密码登录失败并在达到阈值后锁定账号。
     *
     * @param account 登录账号
     * @return 当前窗口失败次数
     * @author Henfon
     * @date 2026-09-04
     */
    public long recordFailure(String account) {
        if (account == null || account.isBlank()) return 0L;
        String key = failureKey(account);
        Long count = redisTemplate.opsForValue().increment(key);
        // 首次失败设置过期时间，避免失败计数永久占用 Redis。
        if (count != null && count == 1L) redisTemplate.expire(key, WINDOW);
        if (count != null && count >= MAX_FAILURES) {
            redisTemplate.opsForValue().set(lockKey(account), "1", WINDOW);
        }
        return count == null ? 0L : count;
    }

    /**
     * 登录成功后清理失败计数与锁定状态。
     *
     * @param account 登录账号
     * @author Henfon
     * @date 2026-09-04
     */
    public void reset(String account) {
        if (account == null || account.isBlank()) return;
        // 删除操作幂等，允许并发成功登录安全执行。
        redisTemplate.delete(failureKey(account));
        redisTemplate.delete(lockKey(account));
    }

    private String failureKey(String account) {
        return FAILURE_PREFIX + normalize(account);
    }

    private String lockKey(String account) {
        return LOCK_PREFIX + normalize(account);
    }

    private String normalize(String account) {
        return account.trim().toLowerCase(Locale.ROOT);
    }
}
