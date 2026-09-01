-- 评价图片关联字段。
USE `henfon-shop`;

SET @review_image_column_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'content_review'
      AND COLUMN_NAME = 'image_urls'
);
SET @add_review_image_column_sql = IF(
    @review_image_column_exists = 0,
    'ALTER TABLE content_review ADD COLUMN image_urls TEXT DEFAULT NULL COMMENT ''评价图片 URL JSON 数组'' AFTER variant_summary',
    'SELECT 1'
);
PREPARE add_review_image_column_stmt FROM @add_review_image_column_sql;
EXECUTE add_review_image_column_stmt;
DEALLOCATE PREPARE add_review_image_column_stmt;
