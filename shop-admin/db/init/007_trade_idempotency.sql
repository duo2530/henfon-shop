-- 交易订单幂等键扩展
-- 用于门户创建订单接口的重复提交保护。

USE `henfon-shop`;

SET @idempotency_column_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'trade_order'
      AND COLUMN_NAME = 'idempotency_key'
);
SET @add_idempotency_column_sql = IF(
    @idempotency_column_exists = 0,
    'ALTER TABLE trade_order ADD COLUMN idempotency_key VARCHAR(64) DEFAULT NULL COMMENT ''会员订单幂等键'' AFTER order_no',
    'SELECT 1'
);
PREPARE add_idempotency_column_stmt FROM @add_idempotency_column_sql;
EXECUTE add_idempotency_column_stmt;
DEALLOCATE PREPARE add_idempotency_column_stmt;

SET @idempotency_index_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'trade_order'
      AND INDEX_NAME = 'uk_trade_order_member_idempotency'
);
SET @add_idempotency_index_sql = IF(
    @idempotency_index_exists = 0,
    'ALTER TABLE trade_order ADD UNIQUE KEY uk_trade_order_member_idempotency (member_id, idempotency_key, is_deleted)',
    'SELECT 1'
);
PREPARE add_idempotency_index_stmt FROM @add_idempotency_index_sql;
EXECUTE add_idempotency_index_stmt;
DEALLOCATE PREPARE add_idempotency_index_stmt;
