package com.henfon.shop.identity.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 管理员刷新令牌 Redis 存储，负责一次性轮换和失效控制。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Service
public class AdminTokenStore {

    private static final String REFRESH_PREFIX = "shop:admin:refresh:";
    private static final Duration REFRESH_TTL = Duration.ofDays(30);

    private final StringRedisTemplate redisTemplate;

    /**
     * 创建管理员令牌存储。
     *
     * @param redisTemplate Redis 字符串模板
     * @author Henfon
     * @date 2026-09-01
     */
    public AdminTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 创建并保存管理员刷新令牌。
     *
     * @param userId 管理员用户 ID
     * @param tenantId 租户 ID
     * @return 刷新令牌
     * @author Henfon
     * @date 2026-09-01
     */
    public String createRefreshToken(Long userId, Long tenantId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        // 值中保留租户和用户主体，刷新时重新读取数据库确认账号状态。
        redisTemplate.opsForValue().set(REFRESH_PREFIX + token,
                String.valueOf(tenantId == null ? 0L : tenantId) + ":" + userId, REFRESH_TTL);
        return token;
    }

    /**
     * 获取刷新令牌关联的管理员主体。
     *
     * @param refreshToken 刷新令牌
     * @return 管理员主体，不存在或格式非法时返回 null
     * @author Henfon
     * @date 2026-09-01
     */
    public AdminRefreshIdentity getIdentity(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return null;
        }
        String value = redisTemplate.opsForValue().get(REFRESH_PREFIX + refreshToken);
        return parseIdentity(value);
    }

    /**
     * 原子消费管理员刷新令牌，防止并发请求重放同一令牌。
     *
     * @param refreshToken 刷新令牌
     * @return 管理员主体，不存在或格式非法时返回 null
     * @author Henfon
     * @date 2026-09-01
     */
    public AdminRefreshIdentity consumeIdentity(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return null;
        }
        // Redis GETDEL 在服务端一次完成读取和删除，第二个并发请求只能拿到 null。
        String value = redisTemplate.opsForValue().getAndDelete(REFRESH_PREFIX + refreshToken);
        return parseIdentity(value);
    }

    /**
     * 解析 Redis 中保存的管理员主体值。
     *
     * @param value Redis 原始值
     * @return 管理员主体或 null
     * @author Henfon
     * @date 2026-09-01
     */
    private AdminRefreshIdentity parseIdentity(String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split(":", -1);
        try {
            if (parts.length == 2) {
                return new AdminRefreshIdentity(Long.valueOf(parts[1]), Long.valueOf(parts[0]));
            }
            // 兼容早期仅保存用户 ID 的令牌值，默认归属零租户。
            return new AdminRefreshIdentity(Long.valueOf(value), 0L);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 删除管理员刷新令牌。
     *
     * @param refreshToken 刷新令牌
     * @author Henfon
     * @date 2026-09-01
     */
    public void deleteRefreshToken(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            // 删除操作幂等，支持重复退出和并发刷新安全失败。
            redisTemplate.delete(REFRESH_PREFIX + refreshToken);
        }
    }
}
