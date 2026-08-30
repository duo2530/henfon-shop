package com.henfon.shop.identity.dto;

/**
 * 门户会员退出请求。
 *
 * @param refreshToken 刷新令牌，可为空
 * @author Henfon
 * @date 2026-08-30
 */
public record MemberLogoutRequest(String refreshToken) {
}
