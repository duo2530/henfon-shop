package com.henfon.shop.identity.security;

import java.util.List;

/**
 * 当前登录用户的轻量认证主体。
 *
 * @param userId 用户ID
 * @param tenantId 租户/组织ID
 * @param username 用户名
 * @param permissions 权限编码
 * @author Henfon
 * @date 2026-08-29
 */
public record AuthenticatedUser(Long userId, Long tenantId, String username,
                                List<String> permissions) {
}
