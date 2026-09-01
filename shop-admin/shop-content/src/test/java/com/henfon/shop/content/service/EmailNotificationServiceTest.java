package com.henfon.shop.content.service;

import com.henfon.shop.content.config.EmailNotificationProperties;
import com.henfon.shop.identity.entity.MemberUser;
import com.henfon.shop.identity.mapper.MemberUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会员事件邮件通知适配器测试。
 *
 * @author Henfon
 * @date 2026-09-01
 */
class EmailNotificationServiceTest {

    /**
     * 校验未启用邮件时安全降级且不会访问 SMTP。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldSkipWhenEmailDisabled() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        MemberUserMapper memberMapper = mock(MemberUserMapper.class);
        EmailNotificationProperties properties = new EmailNotificationProperties();
        EmailNotificationService service = new EmailNotificationService(provider, memberMapper, properties);

        service.sendIfConfigured(1L, "PAYMENT_SUCCEEDED", "支付成功", "订单已支付", "evt-1");

        verify(provider, never()).getIfAvailable();
        verify(memberMapper, never()).selectById(1L);
    }

    /**
     * 校验已配置 SMTP 时支付事件可以发送邮件且同一事件只发送一次。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldSendConfiguredEventOnce() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        MemberUserMapper memberMapper = mock(MemberUserMapper.class);
        EmailNotificationProperties properties = new EmailNotificationProperties();
        properties.setEnabled(true);
        properties.setFrom("noreply@example.com");
        properties.setSubjectPrefix("商城");
        MemberUser member = new MemberUser();
        member.setEmail("buyer@example.com");
        when(provider.getIfAvailable()).thenReturn(sender);
        when(memberMapper.selectById(1L)).thenReturn(member);
        EmailNotificationService service = new EmailNotificationService(provider, memberMapper, properties);

        service.sendIfConfigured(1L, "PAYMENT_SUCCEEDED", "支付成功", "订单已支付", "evt-1");
        service.sendIfConfigured(1L, "PAYMENT_SUCCEEDED", "支付成功", "订单已支付", "evt-1");

        verify(sender, times(1)).send(any(SimpleMailMessage.class));
    }

    /**
     * 校验不支持的事件不会发送邮件。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldSkipUnsupportedEvent() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        EmailNotificationProperties properties = new EmailNotificationProperties();
        properties.setEnabled(true);
        EmailNotificationService service = new EmailNotificationService(provider, mock(MemberUserMapper.class), properties);

        service.sendIfConfigured(1L, "COUPON_GRANTED", "优惠券", "已领取", "evt-1");

        verify(provider, never()).getIfAvailable();
    }
}
