package com.henfon.shop.identity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.henfon.shop.common.exception.BusinessException;
import com.henfon.shop.identity.dto.AdminLoginRequest;
import com.henfon.shop.identity.dto.AdminLoginResponse;
import com.henfon.shop.identity.dto.AdminCurrentUserResponse;
import com.henfon.shop.identity.dto.AdminPasswordChangeRequest;
import com.henfon.shop.identity.dto.AdminProfileUpdateRequest;
import com.henfon.shop.identity.dto.AdminRefreshRequest;
import com.henfon.shop.identity.entity.SysUser;
import com.henfon.shop.identity.mapper.SysUserMapper;
import com.henfon.shop.identity.mapper.SysUserRoleMapper;
import com.henfon.shop.identity.security.AuthenticatedUser;
import com.henfon.shop.identity.security.JwtTokenService;
import com.henfon.shop.identity.security.AdminRefreshIdentity;
import com.henfon.shop.identity.security.AdminTokenStore;
import com.henfon.shop.identity.security.LoginRateLimiter;
import com.henfon.shop.identity.security.LoginFailureTracker;
import com.henfon.shop.identity.security.LoginCaptchaStore;
import com.henfon.shop.integration.storage.ImageReferenceResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端登录应用服务。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Service
public class AdminAuthService {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AuditLogService auditLogService;
    private final LoginRateLimiter loginRateLimiter;
    private final LoginFailureTracker loginFailureTracker;
    private final LoginCaptchaStore loginCaptchaStore;
    private final AdminTokenStore adminTokenStore;
    private final ImageReferenceResolver imageReferenceResolver;

    /**
     * 创建管理端登录服务。
     *
     * @param sysUserMapper 用户数据访问对象
     * @param sysUserRoleMapper 用户角色数据访问对象
     * @param passwordEncoder 密码编码器
     * @param jwtTokenService JWT 服务
     * @param auditLogService 登录审计服务
     * @param loginRateLimiter IP 登录限流器
     * @param loginFailureTracker 账号失败锁定跟踪器
     * @param loginCaptchaStore 图形验证码存储
     * @param adminTokenStore 管理员刷新令牌存储
     * @param imageReferenceResolver 媒体引用解析器
     * @author Henfon
     * @date 2026-08-29
     */
    public AdminAuthService(SysUserMapper sysUserMapper, SysUserRoleMapper sysUserRoleMapper,
                            PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService,
                            AuditLogService auditLogService, LoginRateLimiter loginRateLimiter,
                            LoginFailureTracker loginFailureTracker, LoginCaptchaStore loginCaptchaStore,
                            AdminTokenStore adminTokenStore, ImageReferenceResolver imageReferenceResolver) {
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.auditLogService = auditLogService;
        this.loginRateLimiter = loginRateLimiter;
        this.loginFailureTracker = loginFailureTracker;
        this.loginCaptchaStore = loginCaptchaStore;
        this.adminTokenStore = adminTokenStore;
        this.imageReferenceResolver = imageReferenceResolver;
    }

