-- 售后凭证图片关联字段。
USE `henfon-shop`;

SET @after_sale_evidence_column_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'trade_after_sale'
      AND COLUMN_NAME = 'evidence_urls'
);
SET @add_after_sale_evidence_column_sql = IF(
    @after_sale_evidence_column_exists = 0,
    'ALTER TABLE trade_after_sale ADD COLUMN evidence_urls TEXT DEFAULT NULL COMMENT ''售后凭证 URL JSON 数组'' AFTER reason',
    'SELECT 1'
);
PREPARE add_after_sale_evidence_column_stmt FROM @add_after_sale_evidence_column_sql;
EXECUTE add_after_sale_evidence_column_stmt;
DEALLOCATE PREPARE add_after_sale_evidence_column_stmt;
