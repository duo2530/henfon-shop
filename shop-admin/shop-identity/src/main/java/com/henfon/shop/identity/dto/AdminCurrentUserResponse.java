package com.henfon.shop.identity.dto;

import java.util.List;

/**
 * 当前登录管理员资料响应。
 *
 * @param userId 用户ID
 * @param tenantId 租户ID
 * @param username 登录用户名
 * @param realName 真实姓名
 * @param avatarUrl 头像地址
 * @param permissions 权限编码列表
 * @author Henfon
 * @date 2026-09-01
 */
public record AdminCurrentUserResponse(Long userId, Long tenantId, String username, String realName,
                                       String avatarUrl, List<String> permissions) {
}
