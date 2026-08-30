package com.henfon.shop.identity.controller;

import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.identity.dto.MemberLoginRequest;
import com.henfon.shop.identity.dto.MemberLoginResponse;
import com.henfon.shop.identity.dto.MemberRegisterRequest;
import com.henfon.shop.identity.service.MemberAuthService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门户会员认证接口。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@RestController
@RequestMapping("/api/portal/auth")
public class MemberAuthController {

    private final MemberAuthService memberAuthService;

    /**
     * 创建会员认证控制器。
     *
     * @param memberAuthService 会员认证服务
     * @author Henfon
     * @date 2026-08-30
     */
    public MemberAuthController(MemberAuthService memberAuthService) {
        this.memberAuthService = memberAuthService;
    }

    /**
     * 会员登录。
     *
     * @param request 登录请求
     * @return 登录令牌和会员摘要
     * @author Henfon
     * @date 2026-08-30
     */
    @PostMapping("/login")
    public ApiResponse<MemberLoginResponse> login(@Valid @RequestBody MemberLoginRequest request) {
        return ApiResponse.success(memberAuthService.login(request), MDC.get("requestId"));
    }

    /**
     * 会员注册。
     *
     * @param request 注册请求
     * @return 注册后的登录令牌和会员摘要
     * @author Henfon
     * @date 2026-08-30
     */
    @PostMapping("/register")
    public ApiResponse<MemberLoginResponse> register(@Valid @RequestBody MemberRegisterRequest request) {
        return ApiResponse.success(memberAuthService.register(request), MDC.get("requestId"));
    }
}
