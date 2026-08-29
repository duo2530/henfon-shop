package com.henfon.shop.identity.controller;

import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.identity.dto.AdminLoginRequest;
import com.henfon.shop.identity.dto.AdminLoginResponse;
import com.henfon.shop.identity.security.AuthenticatedUser;
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

    /**
     * 创建认证控制器。
     *
     * @param adminAuthService 管理端认证服务
     * @author Henfon
     * @date 2026-08-29
     */
    public AdminAuthController(AdminAuthService adminAuthService, SysUserRoleMapper sysUserRoleMapper) {
        this.adminAuthService = adminAuthService;
        this.sysUserRoleMapper = sysUserRoleMapper;
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
