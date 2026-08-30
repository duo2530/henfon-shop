-- 交易售后查询与重复申请约束
-- 同一订单或订单明细只能同时存在一条待审核/处理中的售后申请。

USE `henfon-shop`;

SET @after_sale_target_column_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'trade_after_sale'
      AND COLUMN_NAME = 'active_target_key'
);
SET @add_after_sale_target_column_sql = IF(
    @after_sale_target_column_exists = 0,
    'ALTER TABLE trade_after_sale ADD COLUMN active_target_key VARCHAR(100) GENERATED ALWAYS AS (CASE WHEN status IN (10, 20) AND is_deleted = 0 THEN CONCAT(order_id, '':'', COALESCE(order_item_id, 0)) ELSE NULL END) STORED COMMENT ''处理中售后目标唯一键''',
    'SELECT 1'
);
PREPARE add_after_sale_target_column_stmt FROM @add_after_sale_target_column_sql;
EXECUTE add_after_sale_target_column_stmt;
DEALLOCATE PREPARE add_after_sale_target_column_stmt;

SET @after_sale_target_index_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'trade_after_sale'
      AND INDEX_NAME = 'uk_trade_after_sale_active_target'
);
SET @add_after_sale_target_index_sql = IF(
    @after_sale_target_index_exists = 0,
    'ALTER TABLE trade_after_sale ADD UNIQUE KEY uk_trade_after_sale_active_target (active_target_key)',
    'SELECT 1'
);
PREPARE add_after_sale_target_index_stmt FROM @add_after_sale_target_index_sql;
EXECUTE add_after_sale_target_index_stmt;
DEALLOCATE PREPARE add_after_sale_target_index_stmt;

SET @after_sale_member_index_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'trade_after_sale'
      AND INDEX_NAME = 'idx_trade_after_sale_member_status'
);
SET @add_after_sale_member_index_sql = IF(
    @after_sale_member_index_exists = 0,
    'ALTER TABLE trade_after_sale ADD KEY idx_trade_after_sale_member_status (member_id, status, created_at)',
    'SELECT 1'
);
PREPARE add_after_sale_member_index_stmt FROM @add_after_sale_member_index_sql;
EXECUTE add_after_sale_member_index_stmt;
DEALLOCATE PREPARE add_after_sale_member_index_stmt;
