package com.henfon.shop.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 后台会员收货地址修改请求。
 *
 * 与门户的 {@link MemberAddressRequest} 分开：门户那份带 memberId 且必填（用来判定地址
 * 属于谁），后台改地址的会员归属以库里那条记录为准，让调用方传一个用不上的必填字段，
 * 只会逼出「随手把列表里的 memberId 抄进去」这种写法。
 *
 * @param receiverName 收货人
 * @param receiverPhone 收货电话
 * @param province 省
 * @param city 市
 * @param district 区县
 * @param detailAddress 详细地址
 * @param addressTag 地址标签
 * @param isDefault 是否默认地址
 * @author Henfon
 * @date 2026-09-22
 */
public record MemberAddressAdminUpdateRequest(@NotBlank(message = "收货人不能为空") String receiverName,
                                              @NotBlank(message = "收货电话不能为空") String receiverPhone,
                                              @NotBlank(message = "省不能为空") String province,
                                              @NotBlank(message = "市不能为空") String city,
                                              @NotBlank(message = "区县不能为空") String district,
                                              @NotBlank(message = "详细地址不能为空") String detailAddress,
                                              String addressTag, Integer isDefault) {
}
