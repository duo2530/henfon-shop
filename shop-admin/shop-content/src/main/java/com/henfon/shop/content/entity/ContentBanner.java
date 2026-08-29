package com.henfon.shop.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
    private String imageUrl;
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
