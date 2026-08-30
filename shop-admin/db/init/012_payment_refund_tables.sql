-- 退款单基础表
-- 记录原路退款申请及异步结果，渠道适配器接入后复用该表完成幂等处理。

USE `henfon-shop`;

CREATE TABLE IF NOT EXISTS payment_refund_order (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    refund_no VARCHAR(64) NOT NULL COMMENT '退款单号',
    idempotency_key VARCHAR(128) DEFAULT NULL COMMENT '退款幂等键',
    payment_no VARCHAR(64) NOT NULL COMMENT '支付单号',
    order_id BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
    order_no VARCHAR(64) NOT NULL COMMENT '订单号',
    member_id BIGINT UNSIGNED DEFAULT NULL COMMENT '会员ID',
    amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '退款金额',
    reason VARCHAR(500) DEFAULT NULL COMMENT '退款原因',
    status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0待退款，1退款中，2成功，3失败，4关闭',
    transaction_no VARCHAR(128) DEFAULT NULL COMMENT '第三方退款交易号',
    notify_payload TEXT DEFAULT NULL COMMENT '最近一次退款通知原文',
    requested_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '申请时间',
    refunded_at DATETIME(3) DEFAULT NULL COMMENT '退款成功时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_refund_no (refund_no, is_deleted),
    UNIQUE KEY uk_payment_refund_idempotency (idempotency_key, is_deleted),
    KEY idx_payment_refund_payment_status (payment_no, status, created_at),
    KEY idx_payment_refund_order_status (order_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付退款单表';
