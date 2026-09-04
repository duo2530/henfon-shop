package com.henfon.shop.identity.service;

import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.identity.config.MemberSmsProperties;
import com.henfon.shop.identity.dto.MemberSmsLoginRequest;
import com.henfon.shop.identity.entity.MemberUser;
import com.henfon.shop.identity.mapper.MemberUserMapper;
import com.henfon.shop.identity.security.MemberTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 会员短信验证码安全策略测试。
 *
 * @author Henfon
 * @date 2026-09-04
 */
@ExtendWith(MockitoExtension.class)
class MemberSmsServiceTest {
    @Mock private MemberUserMapper memberUserMapper;
    @Mock private MemberTokenStore tokenStore;
    @Mock private MemberAuthService memberAuthService;
    private MemberSmsService service;

    /** 初始化短信服务测试对象。 @author Henfon @date 2026-09-04 */
    @BeforeEach
    void setUp() {
        MemberSmsProperties properties = new MemberSmsProperties();
        properties.setMaxVerifyAttempts(3);
        service = new MemberSmsService(memberUserMapper, tokenStore, memberAuthService, properties);
    }

    /** 验证错误次数达到阈值时统一拒绝登录。 @author Henfon @date 2026-09-04 */
    @Test
    void shouldRejectWhenVerificationAttemptsExceeded() {
        when(tokenStore.verifySmsCode(any(), any(), any(Integer.class), any(Duration.class))).thenReturn(-2);
        assertThrows(BusinessException.class,
                () -> service.login(new MemberSmsLoginRequest("13800138000", "000000")));
    }

    /** 验证验证码过期或不存在时拒绝登录。 @author Henfon @date 2026-09-04 */
    @Test
    void shouldRejectExpiredCode() {
        when(tokenStore.verifySmsCode(any(), any(), any(Integer.class), any(Duration.class))).thenReturn(-1);
        assertThrows(BusinessException.class,
                () -> service.login(new MemberSmsLoginRequest("13800138000", "000000")));
    }
}
