-- 会员业务邮件持久化投递记录，避免应用重启后重复发送同一事件。

USE `henfon-shop`;

CREATE TABLE IF NOT EXISTS content_email_delivery (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    member_id BIGINT UNSIGNED NOT NULL COMMENT '会员ID',
    dedupe_key VARCHAR(200) NOT NULL COMMENT '事件幂等键',
    recipient VARCHAR(320) NOT NULL COMMENT '收件人邮箱',
    event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
    subject VARCHAR(255) NOT NULL COMMENT '邮件主题',
    status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '投递状态：0待发送，1已发送，2发送中',
    sending_token VARCHAR(64) DEFAULT NULL COMMENT '发送占用令牌',
    sending_at DATETIME(3) DEFAULT NULL COMMENT '开始发送时间',
    sent_at DATETIME(3) DEFAULT NULL COMMENT '发送成功时间',
    last_error VARCHAR(1000) DEFAULT NULL COMMENT '最近一次发送错误',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_content_email_delivery_dedupe (member_id, dedupe_key),
    KEY idx_content_email_delivery_status (status, sending_at),
    KEY idx_content_email_delivery_recipient (recipient, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员业务邮件投递记录表';
