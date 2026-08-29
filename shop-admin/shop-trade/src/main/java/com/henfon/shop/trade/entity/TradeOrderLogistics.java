package com.henfon.shop.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单物流轨迹实体。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Data
@TableName("trade_order_logistics")
public class TradeOrderLogistics {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String trackingNo;
    private String logisticsCompany;
    private String logisticsStatus;
    private LocalDateTime eventTime;
    private String eventDescription;
    private String eventLocation;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
}
