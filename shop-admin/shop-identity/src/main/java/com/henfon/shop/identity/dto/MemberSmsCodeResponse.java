package com.henfon.shop.identity.dto;

/** 短信验证码发送响应。 @author Henfon @date 2026-09-04 */
public record MemberSmsCodeResponse(String phone, long expiresInSeconds, String verificationCode) { }
