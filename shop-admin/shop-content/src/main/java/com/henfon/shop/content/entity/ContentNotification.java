package com.henfon.shop.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员站内通知实体。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Data
@TableName("content_notification")
public class ContentNotification {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memberId;
    private Long orderId;
    private String businessId;
    private String eventType;
    private String title;
    private String content;
    private String dedupeKey;
    private Integer readStatus;
    private LocalDateTime readAt;
    /** 运营在管理端通知中心的已读状态，与会员的 readStatus 相互独立。 */
    private Integer adminReadStatus;
    private LocalDateTime adminReadAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
}
