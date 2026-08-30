-- 商品评价商家回复字段
-- 内容模块使用 content_ 前缀，兼容已存在的 content_review 表。

USE `henfon-shop`;

SET @reply_column_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_review' AND COLUMN_NAME = 'reply_content'
);
SET @reply_column_sql = IF(@reply_column_exists = 0,
    'ALTER TABLE content_review ADD COLUMN reply_content VARCHAR(2000) DEFAULT NULL COMMENT ''商家回复内容'' AFTER reviewed_at',
    'SELECT 1');
PREPARE reply_column_stmt FROM @reply_column_sql;
EXECUTE reply_column_stmt;
DEALLOCATE PREPARE reply_column_stmt;

SET @replied_at_column_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_review' AND COLUMN_NAME = 'replied_at'
);
SET @replied_at_column_sql = IF(@replied_at_column_exists = 0,
    'ALTER TABLE content_review ADD COLUMN replied_at DATETIME(3) DEFAULT NULL COMMENT ''商家回复时间'' AFTER reply_content',
    'SELECT 1');
PREPARE replied_at_column_stmt FROM @replied_at_column_sql;
EXECUTE replied_at_column_stmt;
DEALLOCATE PREPARE replied_at_column_stmt;

SET @replied_by_column_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_review' AND COLUMN_NAME = 'replied_by'
);
SET @replied_by_column_sql = IF(@replied_by_column_exists = 0,
    'ALTER TABLE content_review ADD COLUMN replied_by VARCHAR(128) DEFAULT NULL COMMENT ''回复管理员'' AFTER replied_at',
    'SELECT 1');
PREPARE replied_by_column_stmt FROM @replied_by_column_sql;
EXECUTE replied_by_column_stmt;
DEALLOCATE PREPARE replied_by_column_stmt;
