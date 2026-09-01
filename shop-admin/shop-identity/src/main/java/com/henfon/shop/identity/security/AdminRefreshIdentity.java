package com.henfon.shop.identity.security;

/**
 * 管理员刷新令牌关联的租户和用户标识。
 *
 * @param userId 管理员用户 ID
 * @param tenantId 租户 ID
 * @author Henfon
 * @date 2026-09-01
 */
public record AdminRefreshIdentity(Long userId, Long tenantId) {
}
