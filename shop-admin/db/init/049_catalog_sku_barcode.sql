-- 商品 SKU 条码字段，支持仓库扫码入库及门户条码检索
SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'catalog_sku' AND column_name = 'barcode'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE catalog_sku ADD COLUMN barcode VARCHAR(64) NULL COMMENT ''商品条码'' AFTER sku_code',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'catalog_sku' AND index_name = 'idx_catalog_sku_barcode'
);
SET @ddl_index := IF(@index_exists = 0,
    'CREATE INDEX idx_catalog_sku_barcode ON catalog_sku (barcode, is_deleted)',
    'SELECT 1');
PREPARE stmt_index FROM @ddl_index;
EXECUTE stmt_index;
DEALLOCATE PREPARE stmt_index;
