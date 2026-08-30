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
                                List<String> permissions, String userType, String tokenId) {

    /**
     * 创建管理员主体的兼容构造方法。
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param username 用户名
     * @param permissions 权限编码
     * @author Henfon
     * @date 2026-08-30
     */
    public AuthenticatedUser(Long userId, Long tenantId, String username, List<String> permissions) {
        this(userId, tenantId, username, permissions, "ADMIN", null);
    }

    /**
     * 创建带用户类型的认证主体兼容构造方法。
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param username 用户名
     * @param permissions 权限编码
     * @param userType 用户类型
     * @author Henfon
     * @date 2026-08-30
     */
    public AuthenticatedUser(Long userId, Long tenantId, String username, List<String> permissions,
                             String userType) {
        this(userId, tenantId, username, permissions, userType, null);
    }
}
