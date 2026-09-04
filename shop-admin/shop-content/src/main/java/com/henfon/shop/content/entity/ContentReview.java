package com.henfon.shop.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品评价实体。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@Data
@TableName("content_review")
public class ContentReview {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private Long memberId;
    private String memberName;
    private String memberAvatarUrl;
    private Integer rating;
    private String reviewContent;
    private String variantSummary;
    /** 评价图片 URL JSON 数组。 */
    private String imageUrls;
    private Integer helpfulCount;
    private Integer status;
    private LocalDateTime reviewedAt;
    private String replyContent;
    private LocalDateTime repliedAt;
    private String repliedBy;
    /** 会员追评内容。 */
    private String followupContent;
    /** 会员追评时间。 */
    private LocalDateTime followupAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
}
