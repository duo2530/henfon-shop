package com.henfon.shop.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 会员邮箱找回密码确认请求。
 *
 * @param token 一次性重置令牌
 * @param newPassword 新密码
 * @author Henfon
 * @date 2026-09-01
 */
public record MemberPasswordResetConfirmRequest(
        @NotBlank(message = "重置令牌不能为空") @Size(max = 128) String token,
        @NotBlank(message = "新密码不能为空") @Size(min = 6, max = 64, message = "新密码长度必须为6到64位") String newPassword) {
}
