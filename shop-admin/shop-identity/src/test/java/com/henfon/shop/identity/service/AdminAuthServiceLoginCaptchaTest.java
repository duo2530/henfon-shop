package com.henfon.shop.identity.service;

import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.identity.dto.AdminLoginRequest;
import com.henfon.shop.identity.mapper.SysUserMapper;
import com.henfon.shop.identity.mapper.SysUserRoleMapper;
import com.henfon.shop.identity.security.AdminTokenStore;
import com.henfon.shop.identity.security.JwtTokenService;
import com.henfon.shop.identity.security.LoginCaptchaStore;
import com.henfon.shop.identity.security.LoginFailureTracker;
import com.henfon.shop.identity.security.LoginRateLimiter;
import com.henfon.shop.integration.storage.ImageReferenceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理员登录图形验证码校验测试。
 *
 * @author Henfon
 * @date 2026-09-18
 */
@ExtendWith(MockitoExtension.class)
class AdminAuthServiceLoginCaptchaTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private LoginRateLimiter loginRateLimiter;
    @Mock
    private LoginFailureTracker loginFailureTracker;
    @Mock
    private LoginCaptchaStore loginCaptchaStore;
    @Mock
    private AdminTokenStore adminTokenStore;
    @Mock
    private ImageReferenceResolver imageReferenceResolver;

    private AdminAuthService service;

    /**
     * 初始化管理员登录服务测试对象。
     *
     * @author Henfon
     * @date 2026-09-18
     */
    @BeforeEach
    void setUp() {
        service = new AdminAuthService(sysUserMapper, sysUserRoleMapper, passwordEncoder, jwtTokenService,
                auditLogService, loginRateLimiter, loginFailureTracker, loginCaptchaStore, adminTokenStore,
                imageReferenceResolver);
    }

    /**
     * 验证验证码错误时直接拒绝，不查询账号也不消耗登录失败次数。
     *
     * @author Henfon
     * @date 2026-09-18
     */
    @Test
    void shouldRejectLoginBeforeTouchingAccountWhenCaptchaWrong() {
        when(loginCaptchaStore.verify("captcha-id", "0000")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.login(loginRequest("0000"), "127.0.0.1"));

        assertEquals("AUTH_CAPTCHA_INVALID", exception.getCode());
        verify(sysUserMapper, never()).selectOne(any());
        verify(loginFailureTracker, never()).recordFailure(0L, "admin");
    }

    /**
     * 验证码通过后仍走原有账号与密码校验流程。
     *
     * @author Henfon
     * @date 2026-09-18
     */
    @Test
    void shouldKeepOriginalAccountChecksAfterCaptchaPassed() {
        when(loginCaptchaStore.verify("captcha-id", "1234")).thenReturn(true);
        when(loginRateLimiter.allow("127.0.0.1")).thenReturn(true);
        when(loginFailureTracker.isLocked(0L, "admin")).thenReturn(false);
        when(sysUserMapper.selectOne(any())).thenReturn(null);
        when(loginFailureTracker.recordFailure(0L, "admin")).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.login(loginRequest("1234"), "127.0.0.1"));

        assertEquals("AUTH_INVALID", exception.getCode());
    }

    /**
     * 构造带验证码的管理员登录请求。
     *
     * @param captchaCode 验证码明文
     * @return 登录请求
     * @author Henfon
     * @date 2026-09-18
     */
    private AdminLoginRequest loginRequest(String captchaCode) {
        return new AdminLoginRequest("admin", "123456", "captcha-id", captchaCode, null);
    }
}
