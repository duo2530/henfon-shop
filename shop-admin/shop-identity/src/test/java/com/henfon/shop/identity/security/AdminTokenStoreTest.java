package com.henfon.shop.identity.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理员刷新令牌 Redis 存储测试。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@ExtendWith(MockitoExtension.class)
class AdminTokenStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private AdminTokenStore tokenStore;

    /**
     * 初始化令牌存储测试对象。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        tokenStore = new AdminTokenStore(redisTemplate);
    }

    /**
     * 验证消费刷新令牌使用 Redis 原子 GETDEL，避免并发重放。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldConsumeRefreshTokenAtomically() {
        when(valueOperations.getAndDelete("shop:admin:refresh:refresh-token")).thenReturn("8:42");

        AdminRefreshIdentity identity = tokenStore.consumeIdentity("refresh-token");

        assertEquals(new AdminRefreshIdentity(42L, 8L), identity);
        verify(valueOperations).getAndDelete("shop:admin:refresh:refresh-token");
    }
}
