package com.henfon.shop.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Outbox 人工补偿操作审计记录。
 *
 * @author Henfon
 * @date 2026-09-04
 */
@Data
@TableName("trade_event_outbox_retry_audit")
public class TradeEventOutboxRetryAudit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private String eventType;
    private String operator;
    private String result;
    private String errorMessage;
    private LocalDateTime createdAt;
}
