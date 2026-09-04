package com.henfon.shop.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品 SKU 实体。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Data
@TableName("catalog_sku")
public class CatalogSku {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String skuCode;
    /** 商品条码，支持仓库扫码和后台检索。 */
    private String barcode;
    private String skuName;
    private String attributesJson;
    private BigDecimal price;
    private BigDecimal marketPrice;
    private BigDecimal costPrice;
    /** SKU 计费重量（克），未设置时由运费服务按 1000 克兜底。 */
    private Integer weightGram;
    private Integer stock;
    private Integer safetyStock;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
    private String remark;
}
