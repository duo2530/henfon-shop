package com.henfon.shop.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Banner 保存请求。
 *
 * @author Henfon
 * @date 2026-08-30
 */
public record ContentBannerSaveRequest(
        Long id,
        @NotBlank @Size(max = 200) String bannerTitle,
        @Size(max = 128) String bannerTag,
        @Size(max = 500) String subtitle,
        @NotBlank @Size(max = 1024) String imageUrl,
        @Size(max = 32) String linkType,
        @Size(max = 128) String linkTarget,
        Integer sortNo,
        Integer status,
        LocalDateTime startAt,
        LocalDateTime endAt,
        @Size(max = 500) String remark
) {
}
