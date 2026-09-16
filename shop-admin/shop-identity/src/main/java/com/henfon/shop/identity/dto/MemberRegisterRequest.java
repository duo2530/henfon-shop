package com.henfon.shop.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 门户会员注册请求。
 *
 * @param username 登录用户名
 * @param password 登录密码
 * @param nickname 会员昵称
 * @param phone 手机号，选填
 * @param email 邮箱，必填；既是登录凭证也是密码找回的唯一通道
 * @author Henfon
 * @date 2026-08-30
 */
public record MemberRegisterRequest(@NotBlank(message = "用户名不能为空") @Size(max = 64) String username,
                                    @NotBlank(message = "密码不能为空") @Size(min = 6, max = 64) String password,
                                    @NotBlank(message = "昵称不能为空") @Size(max = 64) String nickname,
                                    @Size(max = 32) String phone,
                                    @NotBlank(message = "邮箱不能为空") @Size(max = 128) String email) {
}
