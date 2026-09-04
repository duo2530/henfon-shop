package com.henfon.shop.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 会员短信验证码配置。
 *
 * @author Henfon
 * @date 2026-09-04
 */
@ConfigurationProperties(prefix = "shop.security.member-sms")
public class MemberSmsProperties {
    private long ttlSeconds = 300;
    private long cooldownSeconds = 60;
    private int maxVerifyAttempts = 5;
    private boolean exposeCode = true;

    /** 读取验证码有效期。 @return 秒数 @author Henfon @date 2026-09-04 */
    public long getTtlSeconds() { return ttlSeconds; }
    /** 设置验证码有效期。 @param ttlSeconds 秒数 @author Henfon @date 2026-09-04 */
    public void setTtlSeconds(long ttlSeconds) { this.ttlSeconds = ttlSeconds; }
    /** 读取发送冷却时间。 @return 秒数 @author Henfon @date 2026-09-04 */
    public long getCooldownSeconds() { return cooldownSeconds; }
    /** 设置发送冷却时间。 @param cooldownSeconds 秒数 @author Henfon @date 2026-09-04 */
    public void setCooldownSeconds(long cooldownSeconds) { this.cooldownSeconds = cooldownSeconds; }
    /** 读取验证码最大错误次数。 @return 最大错误次数 @author Henfon @date 2026-09-04 */
    public int getMaxVerifyAttempts() { return maxVerifyAttempts; }
    /** 设置验证码最大错误次数。 @param maxVerifyAttempts 最大错误次数 @author Henfon @date 2026-09-04 */
    public void setMaxVerifyAttempts(int maxVerifyAttempts) { this.maxVerifyAttempts = maxVerifyAttempts; }
    /** 读取是否在开发环境返回验证码。 @return 是否返回 @author Henfon @date 2026-09-04 */
    public boolean isExposeCode() { return exposeCode; }
    /** 设置是否在开发环境返回验证码。 @param exposeCode 是否返回 @author Henfon @date 2026-09-04 */
    public void setExposeCode(boolean exposeCode) { this.exposeCode = exposeCode; }
}
