package com.henfon.shop.marketing.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 优惠券核销请求。
 *
 * @param couponId 优惠券ID
 * @param orderId 订单ID
 * @author Henfon
 * @date 2026-08-30
 */
public record MarketingCouponRedeemRequest(@NotNull Long couponId, @NotNull Long orderId) {
}
