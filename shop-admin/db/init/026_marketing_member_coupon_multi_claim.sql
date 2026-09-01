-- 放开同一会员领取同一优惠券的单行唯一限制
-- 单会员领取数量由 marketing_coupon.per_member_limit 和事务锁控制。

USE `henfon-shop`;

SET @member_coupon_unique_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'marketing_member_coupon'
      AND INDEX_NAME = 'uk_marketing_member_coupon'
);
SET @drop_member_coupon_unique_sql = IF(
    @member_coupon_unique_exists > 0,
    'ALTER TABLE marketing_member_coupon DROP INDEX uk_marketing_member_coupon',
    'SELECT 1'
);
PREPARE drop_member_coupon_unique_stmt FROM @drop_member_coupon_unique_sql;
EXECUTE drop_member_coupon_unique_stmt;
DEALLOCATE PREPARE drop_member_coupon_unique_stmt;

SET @member_coupon_lookup_index_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'marketing_member_coupon'
      AND INDEX_NAME = 'idx_marketing_member_coupon_member_coupon_status'
);
SET @add_member_coupon_lookup_index_sql = IF(
    @member_coupon_lookup_index_exists = 0,
    'ALTER TABLE marketing_member_coupon ADD KEY idx_marketing_member_coupon_member_coupon_status (member_id, coupon_id, receive_status)',
    'SELECT 1'
);
PREPARE add_member_coupon_lookup_index_stmt FROM @add_member_coupon_lookup_index_sql;
EXECUTE add_member_coupon_lookup_index_stmt;
DEALLOCATE PREPARE add_member_coupon_lookup_index_stmt;
