package com.henfon.shop.common.marketing;

import java.util.List;

/**
 * 秒杀库存预占扩展接口，由营销模块实现、交易模块调用。
 *
 * @author Henfon
 * @date 2026-09-01
 */
public interface FlashSaleReservationService {

    /**
     * 预占活动库存并校验会员限购。
     *
     * @param activityId 活动ID
     * @param memberId 会员ID
     * @param orderId 订单ID
     * @param items 活动商品
     * @author Henfon
     * @date 2026-09-01
     */
    void reserve(Long activityId, Long memberId, Long orderId, List<FlashSaleReservationItem> items);

    /**
     * 释放已取消订单的秒杀库存。
     *
     * @param orderId 订单ID
     * @author Henfon
     * @date 2026-09-01
     */
    void release(Long orderId);
}
