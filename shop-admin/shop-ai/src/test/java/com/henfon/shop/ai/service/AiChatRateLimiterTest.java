package com.henfon.shop.ai.service;

import com.henfon.shop.ai.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 门户客服提问配额计数器测试。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@ExtendWith(MockitoExtension.class)
class AiChatRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private AiProperties properties;

    private AiChatRateLimiter rateLimiter;

    /**
     * 初始化配额计数器测试对象。
     *
     * @author Henfon
     * @date 2026-09-21
     */
    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        rateLimiter = new AiChatRateLimiter(redisTemplate, properties);
    }

    /**
     * 验证首次提问放行，并为分钟与日两个窗口都设置过期时间。
     *
     * 过期时间只在首次计数时设置，漏掉就会在 Redis 里留下永不回收的键。
     *
     * @author Henfon
     * @date 2026-09-21
     */
    @Test
    void shouldAllowFirstMemberQuestionAndSetWindowExpiry() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        assertTrue(rateLimiter.allowMember(42L));

        verify(redisTemplate).expire(eq("shop:ai:portal-rate:m42:minute"), eq(Duration.ofSeconds(60)));
        verify(redisTemplate).expire(eq("shop:ai:portal-rate:m42:day"), eq(Duration.ofSeconds(86400)));
    }

    /**
     * 验证会员超出分钟配额时拒绝。
     *
     * @author Henfon
     * @date 2026-09-21
     */
    @Test
    void shouldRejectMemberBeyondMinuteQuota() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // 默认每分钟上限 30，第 31 次拒绝。
        when(valueOperations.increment(anyString())).thenReturn(31L);

        assertFalse(rateLimiter.allowMember(42L));
    }

    /**
     * 验证分钟未超但日配额已满时拒绝。
     *
     * @author Henfon
     * @date 2026-09-21
     */
    @Test
    void shouldRejectMemberBeyondDayQuota() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // 分钟计数正常，日计数超过默认的 500。
        when(valueOperations.increment(anyString())).thenAnswer(invocation ->
                invocation.getArgument(0, String.class).endsWith(":minute") ? 1L : 501L);

        assertFalse(rateLimiter.allowMember(42L));
    }

    /**
     * 验证访客按来源 IP 计数，且使用与会员不同的键空间。
     *
     * @author Henfon
     * @date 2026-09-21
     */
    @Test
    void shouldCountAnonymousByClientIp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // 访客默认每分钟上限 10，第 11 次拒绝。
        when(valueOperations.increment(anyString())).thenReturn(11L);

        assertFalse(rateLimiter.allowAnonymous("1.2.3.4"));

        verify(valueOperations).increment("shop:ai:portal-rate:ip1.2.3.4:minute");
    }

    /**
     * 验证来源 IP 缺失时归入统一桶，不因为取不到 IP 而放行不限次。
     *
     * @author Henfon
     * @date 2026-09-21
     */
    @Test
    void shouldFallbackToUnknownBucketWhenClientIpMissing() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        assertTrue(rateLimiter.allowAnonymous(null));

        verify(valueOperations).increment("shop:ai:portal-rate:ipunknown:minute");
    }

    /**
     * 验证 Redis 故障时放行，不因为限流组件不可用而让客服整体不可用。
     *
     * @author Henfon
     * @date 2026-09-21
     */
    @Test
    void shouldAllowWhenRedisFails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenThrow(new IllegalStateException("redis down"));

        assertTrue(rateLimiter.allowMember(42L));
    }

    /**
     * 验证关闭配额后放行且不访问 Redis。
     *
     * @author Henfon
     * @date 2026-09-21
     */
    @Test
    void shouldAllowAllWhenLimitDisabled() {
        properties.getLimit().setEnabled(false);

        assertTrue(rateLimiter.allowMember(42L));
        assertTrue(rateLimiter.allowAnonymous("1.2.3.4"));

        verifyNoInteractions(redisTemplate);
    }

    /**
     * 验证配额为非正数时视为不限制，跳过计数。
     *
     * @author Henfon
     * @date 2026-09-21
     */
    @Test
    void shouldSkipCountingWhenQuotaNotPositive() {
        properties.getLimit().setMemberPerMinute(0);
        properties.getLimit().setMemberPerDay(0);

        assertTrue(rateLimiter.allowMember(42L));

        verifyNoInteractions(redisTemplate);
    }
}
