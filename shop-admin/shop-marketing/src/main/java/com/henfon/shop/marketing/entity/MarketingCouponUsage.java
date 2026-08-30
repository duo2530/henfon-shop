package com.henfon.shop.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券核销流水实体。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Data
@TableName("marketing_coupon_usage")
public class MarketingCouponUsage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long couponId;
    private Long memberCouponId;
    private Long memberId;
    private Long orderId;
    private BigDecimal discountAmount;
    private Integer action;
    private LocalDateTime createdAt;
}
