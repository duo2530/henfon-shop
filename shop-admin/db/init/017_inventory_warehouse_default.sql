-- 库存仓库默认仓库约束：启用仓库中最多保留一条默认仓库。
-- 使用生成列将非默认仓库转换为 NULL，避免唯一索引限制普通仓库。

USE `henfon-shop`;

SET @warehouse_default_column_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inventory_warehouse'
      AND COLUMN_NAME = 'active_default_guard'
);
SET @add_warehouse_default_column_sql = IF(
    @warehouse_default_column_exists = 0,
    'ALTER TABLE inventory_warehouse ADD COLUMN active_default_guard TINYINT GENERATED ALWAYS AS (IF(status = 1 AND is_default = 1 AND is_deleted = 0, 1, NULL)) STORED COMMENT ''启用默认仓库唯一约束辅助列''',
    'SELECT 1'
);
PREPARE add_warehouse_default_column_stmt FROM @add_warehouse_default_column_sql;
EXECUTE add_warehouse_default_column_stmt;
DEALLOCATE PREPARE add_warehouse_default_column_stmt;

SET @warehouse_default_index_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inventory_warehouse'
      AND INDEX_NAME = 'uk_inventory_warehouse_active_default'
);
SET @add_warehouse_default_index_sql = IF(
    @warehouse_default_index_exists = 0,
    'ALTER TABLE inventory_warehouse ADD UNIQUE KEY uk_inventory_warehouse_active_default (active_default_guard)',
    'SELECT 1'
);
PREPARE add_warehouse_default_index_stmt FROM @add_warehouse_default_index_sql;
EXECUTE add_warehouse_default_index_stmt;
DEALLOCATE PREPARE add_warehouse_default_index_stmt;
