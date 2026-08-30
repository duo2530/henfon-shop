package com.henfon.shop.identity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 后台会员余额积分调账请求。
 *
 * @author Henfon
 * @date 2026-08-30
 */
public record MemberAdminAdjustRequest(
        @NotNull Long pointsDelta,
        @NotNull BigDecimal balanceDelta,
        @Size(max = 500) String remark
) {
}
