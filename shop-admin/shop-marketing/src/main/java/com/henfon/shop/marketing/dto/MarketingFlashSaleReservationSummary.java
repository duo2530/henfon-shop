package com.henfon.shop.marketing.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀预占记录的聚合统计结果。
 *
 * <p>对应 {@code MarketingFlashSaleReservationMapper#summarize(Long)} 的查询结果，
 * 由数据库一次性算出参与人数、订单数与预占/释放数量，避免把整表预占记录读进内存求和。</p>
 *
 * @author Henfon
 * @date 2026-09-17
 */
@Data
public class MarketingFlashSaleReservationSummary {

    /** 预占记录条数。 */
    private Long reservationCount;

    /** 预占中的数量合计。 */
    private Long reservedQuantity;

    /** 已释放的数量合计。 */
    private Long releasedQuantity;

    /** 参与会员数，按会员去重。 */
    private Long participantCount;

    /** 关联订单数，按订单去重。 */
    private Long orderCount;

    /** 最早一次预占时间。 */
    private LocalDateTime earliestReservedAt;

    /** 最近一次预占时间。 */
    private LocalDateTime latestReservedAt;
}
