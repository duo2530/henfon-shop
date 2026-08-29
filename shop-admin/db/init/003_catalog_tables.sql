-- 商品目录基础表
-- 命名规范：商品目录模块使用 catalog_ 前缀。

USE `henfon-shop`;

CREATE TABLE IF NOT EXISTS catalog_category (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    parent_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父类目ID，根类目为0',
    category_name VARCHAR(128) NOT NULL COMMENT '类目名称',
    category_code VARCHAR(64) NOT NULL COMMENT '类目编码',
    level_no TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '类目层级',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    icon_url VARCHAR(512) DEFAULT NULL COMMENT '类目图标地址',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_catalog_category_code (category_code, is_deleted),
    KEY idx_catalog_category_parent_sort (parent_id, sort_no),
    KEY idx_catalog_category_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品类目表';

CREATE TABLE IF NOT EXISTS catalog_product (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    category_id BIGINT UNSIGNED DEFAULT NULL COMMENT '类目ID',
    category_name VARCHAR(128) DEFAULT NULL COMMENT '类目名称快照',
    product_name VARCHAR(200) NOT NULL COMMENT '商品名称',
    product_code VARCHAR(64) NOT NULL COMMENT '商品编码',
    default_sku_code VARCHAR(64) DEFAULT NULL COMMENT '默认SKU编码',
    brand_name VARCHAR(128) DEFAULT NULL COMMENT '品牌名称',
    short_description VARCHAR(500) DEFAULT NULL COMMENT '商品摘要',
    description TEXT COMMENT '商品描述',
    price DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '销售价',
    market_price DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '市场价',
    cost_price DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '成本价',
    current_stock INT NOT NULL DEFAULT 0 COMMENT '当前库存快照',
    safety_stock INT NOT NULL DEFAULT 0 COMMENT '安全库存',
    sales_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '累计销量',
    main_image_url VARCHAR(512) DEFAULT NULL COMMENT '主图地址',
    tags_csv VARCHAR(500) DEFAULT NULL COMMENT '标签，逗号分隔',
    status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0草稿，1上架，2下架',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_catalog_product_code (product_code, is_deleted),
    KEY idx_catalog_product_category_status (category_id, status),
    KEY idx_catalog_product_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品主表';

CREATE TABLE IF NOT EXISTS catalog_sku (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    product_id BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
    sku_code VARCHAR(64) NOT NULL COMMENT 'SKU编码',
    sku_name VARCHAR(200) NOT NULL COMMENT 'SKU名称',
    attributes_json JSON DEFAULT NULL COMMENT 'SKU销售属性JSON',
    price DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '销售价',
    market_price DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '市场价',
    cost_price DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '成本价',
    stock INT NOT NULL DEFAULT 0 COMMENT '库存快照',
    safety_stock INT NOT NULL DEFAULT 0 COMMENT '安全库存',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_catalog_sku_code (sku_code, is_deleted),
    KEY idx_catalog_sku_product_status (product_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品SKU表';

CREATE TABLE IF NOT EXISTS catalog_product_media (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    product_id BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
    sku_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'SKU ID，可为空',
    media_type VARCHAR(32) NOT NULL DEFAULT 'IMAGE' COMMENT '媒体类型：IMAGE、VIDEO',
    object_key VARCHAR(512) NOT NULL COMMENT 'MinIO对象键',
    media_url VARCHAR(1024) DEFAULT NULL COMMENT '媒体访问地址',
    is_cover TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否主图：1是，0否',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_catalog_media_product_sort (product_id, sort_no),
    KEY idx_catalog_media_cover (product_id, is_cover)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品媒体表';
