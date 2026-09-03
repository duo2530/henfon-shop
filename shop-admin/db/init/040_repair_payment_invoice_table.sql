-- 修复历史数据库中缺失的订单发票申请表。
-- 发票模块已上线但旧数据卷未执行 018 版本时，使用幂等建表补齐结构。

USE `henfon-shop`;

CREATE TABLE IF NOT EXISTS payment_invoice (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    invoice_no VARCHAR(64) NOT NULL COMMENT '发票申请号',
    order_id BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
    order_no VARCHAR(64) NOT NULL COMMENT '订单号快照',
    member_id BIGINT UNSIGNED NOT NULL COMMENT '会员ID',
    invoice_type TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '发票类型：1普通，2专用',
    title VARCHAR(200) NOT NULL COMMENT '发票抬头',
    tax_no VARCHAR(64) DEFAULT NULL COMMENT '税号',
    email VARCHAR(128) DEFAULT NULL COMMENT '接收邮箱',
    amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '开票金额',
    status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0待开票，1开票中，2已开票，3失败，4取消',
    invoice_url VARCHAR(1024) DEFAULT NULL COMMENT '发票文件地址',
    failure_reason VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
    requested_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '申请时间',
    issued_at DATETIME(3) DEFAULT NULL COMMENT '开票时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_invoice_no (invoice_no, is_deleted),
    UNIQUE KEY uk_payment_invoice_order (order_id, is_deleted),
    KEY idx_payment_invoice_member_status (member_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单发票申请表';
