package com.henfon.shop.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * 修改当前登录管理员本人资料的请求。
 *
 * <p>只允许改头像与联系方式，部门、状态、角色等管理字段不在此接口范围内，
 * 因此个人中心场景不需要 system:user:update 权限。</p>
 *
 * @param nickname 昵称，null 表示不改动
 * @param phone 手机号，null 表示不改动
 * @param email 邮箱，null 表示不改动
 * @param avatarUrl 头像地址，null 表示不改动；服务端会归一化成对象键
 * @author Henfon
 * @date 2026-09-17
 */
public record AdminProfileUpdateRequest(
        @Size(max = 64) String nickname,
        @Size(max = 32) String phone,
        @Email @Size(max = 128) String email,
        @Size(max = 1024) String avatarUrl
) {
}
