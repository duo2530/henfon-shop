package com.henfon.shop.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * 门户会员资料更新请求。
 *
 * @author Henfon
 * @date 2026-09-04
 */
public record MemberProfileUpdateRequest(
        @Size(max = 128) String nickname,
        @Size(max = 32) String phone,
        @Email @Size(max = 128) String email,
        @Size(max = 1024) String avatarUrl
) {
}
