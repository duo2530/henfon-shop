package com.henfon.shop.reporting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报表领域事件投影实体。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@Data
@TableName("reporting_event_projection")
public class ReportingEventProjection {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private String eventType;
    private String topic;
    private String aggregateId;
    private LocalDateTime occurredAt;
    private String payload;
    private LocalDateTime projectedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
    @Version
    private Integer version;
}
