package com.henfon.shop.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理员登录请求。
 *
 * @param username 登录用户名
 * @param password 登录密码
 * @param captchaId 图形验证码标识，取自验证码接口
 * @param captchaCode 用户填写的图形验证码，不区分大小写
 * @param tenantId 租户/组织ID，可选
 * @author Henfon
 * @date 2026-08-29
 */
public record AdminLoginRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password,
        @NotBlank(message = "请先获取验证码") String captchaId,
        @NotBlank(message = "验证码不能为空") String captchaCode,
        Long tenantId) {
}
