package com.henfon.shop.marketing.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动详情响应。
 *
 * <p>活动基础字段来自 {@code marketing_flash_sale}，库存与预占统计由两条聚合查询得到，
 * 不把活动商品与预占记录读进内存再累加——明细表可能上百行，预占记录更是随活动时长持续增长。
 * 预占记录的时间范围用于页面提示「最近一次抢购发生在何时」，两者都为空代表尚无任何会员参与。</p>
 *
 * @param id 活动ID
 * @param activityCode 活动编码
 * @param activityName 活动名称
 * @param startAt 开始时间
 * @param endAt 结束时间
 * @param limitPerMember 活动级单会员限购数量
 * @param status 配置状态：0草稿、1启用、2停用
 * @param statusText 结合时间窗口推导的展示状态
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @param itemCount 活动商品数量
 * @param totalStock 活动总库存
 * @param soldStock 已售活动库存
 * @param remainingStock 剩余活动库存
 * @param sellThroughRate 售罄率，单位为百分数，取一位小数
 * @param reservedQuantity 预占中的数量
 * @param releasedQuantity 已释放的数量
 * @param participantCount 参与会员数，按会员去重
 * @param reservationCount 预占记录条数
 * @param orderCount 关联订单数，按订单去重
 * @param earliestReservedAt 最早一次预占时间
 * @param latestReservedAt 最近一次预占时间
 * @author Henfon
 * @date 2026-09-17
 */
public record MarketingFlashSaleDetailResponse(Long id,
                                               String activityCode,
                                               String activityName,
                                               LocalDateTime startAt,
                                               LocalDateTime endAt,
                                               Integer limitPerMember,
                                               Integer status,
                                               String statusText,
                                               LocalDateTime createdAt,
                                               LocalDateTime updatedAt,
                                               Long itemCount,
                                               Long totalStock,
                                               Long soldStock,
                                               Long remainingStock,
                                               BigDecimal sellThroughRate,
                                               Long reservedQuantity,
                                               Long releasedQuantity,
                                               Long participantCount,
                                               Long reservationCount,
                                               Long orderCount,
                                               LocalDateTime earliestReservedAt,
                                               LocalDateTime latestReservedAt) {
}
