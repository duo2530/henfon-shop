package com.henfon.shop.marketing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀活动保存请求。
 *
 * @author Henfon
 * @date 2026-08-31
 */
public record MarketingFlashSaleSaveRequest(
        Long id,
        @NotBlank @Size(max = 64) String activityCode,
        @NotBlank @Size(max = 128) String activityName,
        @NotNull LocalDateTime startAt,
        @NotNull LocalDateTime endAt,
        @NotNull @Min(1) @Max(999999) Integer limitPerMember,
        @NotNull @Min(0) @Max(2) Integer status,
        @NotEmpty @Size(max = 2000) List<@Valid Item> items,
        @Min(0) Integer version
) {
    /**
     * 活动商品保存项。
     *
     * @author Henfon
     * @date 2026-08-31
     */
    public record Item(
            Long id,
            @NotNull @Min(1) Long productId,
            @Min(1) Long skuId,
            @NotNull @DecimalMin("0.01") BigDecimal activityPrice,
            @NotNull @Min(0) @Max(999999999) Integer totalStock,
            @NotNull @Min(1) @Max(999999) Integer limitPerMember,
            @NotNull @Min(0) @Max(1) Integer status,
            @Min(0) Integer version
    ) {
    }
}
