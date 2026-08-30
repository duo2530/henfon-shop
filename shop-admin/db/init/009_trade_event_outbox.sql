-- 交易领域事件 Outbox 表
-- 订单状态变更先与业务事务一起落库，再由后台任务投递 RocketMQ，避免业务提交成功但消息丢失。

USE `henfon-shop`;

CREATE TABLE IF NOT EXISTS trade_event_outbox (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    event_id VARCHAR(64) NOT NULL COMMENT '事件唯一标识',
    event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
    aggregate_type VARCHAR(64) NOT NULL COMMENT '聚合类型',
    aggregate_id VARCHAR(64) NOT NULL COMMENT '聚合ID',
    topic VARCHAR(128) NOT NULL COMMENT 'RocketMQ主题',
    payload TEXT NOT NULL COMMENT '事件载荷JSON',
    status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0待发送，1已发送，2死信',
    retry_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_at DATETIME(3) DEFAULT NULL COMMENT '下一次重试时间',
    published_at DATETIME(3) DEFAULT NULL COMMENT '发送成功时间',
    last_error VARCHAR(1000) DEFAULT NULL COMMENT '最近一次发送错误',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_trade_event_outbox_event (event_id, is_deleted),
    KEY idx_trade_event_outbox_pending (status, next_retry_at, id),
    KEY idx_trade_event_outbox_aggregate (aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易领域事件Outbox表';
