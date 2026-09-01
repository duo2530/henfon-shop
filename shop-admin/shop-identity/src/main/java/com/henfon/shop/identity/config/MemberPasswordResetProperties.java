package com.henfon.shop.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 会员邮箱找回密码配置。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@ConfigurationProperties(prefix = "shop.security.member-password-reset")
public class MemberPasswordResetProperties {

    private String url = "http://localhost:3000/";
    private long ttlMinutes = 15;
    private long cooldownSeconds = 60;

    /**
     * 读取密码重置页面地址。
     *
     * @return 页面地址
     * @author Henfon
     * @date 2026-09-01
     */
    public String getUrl() {
        return url;
    }

    /**
     * 设置密码重置页面地址。
     *
     * @param url 页面地址
     * @author Henfon
     * @date 2026-09-01
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * 读取令牌有效期分钟数。
     *
     * @return 有效期分钟数
     * @author Henfon
     * @date 2026-09-01
     */
    public long getTtlMinutes() {
        return ttlMinutes;
    }

    /**
     * 设置令牌有效期分钟数。
     *
     * @param ttlMinutes 有效期分钟数
     * @author Henfon
     * @date 2026-09-01
     */
    public void setTtlMinutes(long ttlMinutes) {
        this.ttlMinutes = ttlMinutes;
    }

    /**
     * 读取同一邮箱重复申请冷却时间。
     *
     * @return 冷却秒数
     * @author Henfon
     * @date 2026-09-01
     */
    public long getCooldownSeconds() {
        return cooldownSeconds;
    }

    /**
     * 设置同一邮箱重复申请冷却时间。
     *
     * @param cooldownSeconds 冷却秒数
     * @author Henfon
     * @date 2026-09-01
     */
    public void setCooldownSeconds(long cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }
}
