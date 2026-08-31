package com.henfon.shop.identity.controller;

import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.identity.dto.AdminLoginRequest;
import com.henfon.shop.identity.dto.AdminLoginResponse;
import com.henfon.shop.identity.dto.AdminPasswordChangeRequest;
import com.henfon.shop.identity.security.AuthenticatedUser;
import com.henfon.shop.identity.security.MemberTokenStore;
import com.henfon.shop.identity.entity.SysMenu;
import com.henfon.shop.identity.mapper.SysUserRoleMapper;
import com.henfon.shop.identity.service.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端认证接口。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final MemberTokenStore memberTokenStore;

    /**
     * 创建认证控制器。
     *
     * @param adminAuthService 管理端认证服务
     * @param sysUserRoleMapper 用户角色数据访问对象
     * @param memberTokenStore 访问令牌黑名单存储
     * @author Henfon
     * @date 2026-08-29
     */
    public AdminAuthController(AdminAuthService adminAuthService, SysUserRoleMapper sysUserRoleMapper,
                               MemberTokenStore memberTokenStore) {
        this.adminAuthService = adminAuthService;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.memberTokenStore = memberTokenStore;
    }

    /**
     * 管理员登录。
     *
     * @param request 登录请求
     * @param servletRequest HTTP 请求
     * @return 登录令牌
     * @author Henfon
     * @date 2026-08-29
     */
    @PostMapping("/login")
    public ApiResponse<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request,
                                                  HttpServletRequest servletRequest) {
        return ApiResponse.success(adminAuthService.login(request, servletRequest.getRemoteAddr()), requestId());
    }

    /**
     * 修改当前管理员密码。
     *
     * @param request 密码修改请求
     * @param authentication 当前认证信息
     * @return 空响应
     * @author Henfon
     * @date 2026-08-31
     */
    @PostMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody AdminPasswordChangeRequest request,
                                             Authentication authentication) {
        adminAuthService.changePassword(authentication, request);
        return ApiResponse.success(requestId());
    }

    /**
     * 注销当前管理员并吊销访问令牌。
     *
     * @param authentication 当前认证信息
     * @return 空响应
     * @author Henfon
     * @date 2026-08-31
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            // 黑名单 TTL 与 JWT 自然过期时间一致，重复退出保持幂等。
            memberTokenStore.revokeAccessToken(user.tokenId());
        }
        return ApiResponse.success(requestId());
    }

    /**
     * 获取当前登录用户信息。
     *
     * @param authentication 当前认证信息
     * @return 当前用户主体
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/me")
    public ApiResponse<AuthenticatedUser> me(Authentication authentication) {
        return ApiResponse.success((AuthenticatedUser) authentication.getPrincipal(), requestId());
    }

    /**
     * 获取当前用户可见菜单，用于管理端动态构建路由。
     *
     * @param authentication 当前认证信息
     * @return 可见菜单列表
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/menus")
    public ApiResponse<List<SysMenu>> menus(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ApiResponse.success(sysUserRoleMapper.selectMenusByUserId(user.userId()), requestId());
    }

    /**
     * 获取请求链路标识。
     *
     * @return 请求链路标识
     * @author Henfon
     * @date 2026-08-29
     */
    private String requestId() {
        return MDC.get("requestId");
    }
}
