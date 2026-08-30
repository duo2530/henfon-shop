package com.henfon.shop.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 门户会员登录请求。
 *
 * @param account 用户名、手机号或邮箱
 * @param password 登录密码
 * @author Henfon
 * @date 2026-08-30
 */
public record MemberLoginRequest(@NotBlank(message = "登录账号不能为空") String account,
                                 @NotBlank(message = "登录密码不能为空") String password) {
}
