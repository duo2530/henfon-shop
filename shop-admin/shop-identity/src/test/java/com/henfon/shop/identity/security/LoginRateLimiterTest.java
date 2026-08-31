package com.henfon.shop.identity.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理员登录限流器测试。
 *
 * @author Henfon
 * @date 2026-08-31
 */
class LoginRateLimiterTest {

    /**
     * 验证单 IP 超过窗口阈值后被拒绝。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldRejectAttemptsAfterLimit() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int index = 0; index < 10; index++) {
            assertTrue(limiter.allow("127.0.0.1"));
        }
        assertFalse(limiter.allow("127.0.0.1"));
    }

    /**
     * 验证登录成功后可以清零指定 IP 的尝试次数。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldResetAttemptsAfterSuccess() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int index = 0; index < 10; index++) {
            limiter.allow("127.0.0.2");
        }
        assertFalse(limiter.allow("127.0.0.2"));
        limiter.reset("127.0.0.2");
        assertTrue(limiter.allow("127.0.0.2"));
    }
}
