package com.henfon.shop.content.service;

import com.henfon.shop.content.config.EmailNotificationProperties;
import com.henfon.shop.identity.entity.MemberUser;
import com.henfon.shop.identity.mapper.MemberUserMapper;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会员业务事件邮件通知适配器。
 *
 * <p>适配器通过 Spring Mail 的 SMTP 配置发送纯文本邮件。SMTP 未配置、会员没有邮箱或发送失败时，
 * 仅记录日志并保留站内通知结果，不阻断 RocketMQ 消费和交易主链路。</p>
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final MemberUserMapper memberUserMapper;
    private final EmailNotificationProperties properties;
    private final Set<String> sentDedupeKeys = ConcurrentHashMap.newKeySet();

    /**
     * 创建邮件通知适配器。
     *
     * @param mailSenderProvider 可选 SMTP 邮件发送器
     * @param memberUserMapper 会员数据访问对象
     * @param properties 邮件通知配置
     * @author Henfon
     * @date 2026-09-01
     */
    public EmailNotificationService(ObjectProvider<JavaMailSender> mailSenderProvider,
                                    MemberUserMapper memberUserMapper,
                                    EmailNotificationProperties properties) {
        this.mailSenderProvider = mailSenderProvider;
        this.memberUserMapper = memberUserMapper;
        this.properties = properties;
    }

    /**
     * 按业务事件幂等发送邮件通知。
     *
     * @param memberId 会员ID
     * @param eventType 事件类型
     * @param title 通知标题
     * @param content 通知正文
     * @param dedupeKey 事件幂等键
     * @author Henfon
     * @date 2026-09-01
     */
    public void sendIfConfigured(Long memberId, String eventType, String title,
                                 String content, String dedupeKey) {
        if (!properties.isEnabled() || memberId == null || !isSupportedEvent(eventType)) {
            return;
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.debug("邮件通知已启用但 SMTP 未配置，跳过发送，eventType={}", eventType);
            return;
        }
        MemberUser member;
        try {
            member = memberUserMapper.selectById(memberId);
        } catch (RuntimeException exception) {
            log.warn("读取会员邮箱失败，memberId={}", memberId, exception);
            return;
        }
        String recipient = member == null ? null : member.getEmail();
        if (!StringUtils.hasText(recipient) || !isValidEmail(recipient)) {
            log.debug("会员未配置有效邮箱，跳过业务邮件，memberId={}", memberId);
            return;
        }
        String safeKey = StringUtils.hasText(dedupeKey)
                ? memberId + ":" + dedupeKey.trim() : memberId + ":" + eventType + ":" + title;
        if (!sentDedupeKeys.add(safeKey)) {
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipient.trim());
        if (StringUtils.hasText(properties.getFrom())) {
            message.setFrom(properties.getFrom().trim());
        }
        String prefix = StringUtils.hasText(properties.getSubjectPrefix())
                ? properties.getSubjectPrefix().trim() + " - " : "";
        message.setSubject(prefix + (StringUtils.hasText(title) ? title.trim() : "订单进度更新"));
        message.setText(StringUtils.hasText(content) ? content.trim() : "您的订单有新的进度更新，请登录商城查看详情。");
        try {
            mailSender.send(message);
        } catch (RuntimeException exception) {
            // 发送失败允许后续事件重试，且不影响站内通知和交易状态推进。
            sentDedupeKeys.remove(safeKey);
            log.warn("业务邮件发送失败，memberId={}, eventType={}", memberId, eventType, exception);
        }
    }

    /**
     * 判断是否属于当前支持的会员业务事件。
     *
     * @param eventType 事件类型
     * @return 是否支持邮件通知
     * @author Henfon
     * @date 2026-09-01
     */
    private boolean isSupportedEvent(String eventType) {
        if (!StringUtils.hasText(eventType)) {
            return false;
        }
        String normalized = eventType.trim().toUpperCase();
        return "PAYMENT_SUCCEEDED".equals(normalized)
                || "ORDER_SHIPPED".equals(normalized)
                || "REFUND_SUCCEEDED".equals(normalized)
                || normalized.startsWith("AFTER_SALE_");
    }

    /**
     * 校验邮箱地址格式，避免把明显非法地址交给 SMTP 服务。
     *
     * @param email 原始邮箱
     * @return 是否有效
     * @author Henfon
     * @date 2026-09-01
     */
    private boolean isValidEmail(String email) {
        try {
            InternetAddress address = new InternetAddress(email.trim());
            address.validate();
            return true;
        } catch (AddressException exception) {
            return false;
        }
    }
}
