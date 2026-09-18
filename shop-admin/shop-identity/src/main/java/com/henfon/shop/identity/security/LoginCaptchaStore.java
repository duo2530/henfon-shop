package com.henfon.shop.identity.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * 管理端登录图形验证码 Redis 存储，负责一次性校验和过期控制。
 *
 * @author Henfon
 * @date 2026-09-18
 */
@Service
public class LoginCaptchaStore {

    private static final String CAPTCHA_PREFIX = "shop:admin:captcha:";
    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);
    /** Redis 5 兼容的一次性读取并删除脚本，避免依赖 Redis 6 的 GETDEL 命令。 */
    private static final DefaultRedisScript<String> GET_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            "local value = redis.call('get', KEYS[1]); "
                    + "if value then redis.call('del', KEYS[1]); end; return value;", String.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * 创建验证码存储。
     *
     * @param redisTemplate Redis 字符串模板
     * @author Henfon
     * @date 2026-09-18
     */
    public LoginCaptchaStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 保存验证码明文并返回其标识。
     *
     * @param code 验证码明文
     * @return 验证码标识
     * @author Henfon
     * @date 2026-09-18
     */
    public String create(String code) {
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        // 验证码只存 Redis 并自带过期时间，校验成功或失败后立即删除，不落库。
        redisTemplate.opsForValue().set(CAPTCHA_PREFIX + captchaId, code, CAPTCHA_TTL);
        return captchaId;
    }

    /**
     * 原子校验并消费验证码，同一验证码只能使用一次。
     *
     * @param captchaId 验证码标识
     * @param captchaCode 用户填写内容
     * @return 是否校验通过
     * @author Henfon
     * @date 2026-09-18
     */
    public boolean verify(String captchaId, String captchaCode) {
        if (captchaId == null || captchaId.isBlank() || captchaCode == null || captchaCode.isBlank()) {
            return false;
        }
        // 脚本内读取并删除，并发重放同一验证码只能有一个请求拿到值。
        String expected = redisTemplate.execute(GET_AND_DELETE_SCRIPT,
                Collections.singletonList(CAPTCHA_PREFIX + captchaId));
        return expected != null && expected.equalsIgnoreCase(captchaCode.trim());
    }
}
