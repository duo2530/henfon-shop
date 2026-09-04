package com.henfon.shop.identity.controller;

import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.identity.dto.MemberLoginRequest;
import com.henfon.shop.identity.dto.MemberLoginResponse;
import com.henfon.shop.identity.dto.MemberRegisterRequest;
import com.henfon.shop.identity.dto.MemberRefreshRequest;
import com.henfon.shop.identity.dto.MemberLogoutRequest;
import com.henfon.shop.identity.dto.MemberPasswordResetConfirmRequest;
import com.henfon.shop.identity.dto.MemberSmsCodeRequest;
import com.henfon.shop.identity.dto.MemberSmsCodeResponse;
import com.henfon.shop.identity.dto.MemberSmsLoginRequest;
import com.henfon.shop.identity.dto.MemberPasswordResetRequest;
import com.henfon.shop.identity.service.MemberAuthService;
import com.henfon.shop.identity.service.MemberPasswordResetService;
import com.henfon.shop.identity.service.MemberSmsService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

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
    private final MemberPasswordResetService memberPasswordResetService;
    private final MemberSmsService memberSmsService;

    /**
     * 创建会员认证控制器。
     *
     * @param memberAuthService 会员认证服务
     * @author Henfon
     * @date 2026-08-30
     */
    public MemberAuthController(MemberAuthService memberAuthService, MemberPasswordResetService memberPasswordResetService,
                                MemberSmsService memberSmsService) {
        this.memberAuthService = memberAuthService;
        this.memberPasswordResetService = memberPasswordResetService;
        this.memberSmsService = memberSmsService;
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
     * 发送会员短信验证码。
     *
     * @param request 手机号请求
     * @return 验证码发送结果
     * @author Henfon
     * @date 2026-09-04
     */
    @PostMapping("/sms/send")
    public ApiResponse<MemberSmsCodeResponse> sendSmsCode(@Valid @RequestBody MemberSmsCodeRequest request) {
        return ApiResponse.success(memberSmsService.sendCode(request.phone()), MDC.get("requestId"));
    }

    /**
     * 使用短信验证码登录会员。
     *
     * @param request 短信登录请求
     * @return 登录令牌和会员摘要
     * @author Henfon
     * @date 2026-09-04
     */
    @PostMapping("/sms/login")
    public ApiResponse<MemberLoginResponse> smsLogin(@Valid @RequestBody MemberSmsLoginRequest request) {
        return ApiResponse.success(memberSmsService.login(request), MDC.get("requestId"));
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

    /**
     * 刷新会员访问令牌。
     *
     * @param request 刷新请求
     * @return 新令牌
     * @author Henfon
     * @date 2026-08-30
     */
    @PostMapping("/refresh")
    public ApiResponse<MemberLoginResponse> refresh(@Valid @RequestBody MemberRefreshRequest request) {
        // 刷新接口不依赖访问令牌，仅校验 Redis 中的一次性刷新令牌。
        return ApiResponse.success(memberAuthService.refresh(request), MDC.get("requestId"));
    }

    /**
     * 退出会员登录并使令牌失效。
     *
     * @param request 退出请求
     * @param authentication 当前认证信息
     * @return 空响应
     * @author Henfon
     * @date 2026-08-30
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) MemberLogoutRequest request,
                                    Authentication authentication) {
        // 请求体可为空，服务层仍会吊销当前访问令牌。
        memberAuthService.logout(authentication, request == null ? null : request.refreshToken());
        return ApiResponse.success(MDC.get("requestId"));
    }

    /**
     * 申请会员邮箱找回密码。
     *
     * @param request 找回密码申请
     * @return 统一空响应，避免暴露邮箱是否注册
     * @author Henfon
     * @date 2026-09-01
     */
    @PostMapping("/password-reset/request")
    public ApiResponse<Void> requestPasswordReset(@Valid @RequestBody MemberPasswordResetRequest request) {
        memberPasswordResetService.requestReset(request);
        return ApiResponse.success(MDC.get("requestId"));
    }

    /**
     * 使用邮箱中的一次性令牌设置新密码。
     *
     * @param request 重置确认请求
     * @return 空响应
     * @author Henfon
     * @date 2026-09-01
     */
    @PostMapping("/password-reset/confirm")
    public ApiResponse<Void> confirmPasswordReset(@Valid @RequestBody MemberPasswordResetConfirmRequest request) {
        memberPasswordResetService.confirmReset(request);
        return ApiResponse.success(MDC.get("requestId"));
    }
}
