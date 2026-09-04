package com.henfon.shop.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存采购单实体。
 *
 * @author Henfon
 * @date 2026-09-04
 */
@Data
@TableName("inventory_purchase_order")
public class InventoryPurchaseOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String purchaseNo;
    private Long supplierId;
    private Long warehouseId;
    private Integer status;
    private BigDecimal totalAmount;
    private LocalDateTime receivedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
    private String remark;
}
