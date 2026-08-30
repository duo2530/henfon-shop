package com.henfon.shop.identity.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 门户会员令牌 Redis 存储，负责刷新令牌和访问令牌失效控制。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Service
public class MemberTokenStore {

    private static final String REFRESH_PREFIX = "shop:member:refresh:";
    private static final String REVOKED_PREFIX = "shop:jwt:revoked:";
    private static final Duration REFRESH_TTL = Duration.ofDays(30);

    private final StringRedisTemplate redisTemplate;
    private final Duration accessTokenTtl;

    /**
     * 创建会员令牌存储。
     *
     * @param redisTemplate Redis 字符串模板
     * @param jwtProperties JWT 配置
     * @author Henfon
     * @date 2026-08-30
     */
    public MemberTokenStore(StringRedisTemplate redisTemplate, JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.accessTokenTtl = Duration.ofSeconds(jwtProperties.getExpirationSeconds());
    }

    /**
     * 创建并保存刷新令牌。
     *
     * @param memberId 会员ID
     * @return 刷新令牌
     * @author Henfon
     * @date 2026-08-30
     */
    public String createRefreshToken(Long memberId) {
        // 使用不可预测的随机值作为 Redis 键，避免暴露会员编号。
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(REFRESH_PREFIX + token, String.valueOf(memberId), REFRESH_TTL);
        return token;
    }

    /**
     * 获取刷新令牌对应的会员ID。
     *
     * @param refreshToken 刷新令牌
     * @return 会员ID，不存在时返回 null
     * @author Henfon
     * @date 2026-08-30
     */
    public Long getMemberId(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return null;
        }
        // Redis 中只保存令牌与会员的映射，不保存访问令牌明文或会员敏感信息。
        String value = redisTemplate.opsForValue().get(REFRESH_PREFIX + refreshToken);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 删除刷新令牌。
     *
     * @param refreshToken 刷新令牌
     * @author Henfon
     * @date 2026-08-30
     */
    public void deleteRefreshToken(String refreshToken) {
        // 删除操作天然幂等，允许重复退出或重复刷新请求安全执行。
        if (refreshToken != null && !refreshToken.isBlank()) {
            redisTemplate.delete(REFRESH_PREFIX + refreshToken);
        }
    }

    /**
     * 将访问令牌加入黑名单直到其自然过期。
     *
     * @param tokenId JWT ID
     * @author Henfon
     * @date 2026-08-30
     */
    public void revokeAccessToken(String tokenId) {
        // 黑名单 TTL 与访问令牌有效期一致，避免无期限占用 Redis。
        if (tokenId != null && !tokenId.isBlank()) {
            redisTemplate.opsForValue().set(REVOKED_PREFIX + tokenId, "1", accessTokenTtl);
        }
    }

    /**
     * 判断访问令牌是否已失效。
     *
     * @param tokenId JWT ID
     * @return 是否已加入黑名单
     * @author Henfon
     * @date 2026-08-30
     */
    public boolean isAccessTokenRevoked(String tokenId) {
        return tokenId != null && !tokenId.isBlank()
                && Boolean.TRUE.equals(redisTemplate.hasKey(REVOKED_PREFIX + tokenId));
    }
}
