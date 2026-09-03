package com.henfon.shop.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员业务邮件投递记录实体。
 *
 * @author Henfon
 * @date 2026-09-03
 */
@Data
@TableName("content_email_delivery")
public class ContentEmailDelivery {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memberId;
    private String dedupeKey;
    private String recipient;
    private String eventType;
    private String subject;
    private Integer status;
    private String sendingToken;
    private LocalDateTime sendingAt;
    private LocalDateTime sentAt;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
