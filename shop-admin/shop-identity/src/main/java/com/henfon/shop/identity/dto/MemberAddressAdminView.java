package com.henfon.shop.identity.dto;

import java.time.LocalDateTime;

/**
 * 后台会员收货地址视图。
 *
 * 地址本身加上所属会员的展示信息：后台看一条地址时要知道是谁的，否则客服核对发货信息
 * 只能拿着会员ID再去会员页翻一遍。
 *
 * @param id 地址ID
 * @param memberId 会员ID
 * @param memberNo 会员编号
 * @param memberName 会员昵称，没有昵称时回退到用户名
 * @param memberPhone 会员注册手机号
 * @param receiverName 收货人
 * @param receiverPhone 收货电话
 * @param province 省
 * @param city 市
 * @param district 区县
 * @param detailAddress 详细地址
 * @param addressTag 地址标签
 * @param isDefault 是否默认地址
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @author Henfon
 * @date 2026-09-22
 */
public record MemberAddressAdminView(Long id, Long memberId, String memberNo, String memberName,
                                     String memberPhone, String receiverName, String receiverPhone,
                                     String province, String city, String district, String detailAddress,
                                     String addressTag, Integer isDefault, LocalDateTime createdAt,
                                     LocalDateTime updatedAt) {
}
