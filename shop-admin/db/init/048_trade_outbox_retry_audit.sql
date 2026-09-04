-- Outbox 人工补偿历史审计记录，保留每次运营操作而非仅覆盖最近一次。
USE `henfon-shop`;

CREATE TABLE IF NOT EXISTS trade_event_outbox_retry_audit (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    event_id VARCHAR(64) NOT NULL COMMENT 'Outbox事件标识',
    event_type VARCHAR(64) DEFAULT NULL COMMENT '事件类型',
    operator VARCHAR(64) DEFAULT NULL COMMENT '操作人',
    result VARCHAR(32) NOT NULL COMMENT '结果：SUCCESS/FAILED',
    error_message VARCHAR(1000) DEFAULT NULL COMMENT '失败原因',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_outbox_retry_audit_event (event_id, created_at),
    KEY idx_outbox_retry_audit_operator (operator, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Outbox人工补偿审计记录';
