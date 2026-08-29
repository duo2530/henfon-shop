package com.henfon.shop.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品媒体实体。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Data
@TableName("catalog_product_media")
public class CatalogProductMedia {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private Long skuId;
    private String mediaType;
    private String objectKey;
    private String mediaUrl;
    private Integer isCover;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
    private String remark;
}
