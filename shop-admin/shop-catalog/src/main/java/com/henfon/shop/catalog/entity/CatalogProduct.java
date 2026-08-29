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
 * 商品目录主实体。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Data
@TableName("catalog_product")
public class CatalogProduct {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long categoryId;
    private String categoryName;
    private String productName;
    private String productCode;
    private String defaultSkuCode;
    private String brandName;
    private String shortDescription;
    private String description;
    private BigDecimal price;
    private BigDecimal marketPrice;
    private BigDecimal costPrice;
    private Integer currentStock;
    private Integer safetyStock;
    private Long salesCount;
    private String mainImageUrl;
    private String tagsCsv;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
    private String remark;
}
