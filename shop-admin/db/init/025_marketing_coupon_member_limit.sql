-- 优惠券单会员领取上限
-- 为营销优惠券补充可配置的会员领取上限，历史数据默认每会员 1 张。

USE `henfon-shop`;

SET @per_member_limit_column_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'marketing_coupon'
      AND COLUMN_NAME = 'per_member_limit'
);
SET @add_per_member_limit_column_sql = IF(
    @per_member_limit_column_exists = 0,
    'ALTER TABLE marketing_coupon ADD COLUMN per_member_limit INT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''单会员领取上限'' AFTER total_quantity',
    'SELECT 1'
);
PREPARE add_per_member_limit_column_stmt FROM @add_per_member_limit_column_sql;
EXECUTE add_per_member_limit_column_stmt;
DEALLOCATE PREPARE add_per_member_limit_column_stmt;
