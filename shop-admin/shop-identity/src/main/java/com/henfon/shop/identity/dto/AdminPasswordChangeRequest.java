package com.henfon.shop.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员修改密码请求。
 *
 * @param oldPassword 当前密码
 * @param newPassword 新密码
 * @author Henfon
 * @date 2026-08-31
 */
public record AdminPasswordChangeRequest(
        @NotBlank(message = "当前密码不能为空") String oldPassword,
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 64, message = "新密码长度必须为6到64位") String newPassword) {
}
