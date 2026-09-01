package com.henfon.shop.content.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 会员事件邮件通知配置。
 *
 * <p>邮件功能默认关闭；仅在配置 SMTP 主机、发件人和启用开关后发送，
 * 未配置时由通知服务安全降级为站内通知。</p>
 *
 * @author Henfon
 * @date 2026-09-01
 */
@ConfigurationProperties(prefix = "shop.notification.email")
public class EmailNotificationProperties {

    private boolean enabled;
    private String from;
    private String subjectPrefix = "Henfon商城";

    /**
     * 读取邮件通知启用开关。
     *
     * @return 是否启用
     * @author Henfon
     * @date 2026-09-01
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置邮件通知启用开关。
     *
     * @param enabled 是否启用
     * @author Henfon
     * @date 2026-09-01
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 读取邮件发件人地址。
     *
     * @return 发件人地址
     * @author Henfon
     * @date 2026-09-01
     */
    public String getFrom() {
        return from;
    }

    /**
     * 设置邮件发件人地址。
     *
     * @param from 发件人地址
     * @author Henfon
     * @date 2026-09-01
     */
    public void setFrom(String from) {
        this.from = from;
    }

    /**
     * 读取邮件主题前缀。
     *
     * @return 主题前缀
     * @author Henfon
     * @date 2026-09-01
     */
    public String getSubjectPrefix() {
        return subjectPrefix;
    }

    /**
     * 设置邮件主题前缀。
     *
     * @param subjectPrefix 主题前缀
     * @author Henfon
     * @date 2026-09-01
     */
    public void setSubjectPrefix(String subjectPrefix) {
        this.subjectPrefix = subjectPrefix;
    }
}
