package com.henfon.shop.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 发送会员短信验证码请求。 @author Henfon @date 2026-09-04 */
public record MemberSmsCodeRequest(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^\\+?[0-9]{7,20}$", message = "手机号格式不正确") String phone) { }
