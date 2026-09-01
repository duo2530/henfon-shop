package com.henfon.shop.marketing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 后台优惠券保存请求。
 *
 * @author Henfon
 * @date 2026-08-30
 */
public record MarketingCouponSaveRequest(
        Long id,
        @NotBlank @Size(max = 64) String couponCode,
        @NotBlank @Size(max = 200) String couponTitle,
        @NotNull @DecimalMin("0.00") BigDecimal discountAmount,
        @NotNull @DecimalMin("0.00") BigDecimal minSpend,
        @Size(max = 64) String categoryCode,
        @Size(max = 64) String tag,
        @Size(max = 500) String description,
        @NotNull Integer totalQuantity,
        @NotNull @Min(1) @Max(999999) Integer perMemberLimit,
        @NotNull LocalDateTime startAt,
        @NotNull LocalDateTime endAt,
        @NotNull Integer status
) {
}