    /**
     * 校验管理员账号并签发 JWT。
     *
     * @param request 登录请求
     * @param loginIp 客户端IP
     * @return 登录响应
     * @author Henfon
     * @date 2026-08-29
     */
    @Transactional
    public AdminLoginResponse login(AdminLoginRequest request, String loginIp) {
        // 图形验证码是登录链路的第一道闸，校验一次即消费，后续校验不应绕过。
        if (!loginCaptchaStore.verify(request.captchaId(), request.captchaCode())) {
            auditLogService.recordLogin(null, request.username(), 0, loginIp, "验证码错误或已过期");
            throw new BusinessException("AUTH_CAPTCHA_INVALID", "验证码错误或已过期");
        }
        if (!loginRateLimiter.allow(loginIp)) {
            auditLogService.recordLogin(null, request.username(), 0, loginIp, "登录尝试过于频繁");
            throw new BusinessException("AUTH_RATE_LIMITED", "登录尝试过于频繁，请稍后再试");
        }
        long tenantId = request.tenantId() == null ? 0L : request.tenantId();
        if (loginFailureTracker.isLocked(tenantId, request.username())) {
            auditLogService.recordLogin(null, request.username(), 0, loginIp, "账号因连续登录失败暂时锁定");
            throw new BusinessException("AUTH_LOCKED", "登录失败次数过多，请15分钟后再试");
        }
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, tenantId)
                .eq(SysUser::getUsername, request.username())
                .last("LIMIT 1"));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            long failureCount = loginFailureTracker.recordFailure(tenantId, request.username());
            auditLogService.recordLogin(user == null ? null : user.getId(), request.username(), 0, loginIp, "用户名或密码错误");
            if (failureCount >= 5) {
                throw new BusinessException("AUTH_LOCKED", "登录失败次数过多，请15分钟后再试");
            }
            throw new BusinessException("AUTH_INVALID", "用户名或密码错误");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            auditLogService.recordLogin(user.getId(), request.username(), 0, loginIp, "账号已被停用");
            throw new BusinessException("AUTH_DISABLED", "账号已被停用");
        }
        List<String> permissions = sysUserRoleMapper.selectPermissionCodesByUserId(user.getId());
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(loginIp);
        sysUserMapper.updateById(user);
        String token = jwtTokenService.generate(user, permissions);
        auditLogService.recordLogin(user.getId(), request.username(), 1, loginIp, null);
        loginRateLimiter.reset(loginIp);
        loginFailureTracker.reset(tenantId, request.username());
        return new AdminLoginResponse(token, jwtTokenService.getExpirationSeconds(),
                adminTokenStore.createRefreshToken(user.getId(), user.getTenantId()), user.getId(),
                user.getTenantId(), user.getUsername(), user.getRealName(), avatarAccessUrl(user), permissions);
    }

    /**
     * 使用刷新令牌轮换管理员访问令牌。
     *
     * @param request 刷新请求
     * @return 新的登录令牌
     * @author Henfon
     * @date 2026-09-01
     */
    @Transactional
    public AdminLoginResponse refresh(AdminRefreshRequest request) {
        AdminRefreshIdentity identity = adminTokenStore.consumeIdentity(request.refreshToken());
        if (identity == null) {
            throw new BusinessException("AUTH_REFRESH_INVALID", "刷新令牌无效或已过期");
        }
        SysUser user = sysUserMapper.selectById(identity.userId());
        if (user == null || !java.util.Objects.equals(user.getTenantId(), identity.tenantId())) {
            throw new BusinessException("AUTH_REFRESH_INVALID", "刷新令牌无效或已过期");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException("AUTH_DISABLED", "账号已被停用");
        }
        List<String> permissions = sysUserRoleMapper.selectPermissionCodesByUserId(user.getId());
        // 旋转前令牌，确保被重放的旧刷新令牌无法再次换取访问令牌。
        String accessToken = jwtTokenService.generate(user, permissions);
        return new AdminLoginResponse(accessToken, jwtTokenService.getExpirationSeconds(),
                adminTokenStore.createRefreshToken(user.getId(), user.getTenantId()), user.getId(),
                user.getTenantId(), user.getUsername(), user.getRealName(), avatarAccessUrl(user), permissions);
    }

    /**
     * 查询当前登录管理员的最新资料，避免头像和姓名停留在旧令牌快照。
     *
     * @param authentication 当前认证信息
     * @return 当前管理员资料
     * @author Henfon
     * @date 2026-09-01
     * @description 从数据库读取实时头像，管理员编辑后刷新页面即可同步右上角资料。
     */
    @Transactional(readOnly = true)
    public AdminCurrentUserResponse currentUser(Authentication authentication) {
        SysUser user = requireAuthenticatedAdmin(authentication);
        // 权限以认证主体为准，资料字段以数据库最新值为准。
        return new AdminCurrentUserResponse(user.getId(), user.getTenantId(), user.getUsername(),
                user.getRealName(), avatarAccessUrl(user), currentPermissions(authentication));
    }

    /**
     * 修改当前登录管理员本人的资料，用于个人中心自助更换头像。
     *
     * @param authentication 当前认证信息
     * @param request 资料修改请求
     * @return 更新后的资料
     * @author Henfon
     * @date 2026-09-17
     */
    @Transactional
    public AdminCurrentUserResponse updateProfile(Authentication authentication, AdminProfileUpdateRequest request) {
        SysUser user = requireAuthenticatedAdmin(authentication);
        if (request.avatarUrl() != null) {
            // 头像历史上存的是 24 小时过期的预签名地址，写入前统一归一化成对象键。
            user.setAvatarUrl(imageReferenceResolver.normalizeReference(request.avatarUrl()));
        }
        if (request.nickname() != null) {
            user.setNickname(request.nickname());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }
        if (request.email() != null) {
            user.setEmail(request.email());
        }
        sysUserMapper.updateById(user);
        return new AdminCurrentUserResponse(user.getId(), user.getTenantId(), user.getUsername(),
                user.getRealName(), avatarAccessUrl(user), currentPermissions(authentication));
    }

    /**
     * 修改当前登录管理员密码。
     *
     * @param authentication 当前认证信息
     * @param request 密码修改请求
     * @author Henfon
     * @date 2026-08-31
     */
    @Transactional
    public void changePassword(Authentication authentication, AdminPasswordChangeRequest request) {
        SysUser user = requireAuthenticatedAdmin(authentication);
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException("AUTH_PASSWORD_INVALID", "当前密码错误");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException("AUTH_PASSWORD_UNCHANGED", "新密码不能与当前密码相同");
        }
        // 更新密码时间，便于安全审计和后续强制定期改密策略读取。
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
    }

    /**
     * 校验当前认证主体为启用的管理员账号并读取最新记录。
     *
     * @param authentication 当前认证信息
     * @return 管理员用户
     * @author Henfon
     * @date 2026-09-17
     */
    private SysUser requireAuthenticatedAdmin(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)
                || !"ADMIN".equalsIgnoreCase(principal.userType())) {
            throw new BusinessException("AUTH_REQUIRED", "请先登录管理员账号");
        }
        SysUser user = sysUserMapper.selectById(principal.userId());
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException("AUTH_USER_NOT_FOUND", "管理员账号不存在或已停用");
        }
        return user;
    }

    /**
     * 读取认证主体已加载的权限列表。
     *
     * @param authentication 当前认证信息
     * @return 权限编码列表
     * @author Henfon
     * @date 2026-09-17
     */
    private List<String> currentPermissions(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).permissions();
    }

    /**
     * 将头像对象键重签为当前有效的访问地址。
     *
     * @param user 管理员用户
     * @return 可访问的头像地址，未设置头像时返回原值
     * @author Henfon
     * @date 2026-09-17
     */
    private String avatarAccessUrl(SysUser user) {
        return imageReferenceResolver.accessUrl(user.getAvatarUrl());
    }
}
