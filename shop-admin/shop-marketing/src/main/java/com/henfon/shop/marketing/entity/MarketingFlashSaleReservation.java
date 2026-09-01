package com.henfon.shop.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀订单库存预占记录。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Data
@TableName("marketing_flash_sale_reservation")
public class MarketingFlashSaleReservation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private Long activityItemId;
    private Long memberId;
    private Long orderId;
    private Integer quantity;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime releasedAt;
}
