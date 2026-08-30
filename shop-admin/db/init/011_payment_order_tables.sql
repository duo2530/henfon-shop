-- 支付单基础表
-- 记录订单支付意图和异步通知结果，支付渠道适配器接入后复用该表完成幂等处理。

USE `henfon-shop`;

CREATE TABLE IF NOT EXISTS payment_order (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    payment_no VARCHAR(64) NOT NULL COMMENT '支付单号',
    order_id BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
    order_no VARCHAR(64) NOT NULL COMMENT '订单号',
    member_id BIGINT UNSIGNED DEFAULT NULL COMMENT '会员ID',
    channel VARCHAR(32) NOT NULL COMMENT '支付渠道：WECHAT_NATIVE等',
    status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0待支付，1支付中，2成功，3已关闭，4失败',
    amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '支付金额',
    transaction_no VARCHAR(128) DEFAULT NULL COMMENT '第三方交易号',
    notify_payload TEXT DEFAULT NULL COMMENT '最近一次异步通知原文',
    paid_at DATETIME(3) DEFAULT NULL COMMENT '支付成功时间',
    expire_at DATETIME(3) DEFAULT NULL COMMENT '支付过期时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_order_no (payment_no, is_deleted),
    UNIQUE KEY uk_payment_order_order_channel (order_id, channel, is_deleted),
    KEY idx_payment_order_member_status (member_id, status, created_at),
    KEY idx_payment_order_expire (status, expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付单表';
