-- 交易订单标旗扩展
-- 为后台订单增加可持久化的运营标旗，兼容已初始化数据库。

USE `henfon-shop`;

SET @flag_color_column_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'trade_order'
      AND COLUMN_NAME = 'flag_color'
);
SET @add_flag_color_column_sql = IF(
    @flag_color_column_exists = 0,
    'ALTER TABLE trade_order ADD COLUMN flag_color VARCHAR(16) DEFAULT NULL COMMENT ''订单运营标旗颜色'' AFTER seller_remark',
    'SELECT 1'
);
PREPARE add_flag_color_column_stmt FROM @add_flag_color_column_sql;
EXECUTE add_flag_color_column_stmt;
DEALLOCATE PREPARE add_flag_color_column_stmt;
