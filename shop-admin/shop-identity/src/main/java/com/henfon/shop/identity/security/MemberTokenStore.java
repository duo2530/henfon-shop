package com.henfon.shop.identity.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
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
    private static final String PASSWORD_RESET_PREFIX = "shop:member:password-reset:";
    private static final String PASSWORD_RESET_COOLDOWN_PREFIX = "shop:member:password-reset:cooldown:";
    private static final String SMS_CODE_PREFIX = "shop:member:sms-code:";
    private static final String SMS_COOLDOWN_PREFIX = "shop:member:sms-cooldown:";
    private static final String SMS_ATTEMPTS_PREFIX = "shop:member:sms-attempts:";
    private static final Duration REFRESH_TTL = Duration.ofDays(30);
    /** Redis 5 兼容的一次性读取并删除脚本，避免依赖 Redis 6 的 GETDEL 命令。 */
    private static final DefaultRedisScript<String> GET_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            "local value = redis.call('get', KEYS[1]); "
                    + "if value then redis.call('del', KEYS[1]); end; return value;", String.class);
    /** 短信验证码校验脚本：成功一次性消费，错误累计达到阈值后失效。 */
    private static final DefaultRedisScript<Long> VERIFY_SMS_SCRIPT = new DefaultRedisScript<>(
            "local expected = redis.call('get', KEYS[1]); "
                    + "if not expected then return -1; end; "
                    + "if expected == ARGV[1] then redis.call('del', KEYS[1]); redis.call('del', KEYS[2]); return 1; end; "
                    + "local attempts = redis.call('incr', KEYS[2]); "
                    + "if attempts == 1 then redis.call('expire', KEYS[2], ARGV[2]); end; "
                    + "if attempts >= tonumber(ARGV[3]) then redis.call('del', KEYS[1]); redis.call('del', KEYS[2]); return -2; end; "
                    + "return 0;", Long.class);

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

    /**
     * 创建并保存会员邮箱找回密码的一次性令牌。
     *
     * @param memberId 会员ID
     * @param ttl 令牌有效期
     * @return 随机重置令牌
     * @author Henfon
     * @date 2026-09-01
     */
    public String createPasswordResetToken(Long memberId, Duration ttl) {
        if (memberId == null || ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("会员ID和令牌有效期必须有效");
        }
        // 令牌本身不携带会员信息，Redis TTL 到期后自动清理，降低泄漏后的可利用窗口。
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(PASSWORD_RESET_PREFIX + token, String.valueOf(memberId), ttl);
        return token;
    }

    /**
     * 原子消费会员邮箱找回密码令牌。
     *
     * @param token 重置令牌
     * @return 会员ID；令牌不存在、过期或已消费时返回 null
     * @author Henfon
     * @date 2026-09-01
     */
    public Long consumePasswordResetToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        // Lua 脚本在 Redis 服务端原子执行，兼容开发环境 Redis 5，两个并发请求最多一个取得会员ID。
        String value = redisTemplate.execute(GET_AND_DELETE_SCRIPT,
                Collections.singletonList(PASSWORD_RESET_PREFIX + token.trim()));
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
     * 尝试获取找回密码申请冷却锁，避免同一邮箱被高频触发邮件。
     *
     * @param email 规范化邮箱
     * @param cooldown 冷却时间
     * @return 是否允许本次申请
     * @author Henfon
     * @date 2026-09-01
     */
    public boolean tryAcquirePasswordResetCooldown(String email, Duration cooldown) {
        if (email == null || email.isBlank() || cooldown == null || cooldown.isZero() || cooldown.isNegative()) {
            return false;
        }
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                PASSWORD_RESET_COOLDOWN_PREFIX + email.trim().toLowerCase(java.util.Locale.ROOT), "1", cooldown);
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * 保存会员短信验证码。
     *
     * @param phone 手机号
     * @param code 验证码
     * @param ttl 有效期
     * @author Henfon
     * @date 2026-09-04
     */
    public void saveSmsCode(String phone, String code, Duration ttl) {
        // 验证码仅保存于 Redis，过期后自动清理，避免落库保存敏感信息。
        redisTemplate.opsForValue().set(SMS_CODE_PREFIX + phone.trim(), code, ttl);
    }

    /**
     * 原子消费会员短信验证码。
     *
     * @param phone 手机号
     * @return 验证码，不存在或已消费时返回 null
     * @author Henfon
     * @date 2026-09-04
     */
    public String consumeSmsCode(String phone) {
        if (phone == null || phone.isBlank()) return null;
        // Lua 脚本保证验证码只能成功校验一次，防止并发重放。
        return redisTemplate.execute(GET_AND_DELETE_SCRIPT,
                Collections.singletonList(SMS_CODE_PREFIX + phone.trim()));
    }

    /**
     * 原子校验会员短信验证码并累计错误次数。
     *
     * @param phone 手机号
     * @param code 待校验验证码
     * @param maxAttempts 最大错误次数
     * @param attemptTtl 错误计数有效期
     * @return 1成功，0验证码错误，-1验证码不存在或过期，-2错误次数超限
     * @author Henfon
     * @date 2026-09-04
     */
    public int verifySmsCode(String phone, String code, int maxAttempts, Duration attemptTtl) {
        if (phone == null || phone.isBlank() || code == null || code.isBlank()
                || maxAttempts <= 0 || attemptTtl == null || attemptTtl.isZero() || attemptTtl.isNegative()) {
            return -1;
        }
        // Redis Lua 保证并发请求下校验、计数和失效操作原子执行，避免绕过错误次数限制。
        Long result = redisTemplate.execute(VERIFY_SMS_SCRIPT,
                java.util.Arrays.asList(SMS_CODE_PREFIX + phone.trim(), SMS_ATTEMPTS_PREFIX + phone.trim()),
                code.trim(), String.valueOf(Math.max(1, attemptTtl.getSeconds())), String.valueOf(maxAttempts));
        return result == null ? -1 : result.intValue();
    }

    /**
     * 尝试获取短信发送冷却锁。
     *
     * @param phone 手机号
     * @param cooldown 冷却时间
     * @return 是否允许发送
     * @author Henfon
     * @date 2026-09-04
     */
    public boolean tryAcquireSmsCooldown(String phone, Duration cooldown) {
        if (phone == null || phone.isBlank() || cooldown == null || cooldown.isZero() || cooldown.isNegative()) return false;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(SMS_COOLDOWN_PREFIX + phone.trim(), "1", cooldown);
        return Boolean.TRUE.equals(acquired);
    }
}
