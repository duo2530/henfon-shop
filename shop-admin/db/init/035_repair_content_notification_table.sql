-- 修复历史数据库遗漏的会员站内通知表。
-- 020 版本已存在但未在部分旧数据卷中执行，本迁移保持幂等，可安全重复运行。

USE `henfon-shop`;

CREATE TABLE IF NOT EXISTS content_notification (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    member_id BIGINT UNSIGNED NOT NULL COMMENT '会员ID',
    order_id BIGINT UNSIGNED DEFAULT NULL COMMENT '关联订单ID',
    business_id VARCHAR(64) DEFAULT NULL COMMENT '关联业务单号或业务标识',
    event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
    title VARCHAR(200) NOT NULL COMMENT '通知标题',
    content VARCHAR(1000) NOT NULL COMMENT '通知内容',
    dedupe_key VARCHAR(200) NOT NULL COMMENT '通知幂等键',
    read_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已读状态：0未读，1已读',
    read_at DATETIME(3) DEFAULT NULL COMMENT '阅读时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_content_notification_dedupe (member_id, dedupe_key, is_deleted),
    KEY idx_content_notification_member_read (member_id, read_status, created_at),
    KEY idx_content_notification_order (order_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员站内通知表';
