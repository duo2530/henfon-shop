-- 供应商与SKU供货关系表，支持采购选供应商及供货价维护。
USE `henfon-shop`;
CREATE TABLE IF NOT EXISTS inventory_supplier_stock (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
 supplier_id BIGINT UNSIGNED NOT NULL,
 product_id BIGINT UNSIGNED NOT NULL,
 sku_id BIGINT UNSIGNED NOT NULL,
 supply_price DECIMAL(18,2) NOT NULL DEFAULT 0,
 min_order_quantity INT UNSIGNED NOT NULL DEFAULT 1,
 status TINYINT UNSIGNED NOT NULL DEFAULT 1,
 remark VARCHAR(500) DEFAULT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
 version INT UNSIGNED NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_inventory_supplier_stock(supplier_id, sku_id, is_deleted),
 KEY idx_inventory_supplier_stock_sku(sku_id, status),
 KEY idx_inventory_supplier_stock_supplier(supplier_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
