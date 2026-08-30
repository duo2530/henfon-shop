-- 会员地址默认地址约束：每个会员最多保留一条未删除的默认地址。
-- 使用生成列将非默认地址转换为 NULL，避免唯一索引限制同一会员的多条普通地址。

USE `henfon-shop`;

SET @default_column_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'member_address'
      AND COLUMN_NAME = 'default_member_id'
);
SET @add_default_column_sql = IF(
    @default_column_exists = 0,
    'ALTER TABLE member_address ADD COLUMN default_member_id BIGINT UNSIGNED GENERATED ALWAYS AS (IF(is_default = 1 AND is_deleted = 0, member_id, NULL)) STORED COMMENT ''默认地址唯一约束辅助列''',
    'SELECT 1'
);
PREPARE add_default_column_stmt FROM @add_default_column_sql;
EXECUTE add_default_column_stmt;
DEALLOCATE PREPARE add_default_column_stmt;

SET @default_index_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'member_address'
      AND INDEX_NAME = 'uk_member_address_default_member'
);
SET @add_default_index_sql = IF(
    @default_index_exists = 0,
    'ALTER TABLE member_address ADD UNIQUE KEY uk_member_address_default_member (default_member_id)',
    'SELECT 1'
);
PREPARE add_default_index_stmt FROM @add_default_index_sql;
EXECUTE add_default_index_stmt;
DEALLOCATE PREPARE add_default_index_stmt;
