package com.henfon.shop.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存变更流水实体。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Data
@TableName("inventory_stock_log")
public class InventoryStockLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long stockId;
    private Long skuId;
    private String bizType;
    private String bizNo;
    private Integer changeQuantity;
    private Integer beforeAvailable;
    private Integer afterAvailable;
    private Integer beforeLocked;
    private Integer afterLocked;
    private Long operatorId;
    private LocalDateTime createdAt;
    private String remark;
}
