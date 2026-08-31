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
 * 秒杀促销活动商品实体。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Data
@TableName("marketing_flash_sale_item")
public class MarketingFlashSaleItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private Long productId;
    private Long skuId;
    private BigDecimal activityPrice;
    private Integer totalStock;
    private Integer soldStock;
    private Integer limitPerMember;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
}
