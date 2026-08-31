-- 库存盘点单及明细表。

USE `henfon-shop`;

CREATE TABLE IF NOT EXISTS inventory_stocktake (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    take_no VARCHAR(64) NOT NULL COMMENT '盘点单号',
    warehouse_id BIGINT UNSIGNED NOT NULL COMMENT '仓库ID',
    status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0进行中，1已完成，2已取消',
    operator_id BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人ID',
    started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '盘点开始时间',
    completed_at DATETIME(3) DEFAULT NULL COMMENT '盘点完成时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '盘点备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_stocktake_no (take_no, is_deleted),
    KEY idx_inventory_stocktake_warehouse_status (warehouse_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存盘点单';

CREATE TABLE IF NOT EXISTS inventory_stocktake_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    stocktake_id BIGINT UNSIGNED NOT NULL COMMENT '盘点单ID',
    stock_id BIGINT UNSIGNED NOT NULL COMMENT '库存台账ID',
    sku_id BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID',
    book_quantity INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '盘点开始账面可用库存',
    actual_quantity INT UNSIGNED DEFAULT NULL COMMENT '实盘数量',
    difference_quantity INT NOT NULL DEFAULT 0 COMMENT '盘盈盘亏数量',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '明细备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_stocktake_item (stocktake_id, stock_id, is_deleted),
    KEY idx_inventory_stocktake_item_sku (sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存盘点明细';
