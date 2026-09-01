package com.henfon.shop.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理员刷新令牌请求。
 *
 * @param refreshToken 刷新令牌
 * @author Henfon
 * @date 2026-09-01
 */
public record AdminRefreshRequest(@NotBlank(message = "刷新令牌不能为空") String refreshToken) {
}
