package com.henfon.shop.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 商品媒体保存请求。
 *
 * @author Henfon
 * @date 2026-08-31
 */
public record CatalogProductMediaRequest(
        Long skuId,
        @NotBlank(message = "媒体类型不能为空") @Pattern(regexp = "IMAGE|VIDEO", message = "媒体类型仅支持IMAGE或VIDEO") String mediaType,
        @NotBlank(message = "媒体对象键不能为空") @Size(max = 512, message = "媒体对象键不能超过512个字符") String objectKey,
        @Size(max = 1024, message = "媒体地址不能超过1024个字符") String mediaUrl,
        @NotNull(message = "封面标识不能为空") @Min(value = 0, message = "封面标识必须为0或1") @Max(value = 1, message = "封面标识必须为0或1") Integer isCover,
        @Min(value = 0, message = "媒体排序号不能为负数") Integer sortNo,
        @Size(max = 500, message = "媒体备注不能超过500个字符") String remark
) {
    /**
     * 获取封面标识数值。
     *
     * @return 封面标识数值
     * @author Henfon
     * @date 2026-08-31
     */
    public Integer coverFlag() {
        return isCover;
    }
}
