package com.henfon.shop.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 后台新增会员请求。
 *
 * @param nickname 会员昵称
 * @param username 登录用户名，可选，未填写时使用手机号或系统生成值
 * @param phone 手机号
 * @param email 邮箱
 * @param memberLevel 会员等级
 * @param status 账户状态，1 正常，0 冻结
 * @param avatarUrl 头像地址
 * @param remark 备注
 * @author Henfon
 * @date 2026-09-01
 */
public record MemberAdminCreateRequest(
        @NotBlank(message = "会员昵称不能为空") @Size(max = 64) String nickname,
        @Size(max = 64) String username,
        @Size(max = 32) String phone,
        @Email @Size(max = 128) String email,
        @Size(max = 32) String memberLevel,
        Integer status,
        @Size(max = 1024) String avatarUrl,
        @Size(max = 500) String remark
) {
}
