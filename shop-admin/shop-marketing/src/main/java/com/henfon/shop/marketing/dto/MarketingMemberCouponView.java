package com.henfon.shop.marketing.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员优惠券视图。
 *
 * 会员券表里只有 couponId，券名、面额、门槛与有效期都在优惠券主表。只查会员券表得到的是
 * 一串没有意义的 ID，展示层与 AI 客服每次都要回表补，这里合并成一个结构。
 *
 * @param couponId 优惠券 ID
 * @param couponTitle 券名
 * @param discountAmount 优惠面额
 * @param minSpend 使用门槛，0 表示无门槛
 * @param startAt 生效时间
 * @param endAt 失效时间
 * @param receiveStatus 领取状态：0 未使用、1 已使用、2 已过期
 * @param receivedAt 领取时间
 * @author Henfon
 * @date 2026-09-21
 */
public record MarketingMemberCouponView(Long couponId,
                                        String couponTitle,
                                        BigDecimal discountAmount,
                                        BigDecimal minSpend,
                                        LocalDateTime startAt,
                                        LocalDateTime endAt,
                                        Integer receiveStatus,
                                        LocalDateTime receivedAt) {
}
