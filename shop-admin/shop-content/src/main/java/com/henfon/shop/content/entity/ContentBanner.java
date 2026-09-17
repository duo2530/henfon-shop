package com.henfon.shop.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门户 Banner 实体。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Data
@TableName("content_banner")
public class ContentBanner {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bannerTitle;
    private String bannerTag;
    private String subtitle;
    /** 持久化的图片引用，新数据为 MinIO 对象键，历史数据可能仍是预签名地址。 */
    private String imageUrl;
    /** 图片临时访问地址，仅用于接口返回预览，不映射数据库字段。 */
    @TableField(exist = false)
    private String imageAccessUrl;
    private String linkType;
    private String linkTarget;
    private Integer sortNo;
    private Integer status;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
    private String remark;
}
