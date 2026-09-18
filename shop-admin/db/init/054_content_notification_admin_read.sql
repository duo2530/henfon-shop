-- 为会员站内通知补充「运营已读」状态列。
-- 原 read_status / read_at 表达的是会员本人是否已读，管理端通知中心需要独立记录运营是否已跟进，
-- 两者语义不同：管理员标记已读不能篡改会员的阅读状态，所以另开两列。

SET @admin_read_status_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_notification' AND COLUMN_NAME = 'admin_read_status'
);
SET @add_admin_read_status_sql = IF(@admin_read_status_exists = 0,
    'ALTER TABLE content_notification ADD COLUMN admin_read_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''运营已读：0未读，1已读'' AFTER read_at',
    'SELECT 1');
PREPARE add_admin_read_status_stmt FROM @add_admin_read_status_sql;
EXECUTE add_admin_read_status_stmt;
DEALLOCATE PREPARE add_admin_read_status_stmt;

SET @admin_read_at_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_notification' AND COLUMN_NAME = 'admin_read_at'
);
SET @add_admin_read_at_sql = IF(@admin_read_at_exists = 0,
    'ALTER TABLE content_notification ADD COLUMN admin_read_at DATETIME(3) DEFAULT NULL COMMENT ''运营阅读时间'' AFTER admin_read_status',
    'SELECT 1');
PREPARE add_admin_read_at_stmt FROM @add_admin_read_at_sql;
EXECUTE add_admin_read_at_stmt;
DEALLOCATE PREPARE add_admin_read_at_stmt;

SET @admin_read_index_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_notification'
      AND INDEX_NAME = 'idx_content_notification_admin_read'
);
SET @add_admin_read_index_sql = IF(@admin_read_index_exists = 0,
    'ALTER TABLE content_notification ADD INDEX idx_content_notification_admin_read (admin_read_status, created_at)',
    'SELECT 1');
PREPARE add_admin_read_index_stmt FROM @add_admin_read_index_sql;
EXECUTE add_admin_read_index_stmt;
DEALLOCATE PREPARE add_admin_read_index_stmt;
