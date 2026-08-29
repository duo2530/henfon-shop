package com.henfon.shop.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 营销优惠券实体。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Data
@TableName("marketing_coupon")
public class MarketingCoupon {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String couponCode;
    private String couponTitle;
    private BigDecimal discountAmount;
    private BigDecimal minSpend;
    private String categoryCode;
    private String tag;
    private String description;
    private Integer totalQuantity;
    private Integer claimedQuantity;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
}
