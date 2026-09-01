-- 报表领域事件投影表，记录订单、支付和退款事件并通过事件编号实现消费幂等。

USE `henfon-shop`;

CREATE TABLE IF NOT EXISTS reporting_event_projection (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    event_id VARCHAR(64) NOT NULL COMMENT '领域事件唯一编号',
    event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
    topic VARCHAR(128) NOT NULL COMMENT 'RocketMQ主题',
    aggregate_id VARCHAR(64) DEFAULT NULL COMMENT '聚合根编号',
    occurred_at DATETIME(3) DEFAULT NULL COMMENT '事件发生时间',
    payload JSON DEFAULT NULL COMMENT '事件载荷',
    projected_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '投影处理时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reporting_event_projection_event (event_id, is_deleted),
    KEY idx_reporting_event_projection_type_time (event_type, occurred_at),
    KEY idx_reporting_event_projection_aggregate (aggregate_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报表领域事件投影表';
