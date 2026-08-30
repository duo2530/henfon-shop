package com.henfon.shop.identity.dto;

import java.math.BigDecimal;

/**
 * 门户会员登录响应。
 *
 * @param accessToken 访问令牌
 * @param expiresInSeconds 令牌有效期
 * @param refreshToken 刷新令牌
 * @param memberId 会员ID
 * @param username 登录用户名
 * @param nickname 会员昵称
 * @param memberLevel 会员等级
 * @param phone 手机号
 * @param email 邮箱
 * @param avatarUrl 头像地址
 * @param points 积分
 * @param balance 余额
 * @author Henfon
 * @date 2026-08-30
 */
public record MemberLoginResponse(String accessToken, long expiresInSeconds, String refreshToken, Long memberId,
                                  String username, String nickname, String memberLevel,
                                  Long points, BigDecimal balance, String phone, String email, String avatarUrl) {
}
