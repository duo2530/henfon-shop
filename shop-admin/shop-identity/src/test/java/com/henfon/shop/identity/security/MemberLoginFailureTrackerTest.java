package com.henfon.shop.identity.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 会员密码登录失败锁定策略测试。
 *
 * @author Henfon
 * @date 2026-09-04
 */
@ExtendWith(MockitoExtension.class)
class MemberLoginFailureTrackerTest {
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    private MemberLoginFailureTracker tracker;

    /** 初始化测试对象。 @author Henfon @date 2026-09-04 */
    @BeforeEach
    void setUp() {
        tracker = new MemberLoginFailureTracker(redisTemplate);
    }

    /** 达到五次失败后应锁定账号。 @author Henfon @date 2026-09-04 */
    @Test
    void shouldLockAfterFiveFailures() {
        // opsForValue 仅在失败计数流程中使用，按用例单独打桩，避免其余用例触发严格模式告警。
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(any())).thenReturn(5L);
        assertEquals(5L, tracker.recordFailure(" Alice "));
        verify(valueOperations).set(eq("shop:member:login:locked:alice"), eq("1"), eq(Duration.ofMinutes(15)));
    }

    /** 登录成功清理失败计数和锁定标记。 @author Henfon @date 2026-09-04 */
    @Test
    void shouldResetFailureState() {
        tracker.reset("Alice");
        verify(redisTemplate).delete("shop:member:login:failure:alice");
        verify(redisTemplate).delete("shop:member:login:locked:alice");
    }

    /** 锁定标记存在时应返回锁定状态。 @author Henfon @date 2026-09-04 */
    @Test
    void shouldDetectLockedAccount() {
        when(redisTemplate.hasKey("shop:member:login:locked:alice")).thenReturn(true);
        assertTrue(tracker.isLocked(" Alice "));
    }
}
