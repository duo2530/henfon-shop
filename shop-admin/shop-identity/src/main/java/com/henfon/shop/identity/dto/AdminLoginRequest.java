package com.henfon.shop.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理员登录请求。
 *
 * @param username 登录用户名
 * @param password 登录密码
 * @param tenantId 租户/组织ID，可选
 * @author Henfon
 * @date 2026-08-29
 */
public record AdminLoginRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password,
        Long tenantId) {
}
