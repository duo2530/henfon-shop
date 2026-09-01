-- 交易订单审核扩展
-- 为已支付待发货订单增加独立审核状态，兼容已有订单数据。

USE `henfon-shop`;

SET @audit_status_column_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'trade_order'
      AND COLUMN_NAME = 'audit_status'
);
SET @add_audit_status_column_sql = IF(
    @audit_status_column_exists = 0,
    'ALTER TABLE trade_order ADD COLUMN audit_status TINYINT UNSIGNED NOT NULL DEFAULT 10 COMMENT ''审核状态：10待审核，20已通过，30已驳回'' AFTER order_status',
    'SELECT 1'
);
PREPARE add_audit_status_column_stmt FROM @add_audit_status_column_sql;
EXECUTE add_audit_status_column_stmt;
DEALLOCATE PREPARE add_audit_status_column_stmt;

SET @audit_remark_column_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'trade_order'
      AND COLUMN_NAME = 'audit_remark'
);
SET @add_audit_remark_column_sql = IF(
    @audit_remark_column_exists = 0,
    'ALTER TABLE trade_order ADD COLUMN audit_remark VARCHAR(500) DEFAULT NULL COMMENT ''审核备注'' AFTER audit_status',
    'SELECT 1'
);
PREPARE add_audit_remark_column_stmt FROM @add_audit_remark_column_sql;
EXECUTE add_audit_remark_column_stmt;
DEALLOCATE PREPARE add_audit_remark_column_stmt;

SET @audited_at_column_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'trade_order'
      AND COLUMN_NAME = 'audited_at'
);
SET @add_audited_at_column_sql = IF(
    @audited_at_column_exists = 0,
    'ALTER TABLE trade_order ADD COLUMN audited_at DATETIME(3) DEFAULT NULL COMMENT ''审核时间'' AFTER audit_remark',
    'SELECT 1'
);
PREPARE add_audited_at_column_stmt FROM @add_audited_at_column_sql;
EXECUTE add_audited_at_column_stmt;
DEALLOCATE PREPARE add_audited_at_column_stmt;

SET @audited_by_column_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'trade_order'
      AND COLUMN_NAME = 'audited_by'
);
SET @add_audited_by_column_sql = IF(
    @audited_by_column_exists = 0,
    'ALTER TABLE trade_order ADD COLUMN audited_by VARCHAR(128) DEFAULT NULL COMMENT ''审核管理员'' AFTER audited_at',
    'SELECT 1'
);
PREPARE add_audited_by_column_stmt FROM @add_audited_by_column_sql;
EXECUTE add_audited_by_column_stmt;
DEALLOCATE PREPARE add_audited_by_column_stmt;

-- 历史已进入履约阶段的订单视为审核通过，避免升级后阻断既有发货流程。
UPDATE trade_order
SET audit_status = 20
WHERE audit_status = 10
  AND order_status IN (30, 40, 60, 70);

SET @audit_index_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'trade_order'
      AND INDEX_NAME = 'idx_trade_order_audit_status'
);
SET @create_audit_index_sql = IF(
    @audit_index_exists = 0,
    'CREATE INDEX idx_trade_order_audit_status ON trade_order (audit_status, order_status, created_at)',
    'SELECT 1'
);
PREPARE create_audit_index_stmt FROM @create_audit_index_sql;
EXECUTE create_audit_index_stmt;
DEALLOCATE PREPARE create_audit_index_stmt;
