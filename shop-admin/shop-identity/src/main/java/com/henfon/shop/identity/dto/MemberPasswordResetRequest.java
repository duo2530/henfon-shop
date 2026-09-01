package com.henfon.shop.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 会员邮箱找回密码申请请求。
 *
 * @param email 会员绑定邮箱
 * @author Henfon
 * @date 2026-09-01
 */
public record MemberPasswordResetRequest(
        @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") @Size(max = 128) String email) {
}
