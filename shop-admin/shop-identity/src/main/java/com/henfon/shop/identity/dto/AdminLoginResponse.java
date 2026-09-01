package com.henfon.shop.identity.dto;

import java.util.List;

/**
 * 管理员登录响应。
 *
 * @param accessToken 访问令牌
 * @param expiresInSeconds 令牌有效期（秒）
 * @param refreshToken 刷新令牌
 * @param tenantId 租户 ID
 * @param userId 用户ID
 * @param username 登录用户名
 * @param realName 真实姓名
 * @param avatarUrl 头像地址
 * @param permissions 权限编码列表
 * @author Henfon
 * @date 2026-09-01
 */
public record AdminLoginResponse(String accessToken, long expiresInSeconds, String refreshToken,
                                 Long userId, Long tenantId, String username, String realName,
                                 String avatarUrl, List<String> permissions) {
}
