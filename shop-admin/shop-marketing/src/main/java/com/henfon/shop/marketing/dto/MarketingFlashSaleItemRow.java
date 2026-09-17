package com.henfon.shop.marketing.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动商品明细行。
 *
 * <p>活动明细表只存商品与 SKU 标识，商品编码、名称和原价在查询时按批补齐，
 * 供详情页展示与导出共用，避免两处各写一套名称解析逻辑。</p>
 *
 * <p>原价的取值口径与活动保存时一致：指定 SKU 时取 SKU 价格，否则取商品价格。
 * 商品或 SKU 已被删除时对应名称与价格为空，但活动价与库存始终来自明细快照，
 * 不会因为商品下架而丢失活动配置的原始数据。</p>
 *
 * @param id 明细ID
 * @param productId 商品ID
 * @param productCode 商品编码
 * @param productName 商品名称
 * @param skuId SKU ID，为空表示商品整体参与活动
 * @param skuCode SKU编码
 * @param skuName SKU名称
 * @param activityPrice 秒杀活动价
 * @param originalPrice 活动创建时的原价
 * @param discountRate 折扣率，单位为百分数，取一位小数；原价缺失时为空
 * @param totalStock 活动库存
 * @param soldStock 已售活动库存
 * @param remainingStock 剩余活动库存
 * @param limitPerMember 单会员限购数量
 * @param status 启用状态：1启用、0停用
 * @param statusText 启用状态文案
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @author Henfon
 * @date 2026-09-17
 */
public record MarketingFlashSaleItemRow(Long id,
                                       Long productId,
                                       String productCode,
                                       String productName,
                                       Long skuId,
                                       String skuCode,
                                       String skuName,
                                       BigDecimal activityPrice,
                                       BigDecimal originalPrice,
                                       BigDecimal discountRate,
                                       Integer totalStock,
                                       Integer soldStock,
                                       Integer remainingStock,
                                       Integer limitPerMember,
                                       Integer status,
                                       String statusText,
                                       LocalDateTime createdAt,
                                       LocalDateTime updatedAt) {
}
