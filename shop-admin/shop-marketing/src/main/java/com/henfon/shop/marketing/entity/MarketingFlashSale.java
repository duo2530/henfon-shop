package com.henfon.shop.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀促销活动实体。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Data
@TableName("marketing_flash_sale")
public class MarketingFlashSale {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String activityCode;
    private String activityName;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer limitPerMember;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
}
