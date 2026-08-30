-- 库存模块基础表
-- 命名规范：库存模块使用 inventory_ 前缀。

USE `henfon-shop`;

CREATE TABLE IF NOT EXISTS inventory_warehouse (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    warehouse_code VARCHAR(64) NOT NULL COMMENT '仓库编码',
    warehouse_name VARCHAR(128) NOT NULL COMMENT '仓库名称',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    is_default TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否默认仓库：1是，0否',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_warehouse_code (warehouse_code, is_deleted),
    KEY idx_inventory_warehouse_status (status, is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存仓库表';

CREATE TABLE IF NOT EXISTS inventory_stock (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    warehouse_id BIGINT UNSIGNED NOT NULL COMMENT '仓库ID',
    product_id BIGINT UNSIGNED DEFAULT NULL COMMENT '商品ID',
    sku_id BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID',
    available_stock INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '可用库存',
    locked_stock INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '锁定库存',
    sold_stock BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已售库存',
    safety_stock INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '安全库存',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_stock_warehouse_sku (warehouse_id, sku_id, is_deleted),
    KEY idx_inventory_stock_sku (sku_id),
    KEY idx_inventory_stock_available (available_stock)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存台账表';

CREATE TABLE IF NOT EXISTS inventory_stock_lock (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    lock_no VARCHAR(64) NOT NULL COMMENT '锁定流水号',
    order_id BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
    order_no VARCHAR(64) NOT NULL COMMENT '订单号',
    stock_id BIGINT UNSIGNED NOT NULL COMMENT '库存台账ID',
    sku_id BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID',
    quantity INT UNSIGNED NOT NULL COMMENT '锁定数量',
    status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0锁定，1已释放，2已扣减',
    expire_at DATETIME(3) DEFAULT NULL COMMENT '锁定过期时间',
    released_at DATETIME(3) DEFAULT NULL COMMENT '释放时间',
    deducted_at DATETIME(3) DEFAULT NULL COMMENT '扣减时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_lock_no (lock_no, is_deleted),
    KEY idx_inventory_lock_order_status (order_id, status),
    KEY idx_inventory_lock_expire (status, expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存锁定流水表';

CREATE TABLE IF NOT EXISTS inventory_stock_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    stock_id BIGINT UNSIGNED NOT NULL COMMENT '库存台账ID',
    sku_id BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID',
    biz_type VARCHAR(32) NOT NULL COMMENT '业务类型：RESERVE、RELEASE、DEDUCT、ADJUST',
    biz_no VARCHAR(64) NOT NULL COMMENT '业务单号',
    change_quantity INT NOT NULL COMMENT '库存变化量，正数增加、负数减少',
    before_available INT UNSIGNED NOT NULL COMMENT '变更前可用库存',
    after_available INT UNSIGNED NOT NULL COMMENT '变更后可用库存',
    before_locked INT UNSIGNED NOT NULL COMMENT '变更前锁定库存',
    after_locked INT UNSIGNED NOT NULL COMMENT '变更后锁定库存',
    operator_id BIGINT UNSIGNED DEFAULT NULL COMMENT '操作人ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_inventory_log_stock_time (stock_id, created_at),
    KEY idx_inventory_log_biz (biz_type, biz_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存流水表';

INSERT INTO inventory_warehouse (warehouse_code, warehouse_name, status, is_default)
SELECT 'DEFAULT', '默认仓库', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM inventory_warehouse WHERE warehouse_code = 'DEFAULT' AND is_deleted = 0);
