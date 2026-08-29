package com.henfon.shop.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员优惠券实体。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Data
@TableName("marketing_member_coupon")
public class MarketingMemberCoupon {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long couponId;
    private Long memberId;
    private Integer receiveStatus;
    private LocalDateTime receivedAt;
    private LocalDateTime usedAt;
    private Long orderId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
}
