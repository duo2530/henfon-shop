package com.henfon.shop.identity.service;

/**
 * 会员认证场景的邮件发送抽象。
 *
 * <p>身份模块只依赖该接口，具体 SMTP 适配由内容通知模块提供，避免业务模块之间形成反向依赖。</p>
 *
 * @author Henfon
 * @date 2026-09-01
 */
public interface MemberEmailSender {

    /**
     * 发送一封会员认证邮件。
     *
     * @param recipient 收件人邮箱
     * @param subject 邮件主题
     * @param content 邮件正文
     * @author Henfon
     * @date 2026-09-01
     */
    void send(String recipient, String subject, String content);
}
