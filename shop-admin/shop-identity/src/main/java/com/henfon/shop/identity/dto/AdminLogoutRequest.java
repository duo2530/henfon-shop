package com.henfon.shop.identity.dto;

/**
 * 管理员退出请求。
 *
 * @param refreshToken 刷新令牌，可为空
 * @author Henfon
 * @date 2026-09-01
 */
public record AdminLogoutRequest(String refreshToken) {
}
