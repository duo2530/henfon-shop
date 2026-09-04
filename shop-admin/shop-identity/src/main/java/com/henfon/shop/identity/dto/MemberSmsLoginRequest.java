package com.henfon.shop.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 会员短信登录请求。 @author Henfon @date 2026-09-04 */
public record MemberSmsLoginRequest(
        @NotBlank(message = "手机号不能为空") @Pattern(regexp = "^\\+?[0-9]{7,20}$", message = "手机号格式不正确") String phone,
        @NotBlank(message = "验证码不能为空") @Pattern(regexp = "^[0-9]{4,8}$", message = "验证码格式不正确") String verificationCode) { }
