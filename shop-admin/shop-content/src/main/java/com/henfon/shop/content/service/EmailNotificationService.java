package com.henfon.shop.content.service;

import com.henfon.shop.content.config.EmailNotificationProperties;
import com.henfon.shop.content.entity.ContentEmailDelivery;
import com.henfon.shop.content.mapper.ContentEmailDeliveryMapper;
import com.henfon.shop.identity.entity.MemberUser;
import com.henfon.shop.identity.mapper.MemberUserMapper;
import com.henfon.shop.identity.service.MemberEmailSender;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

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
public class EmailNotificationService implements MemberEmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final MemberUserMapper memberUserMapper;
    private final ContentEmailDeliveryMapper emailDeliveryMapper;
    private final EmailNotificationProperties properties;

    /**
     * 创建邮件通知适配器。
     *
     * @param mailSenderProvider 可选 SMTP 邮件发送器
     * @param memberUserMapper 会员数据访问对象
     * @param emailDeliveryMapper 邮件投递记录数据访问对象
     * @param properties 邮件通知配置
     * @author Henfon
     * @date 2026-09-01
     */
    public EmailNotificationService(ObjectProvider<JavaMailSender> mailSenderProvider,
                                    MemberUserMapper memberUserMapper,
                                    ContentEmailDeliveryMapper emailDeliveryMapper,
                                    EmailNotificationProperties properties) {
        this.mailSenderProvider = mailSenderProvider;
        this.memberUserMapper = memberUserMapper;
        this.emailDeliveryMapper = emailDeliveryMapper;
        this.properties = properties;
    }

    /**
     * 向指定邮箱发送认证邮件，供身份模块的密码找回流程复用。
     *
     * @param recipient 收件人邮箱
     * @param subject 邮件主题
     * @param content 邮件正文
     * @author Henfon
     * @date 2026-09-01
     */
    @Override
    public void send(String recipient, String subject, String content) {
        if (!properties.isEnabled() || !StringUtils.hasText(recipient) || !isValidEmail(recipient)) {
            return;
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.debug("邮件通知已启用但 SMTP 未配置，跳过认证邮件");
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipient.trim());
        if (StringUtils.hasText(properties.getFrom())) {
            message.setFrom(properties.getFrom().trim());
        }
        String prefix = StringUtils.hasText(properties.getSubjectPrefix())
                ? properties.getSubjectPrefix().trim() + " - " : "";
        String safeSubject = StringUtils.hasText(subject) ? subject.trim() : "会员认证通知";
        message.setSubject(prefix + safeSubject + "｜Henfon商城安全提醒");
        message.setText(formatMailContent(subject, content, null));
        try {
            mailSender.send(message);
        } catch (RuntimeException exception) {
            // 认证流程不因 SMTP 暂时不可用而暴露账号状态或阻塞请求。
            log.warn("认证邮件发送失败，recipient={}", recipient, exception);
        }
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
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipient.trim());
        if (StringUtils.hasText(properties.getFrom())) {
            message.setFrom(properties.getFrom().trim());
        }
        String prefix = StringUtils.hasText(properties.getSubjectPrefix())
                ? properties.getSubjectPrefix().trim() + " - " : "";
        String safeTitle = StringUtils.hasText(title) ? title.trim() : "订单进度更新";
        String subject = prefix + safeTitle + "｜Henfon商城提醒";
        String sendingToken = UUID.randomUUID().toString().replace("-", "");
        if (!claimDelivery(memberId, safeKey, recipient.trim(), eventType.trim().toUpperCase(), subject, sendingToken)) {
            return;
        }
        message.setSubject(subject);
        message.setText(formatMailContent(safeTitle, content, eventType));
        try {
            mailSender.send(message);
        } catch (RuntimeException exception) {
            // 发送失败允许后续事件重试，且不影响站内通知和交易状态推进。
            markDeliveryFailed(memberId, safeKey, sendingToken, exception);
            log.warn("业务邮件发送失败，memberId={}, eventType={}", memberId, eventType, exception);
            return;
        }
        try {
            emailDeliveryMapper.markSent(memberId, safeKey, sendingToken);
        } catch (RuntimeException exception) {
            // 邮件已交给 SMTP 后仅记录投递状态异常，避免误将已发送邮件标记为可重试而造成重复发送。
            log.warn("记录邮件发送成功状态异常，memberId={}, eventType={}", memberId, eventType, exception);
        }
    }

    /**
     * 在 MySQL 中创建并原子抢占邮件投递记录，保证重启和多实例场景下的幂等性。
     *
     * @param memberId 会员ID
     * @param dedupeKey 事件幂等键
     * @param recipient 收件人邮箱
     * @param eventType 事件类型
     * @param subject 邮件主题
     * @param sendingToken 本次发送占用令牌
     * @return 是否成功抢占发送资格
     * @author Henfon
     * @date 2026-09-03
     */
    private boolean claimDelivery(Long memberId, String dedupeKey, String recipient,
                                  String eventType, String subject, String sendingToken) {
        try {
            ContentEmailDelivery delivery = new ContentEmailDelivery();
            delivery.setMemberId(memberId);
            delivery.setDedupeKey(dedupeKey);
            delivery.setRecipient(recipient);
            delivery.setEventType(eventType);
            delivery.setSubject(subject);
            emailDeliveryMapper.insertIgnore(delivery);
            ContentEmailDelivery existing = emailDeliveryMapper.selectByDedupeKey(memberId, dedupeKey);
            if (existing != null && Integer.valueOf(1).equals(existing.getStatus())) {
                return false;
            }
            return emailDeliveryMapper.claimSending(memberId, dedupeKey, sendingToken) == 1;
        } catch (RuntimeException exception) {
            // 投递记录不可用时跳过邮件，避免邮件幂等故障阻断交易事件消费。
            log.warn("初始化邮件投递记录失败，memberId={}, dedupeKey={}", memberId, dedupeKey, exception);
            return false;
        }
    }

    /**
     * 将发送异常写入 MySQL 并释放发送占用，等待后续轮询重试。
     *
     * @param memberId 会员ID
     * @param dedupeKey 事件幂等键
     * @param sendingToken 本次发送占用令牌
     * @param exception 发送异常
     * @author Henfon
     * @date 2026-09-03
     */
    private void markDeliveryFailed(Long memberId, String dedupeKey, String sendingToken, RuntimeException exception) {
        try {
            String message = exception.getMessage();
            emailDeliveryMapper.markFailed(memberId, dedupeKey, sendingToken,
                    message == null ? exception.getClass().getSimpleName() : message.substring(0, Math.min(message.length(), 1000)));
        } catch (RuntimeException persistException) {
            log.warn("记录邮件发送失败状态异常，memberId={}, dedupeKey={}", memberId, dedupeKey, persistException);
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
     * 组装统一的邮件正文，补充品牌抬头、事件提示和服务 footer，提升纯文本邮件的可读性。
     *
     * @param title 邮件标题
     * @param content 业务正文
     * @param eventType 业务事件类型，认证邮件可为空
     * @return 格式化后的邮件正文
     * @author Henfon
     * @date 2026-09-03
     */
    private String formatMailContent(String title, String content, String eventType) {
        String safeTitle = StringUtils.hasText(title) ? title.trim() : "会员服务通知";
        String safeContent = StringUtils.hasText(content)
                ? content.trim() : "您有一封新的会员服务通知，请登录商城查看详情。";
        StringBuilder body = new StringBuilder(256);
        body.append("您好！\n\n")
                .append("Henfon 商城为您带来一条服务提醒\n")
                .append("━━━━━━━━━━━━━━━━━━━━\n")
                .append("【").append(safeTitle).append("】\n")
                .append("━━━━━━━━━━━━━━━━━━━━\n\n")
                .append(safeContent);
        if (StringUtils.hasText(eventType)) {
            body.append("\n\n").append(eventPrompt(eventType));
        }
        if (StringUtils.hasText(eventType)) {
            body.append("\n\n如需查看完整详情，请登录 Henfon 商城「我的订单」。");
        } else {
            body.append("\n\n为保障账户安全，请勿将邮件中的链接或验证码转发给他人。");
        }
        body.append("\n本邮件由系统自动发送，请勿直接回复。")
                .append("\n\n—— Henfon 商城");
        return body.toString();
    }

    /**
     * 获取不同业务事件对应的下一步提示。
     *
     * @param eventType 业务事件类型
     * @return 面向会员的操作提示
     * @author Henfon
     * @date 2026-09-03
     */
    private String eventPrompt(String eventType) {
        String normalized = StringUtils.hasText(eventType) ? eventType.trim().toUpperCase() : "";
        return switch (normalized) {
            case "PAYMENT_SUCCEEDED" -> "温馨提示：订单已进入备货流程，请留意后续发货通知。";
            case "ORDER_SHIPPED" -> "温馨提示：包裹运输状态会持续更新，请留意收货并及时查验。";
            case "REFUND_SUCCEEDED" -> "温馨提示：退款到账时间以支付渠道处理进度为准，请留意账户余额变化。";
            case "AFTER_SALE_CREATED" -> "温馨提示：商家审核后我们会第一时间通知您，请耐心等待。";
            case "AFTER_SALE_APPROVED" -> "温馨提示：请按照售后指引完成后续操作，以便尽快处理。";
            case "AFTER_SALE_RETURN_RECEIVED" -> "温馨提示：退货已入库，退款处理完成后会再次通知您。";
            case "AFTER_SALE_REJECTED" -> "温馨提示：如有疑问，可登录商城查看审核备注或联系客服。";
            case "AFTER_SALE_CANCELLED" -> "温馨提示：本次售后已结束，如需帮助欢迎联系客服。";
            case "AFTER_SALE_COMPLETED" -> "温馨提示：售后流程已完成，感谢您的理解与支持。";
            default -> "温馨提示：您可以登录商城查看订单的最新处理进度。";
        };
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
