package com.henfon.shop.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存锁定流水实体。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Data
@TableName("inventory_stock_lock")
public class InventoryStockLock {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String lockNo;
    private Long orderId;
    private String orderNo;
    private Long stockId;
    private Long skuId;
    private Integer quantity;
    private Integer status;
    private LocalDateTime expireAt;
    private LocalDateTime releasedAt;
    private LocalDateTime deductedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
    private String remark;
}
