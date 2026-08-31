-- 秒杀/促销活动基础表，后续订单链路可基于活动商品扩展限购和库存扣减。
USE `henfon-shop`;

CREATE TABLE IF NOT EXISTS marketing_flash_sale (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    activity_code VARCHAR(64) NOT NULL COMMENT '活动编码',
    activity_name VARCHAR(128) NOT NULL COMMENT '活动名称',
    start_at DATETIME(3) NOT NULL COMMENT '开始时间',
    end_at DATETIME(3) NOT NULL COMMENT '结束时间',
    limit_per_member INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '会员限购数量',
    status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0草稿，1启用，2停用',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_marketing_flash_sale_code (activity_code, is_deleted),
    KEY idx_marketing_flash_sale_window (status, start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀促销活动表';

CREATE TABLE IF NOT EXISTS marketing_flash_sale_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    activity_id BIGINT UNSIGNED NOT NULL COMMENT '活动ID',
    product_id BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
    sku_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'SKU ID',
    activity_price DECIMAL(18,2) NOT NULL COMMENT '活动价',
    total_stock INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '活动库存',
    sold_stock INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已售库存',
    limit_per_member INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '单会员限购数量',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_marketing_flash_sale_item (activity_id, product_id, sku_id, is_deleted),
    KEY idx_marketing_flash_sale_item_product (product_id, sku_id),
    CONSTRAINT fk_marketing_flash_sale_item_activity FOREIGN KEY (activity_id) REFERENCES marketing_flash_sale (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀促销活动商品表';
