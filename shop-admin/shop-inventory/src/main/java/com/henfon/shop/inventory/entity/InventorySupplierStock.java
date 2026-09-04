package com.henfon.shop.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 供应商SKU供货关系实体。 @author Henfon @date 2026-09-04 */
@Data
@TableName("inventory_supplier_stock")
public class InventorySupplierStock {
    @TableId(type = IdType.AUTO) private Long id;
    private Long supplierId;
    private Long productId;
    private Long skuId;
    private BigDecimal supplyPrice;
    private Integer minOrderQuantity;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic private Integer isDeleted;
    @Version private Integer version;
}
