package com.henfon.shop.marketing.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 优惠券核销回滚请求。
 *
 * @param orderId 订单ID
 * @author Henfon
 * @date 2026-08-30
 */
public record MarketingCouponRollbackRequest(@NotNull Long orderId) {
}
