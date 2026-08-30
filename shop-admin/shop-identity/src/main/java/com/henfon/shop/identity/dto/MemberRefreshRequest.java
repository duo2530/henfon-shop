package com.henfon.shop.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 门户会员刷新令牌请求。
 *
 * @param refreshToken 刷新令牌
 * @author Henfon
 * @date 2026-08-30
 */
public record MemberRefreshRequest(@NotBlank(message = "刷新令牌不能为空") String refreshToken) {
}
