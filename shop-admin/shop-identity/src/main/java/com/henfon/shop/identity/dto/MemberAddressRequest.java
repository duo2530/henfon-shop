package com.henfon.shop.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 会员地址保存请求。
 *
 * @param id 地址ID，编辑时传入
 * @param memberId 会员ID
 * @param receiverName 收货人
 * @param receiverPhone 收货电话
 * @param province 省
 * @param city 市
 * @param district 区县
 * @param detailAddress 详细地址
 * @param addressTag 地址标签
 * @param isDefault 是否默认地址
 * @author Henfon
 * @date 2026-08-29
 */
public record MemberAddressRequest(Long id, @NotNull(message = "会员ID不能为空") Long memberId, @NotBlank String receiverName,
                                   @NotBlank String receiverPhone, @NotBlank String province,
                                   @NotBlank String city, @NotBlank String district,
                                   @NotBlank String detailAddress, String addressTag, Integer isDefault) {
}
