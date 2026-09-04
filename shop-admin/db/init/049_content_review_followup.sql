SET @followup_content_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_review' AND COLUMN_NAME = 'followup_content'
);
SET @add_followup_content_sql = IF(@followup_content_exists = 0,
    'ALTER TABLE content_review ADD COLUMN followup_content VARCHAR(2000) DEFAULT NULL COMMENT ''会员追评内容'' AFTER replied_by',
    'SELECT 1');
PREPARE add_followup_content_stmt FROM @add_followup_content_sql;
EXECUTE add_followup_content_stmt;
DEALLOCATE PREPARE add_followup_content_stmt;

SET @followup_at_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_review' AND COLUMN_NAME = 'followup_at'
);
SET @add_followup_at_sql = IF(@followup_at_exists = 0,
    'ALTER TABLE content_review ADD COLUMN followup_at DATETIME(3) DEFAULT NULL COMMENT ''会员追评时间'' AFTER followup_content',
    'SELECT 1');
PREPARE add_followup_at_stmt FROM @add_followup_at_sql;
EXECUTE add_followup_at_stmt;
DEALLOCATE PREPARE add_followup_at_stmt;
