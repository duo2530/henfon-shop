package com.henfon.shop.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 交易领域事件 Outbox 实体。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@Data
@TableName("trade_event_outbox")
public class TradeEventOutbox {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private String eventType;
    private String aggregateType;
    private String aggregateId;
    private String topic;
    private String payload;
    private Integer status;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime publishedAt;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
    private String remark;
    /** 人工重试操作人。 */
    private String manualRetryBy;
    /** 最近一次人工重试时间。 */
    private LocalDateTime manualRetryAt;
    /** 最近一次人工重试结果。 */
    private String manualRetryResult;
}
