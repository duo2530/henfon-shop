package com.henfon.shop.identity.service;

import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.identity.dto.AdminPasswordChangeRequest;
import com.henfon.shop.identity.dto.AdminRefreshRequest;
import com.henfon.shop.identity.security.AdminRefreshIdentity;
import com.henfon.shop.identity.entity.SysUser;
import com.henfon.shop.identity.mapper.SysUserMapper;
import com.henfon.shop.identity.mapper.SysUserRoleMapper;
import com.henfon.shop.identity.security.AuthenticatedUser;
import com.henfon.shop.identity.security.JwtTokenService;
import com.henfon.shop.identity.security.AdminTokenStore;
import com.henfon.shop.identity.security.LoginFailureTracker;
import com.henfon.shop.identity.security.LoginRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理员密码修改服务测试。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@ExtendWith(MockitoExtension.class)
class AdminAuthServicePasswordTest {

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
    private AdminTokenStore adminTokenStore;

    private AdminAuthService service;

    /**
     * 初始化管理员认证服务测试对象。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @BeforeEach
    void setUp() {
        service = new AdminAuthService(sysUserMapper, sysUserRoleMapper, passwordEncoder,
                jwtTokenService, auditLogService, loginRateLimiter, loginFailureTracker, adminTokenStore);
    }

    /**
     * 验证正确旧密码可以更新哈希并记录更新时间。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldChangePasswordForAuthenticatedAdmin() {
        SysUser user = activeUser();
        when(sysUserMapper.selectById(7L)).thenReturn(user);
        when(passwordEncoder.matches("old-pass", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("new-pass", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("new-pass")).thenReturn("new-hash");

        service.changePassword(authentication(), new AdminPasswordChangeRequest("old-pass", "new-pass"));

        assertEquals("new-hash", user.getPasswordHash());
        assertNotNull(user.getPasswordUpdatedAt());
        verify(sysUserMapper).updateById(user);
    }

    /**
     * 验证旧密码错误时拒绝修改并保留原密码。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldRejectWrongCurrentPassword() {
        SysUser user = activeUser();
        when(sysUserMapper.selectById(7L)).thenReturn(user);
        when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.changePassword(authentication(), new AdminPasswordChangeRequest("wrong", "new-pass")));

        assertEquals("AUTH_PASSWORD_INVALID", exception.getCode());
    }

    /**
     * 验证新密码与旧密码相同时拒绝修改。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    @Test
    void shouldRejectUnchangedPassword() {
        SysUser user = activeUser();
        when(sysUserMapper.selectById(7L)).thenReturn(user);
        when(passwordEncoder.matches("same-pass", "old-hash")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.changePassword(authentication(), new AdminPasswordChangeRequest("same-pass", "same-pass")));

        assertEquals("AUTH_PASSWORD_UNCHANGED", exception.getCode());
    }

    /**
     * 验证有效刷新令牌会轮换访问令牌和刷新令牌。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldRotateAdminRefreshToken() {
        SysUser user = activeUser();
        user.setTenantId(3L);
        when(adminTokenStore.consumeIdentity("old-refresh")).thenReturn(new AdminRefreshIdentity(7L, 3L));
        when(sysUserMapper.selectById(7L)).thenReturn(user);
        when(sysUserRoleMapper.selectPermissionCodesByUserId(7L)).thenReturn(List.of("system:user:query"));
        when(jwtTokenService.generate(user, List.of("system:user:query"))).thenReturn("new-access");
        when(jwtTokenService.getExpirationSeconds()).thenReturn(3600L);
        when(adminTokenStore.createRefreshToken(7L, 3L)).thenReturn("new-refresh");

        var response = service.refresh(new AdminRefreshRequest("old-refresh"));

        assertEquals("new-access", response.accessToken());
        assertEquals("new-refresh", response.refreshToken());
        assertEquals(3L, response.tenantId());
        verify(adminTokenStore).consumeIdentity("old-refresh");
    }

    /**
     * 验证无效刷新令牌不会查询用户并返回稳定错误码。
     *
     * @author Henfon
     * @date 2026-09-01
     */
    @Test
    void shouldRejectInvalidAdminRefreshToken() {
        when(adminTokenStore.consumeIdentity("invalid")).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.refresh(new AdminRefreshRequest("invalid")));

        assertEquals("AUTH_REFRESH_INVALID", exception.getCode());
    }

    /**
     * 构造启用状态的管理员测试用户。
     *
     * @return 管理员测试用户
     * @author Henfon
     * @date 2026-08-31
     */
    private SysUser activeUser() {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setTenantId(0L);
        user.setStatus(1);
        user.setPasswordHash("old-hash");
        return user;
    }

    /**
     * 构造管理员认证主体。
     *
     * @return Spring Security 认证对象
     * @author Henfon
     * @date 2026-08-31
     */
    private UsernamePasswordAuthenticationToken authentication() {
        AuthenticatedUser user = new AuthenticatedUser(7L, 0L, "admin", List.of(), "ADMIN", "token-id");
        return new UsernamePasswordAuthenticationToken(user, "token", List.of());
    }
}
