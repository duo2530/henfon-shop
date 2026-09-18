package com.henfon.shop.identity.dto;

/**
 * 管理端登录图形验证码响应。
 *
 * @param captchaId 验证码标识，登录时原样回传
 * @param imageBase64 验证码图片，data URL 形式的 PNG
 * @author Henfon
 * @date 2026-09-18
 */
public record LoginCaptchaResponse(String captchaId, String imageBase64) {
}
