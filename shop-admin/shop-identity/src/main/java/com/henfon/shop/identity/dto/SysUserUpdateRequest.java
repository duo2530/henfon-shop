package com.henfon.shop.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * 修改后台用户请求。
 *
 * @param realName 真实姓名
 * @param nickname 昵称
 * @param phone 手机号
 * @param email 邮箱
 * @param avatarUrl 头像地址
 * @param deptId 部门ID
 * @param status 状态
 * @param remark 备注
 * @author Henfon
 * @date 2026-08-29
 */
public record SysUserUpdateRequest(
        @Size(max = 64) String realName,
        @Size(max = 64) String nickname,
        @Size(max = 32) String phone,
        @Email @Size(max = 128) String email,
        @Size(max = 512) String avatarUrl,
        Long deptId,
        Integer status,
        @Size(max = 500) String remark
) {
}
