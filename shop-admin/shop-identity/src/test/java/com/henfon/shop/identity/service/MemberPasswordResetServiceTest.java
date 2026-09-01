package com.henfon.shop.identity.service;

import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.identity.config.MemberPasswordResetProperties;
import com.henfon.shop.identity.dto.MemberPasswordResetConfirmRequest;
import com.henfon.shop.identity.dto.MemberPasswordResetRequest;
import com.henfon.shop.identity.entity.MemberUser;
import com.henfon.shop.identity.mapper.MemberUserMapper;
import com.henfon.shop.identity.security.MemberTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会员邮箱找回密码服务测试。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@ExtendWith(MockitoExtension.class)
class MemberPasswordResetServiceTest {

    @Mock
    private MemberUserMapper memberUserMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private MemberTokenStore memberTokenStore;
    @Mock
    private ObjectProvider<MemberEmailSender> emailSenderProvider;
    @Mock
    private MemberEmailSender emailSender;

    private MemberPasswordResetService service;

    /**
     * 初始化找回密码服务测试对象。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @BeforeEach
    void setUp() {
        MemberPasswordResetProperties properties = new MemberPasswordResetProperties();
        properties.setUrl("https://shop.example/reset");
        properties.setTtlMinutes(15);
        properties.setCooldownSeconds(60);
        service = new MemberPasswordResetService(memberUserMapper, passwordEncoder, memberTokenStore,
                emailSenderProvider, properties);
    }

    /**
     * 验证已注册邮箱可以收到带短时令牌的重置邮件。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldSendResetMailForActiveMember() {
        MemberUser member = activeMember();
        when(emailSenderProvider.getIfAvailable()).thenReturn(emailSender);
        when(memberUserMapper.selectOne(any())).thenReturn(member);
        when(memberTokenStore.tryAcquirePasswordResetCooldown("buyer@example.com", Duration.ofSeconds(60)))
                .thenReturn(true);
        when(memberTokenStore.createPasswordResetToken(9L, Duration.ofMinutes(15))).thenReturn("abc-token");

        service.requestReset(new MemberPasswordResetRequest(" buyer@example.com "));

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(org.mockito.ArgumentMatchers.eq("buyer@example.com"),
                org.mockito.ArgumentMatchers.eq("会员登录密码重置"), content.capture());
        org.junit.jupiter.api.Assertions.assertTrue(content.getValue().contains("resetToken=abc-token"));
    }

    /**
     * 验证不存在的邮箱只返回统一结果且不会发送邮件。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldNotRevealUnknownEmail() {
        when(emailSenderProvider.getIfAvailable()).thenReturn(emailSender);
        when(memberUserMapper.selectOne(any())).thenReturn(null);

        service.requestReset(new MemberPasswordResetRequest("unknown@example.com"));

        verify(emailSender, never()).send(any(), any(), any());
    }

    /**
     * 验证重置令牌只能消费一次并更新密码哈希。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldConsumeTokenAndUpdatePasswordOnce() {
        MemberUser member = activeMember();
        when(memberTokenStore.consumePasswordResetToken("one-time-token")).thenReturn(9L).thenReturn(null);
        when(memberUserMapper.selectById(9L)).thenReturn(member);
        when(passwordEncoder.encode("new-pass")).thenReturn("new-hash");

        service.confirmReset(new MemberPasswordResetConfirmRequest("one-time-token", "new-pass"));

        assertEquals("new-hash", member.getPasswordHash());
        verify(memberUserMapper).updateById(member);
        assertThrows(BusinessException.class,
                () -> service.confirmReset(new MemberPasswordResetConfirmRequest("one-time-token", "new-pass")));
    }

    /**
     * 创建一条可用于测试的正常会员记录。
     *
     * @return 正常会员
     * @author Henfon
     * @date 2026-09-01
     */
    private MemberUser activeMember() {
        MemberUser member = new MemberUser();
        member.setId(9L);
        member.setTenantId(0L);
        member.setEmail("buyer@example.com");
        member.setStatus(1);
        return member;
    }
}
