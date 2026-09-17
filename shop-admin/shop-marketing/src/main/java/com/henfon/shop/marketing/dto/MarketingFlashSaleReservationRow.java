package com.henfon.shop.marketing.dto;

import java.time.LocalDateTime;

/**
 * 秒杀预占记录行。
 *
 * <p>预占表只存会员、订单与活动明细标识，页面需要看到会员是谁、哪个订单占用的库存，
 * 因此在查询时按批补齐会员昵称、订单编号、商品与规格名称，避免逐行查询。</p>
 *
 * <p>会员名称优先取昵称，昵称为空回退用户名；会员记录被删除时为空，此时会员标识仍保留，
 * 以便运营据此排查异常占用。</p>
 *
 * @param id 预占记录ID
 * @param createdAt 预占时间
 * @param memberId 会员ID
 * @param memberNo 会员编号
 * @param memberName 会员昵称或用户名
 * @param memberPhone 会员手机号
 * @param orderId 订单ID
 * @param orderNo 订单编号
 * @param productId 商品ID
 * @param productName 商品名称
 * @param skuId SKU ID，为空表示计入商品整体
 * @param skuName SKU名称
 * @param quantity 预占数量
 * @param status 预占状态：0预占中、1已释放
 * @param statusText 预占状态文案
 * @param releasedAt 释放时间，预占中为空
 * @author Henfon
 * @date 2026-09-17
 */
public record MarketingFlashSaleReservationRow(Long id,
                                              LocalDateTime createdAt,
                                              Long memberId,
                                              String memberNo,
                                              String memberName,
                                              String memberPhone,
                                              Long orderId,
                                              String orderNo,
                                              Long productId,
                                              String productName,
                                              Long skuId,
                                              String skuName,
                                              Integer quantity,
                                              Integer status,
                                              String statusText,
                                              LocalDateTime releasedAt) {
}
