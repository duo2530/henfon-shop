package com.henfon.shop.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存台账实体。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Data
@TableName("inventory_stock")
public class InventoryStock {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long warehouseId;
    private Long productId;
    private Long skuId;
    private Integer availableStock;
    private Integer lockedStock;
    private Long soldStock;
    private Integer safetyStock;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
    private String remark;
}
