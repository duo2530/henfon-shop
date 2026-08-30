package com.henfon.shop.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * 后台会员资料更新请求。
 *
 * @author Henfon
 * @date 2026-08-30
 */
public record MemberAdminUpdateRequest(
        @Size(max = 128) String nickname,
        @Size(max = 32) String phone,
        @Email @Size(max = 128) String email,
        @Size(max = 32) String memberLevel,
        @Size(max = 1024) String avatarUrl,
        @Size(max = 500) String remark
) {
}
