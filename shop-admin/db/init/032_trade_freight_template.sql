-- 交易运费模板及商品计重字段。
-- 运费由后端按模板、收货地区和商品重量计算，门户只展示试算结果。

USE `henfon-shop`;

-- MySQL 8.0 的 ALTER TABLE 不统一支持 ADD COLUMN IF NOT EXISTS，使用元数据判断保证脚本可重复执行。
SET @product_weight_sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE catalog_product ADD COLUMN weight_gram INT UNSIGNED NOT NULL DEFAULT 1000 COMMENT ''商品默认重量（克）''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'catalog_product' AND column_name = 'weight_gram'
);
PREPARE product_weight_stmt FROM @product_weight_sql;
EXECUTE product_weight_stmt;
DEALLOCATE PREPARE product_weight_stmt;

SET @sku_weight_sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE catalog_sku ADD COLUMN weight_gram INT UNSIGNED NOT NULL DEFAULT 1000 COMMENT ''SKU重量（克）''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'catalog_sku' AND column_name = 'weight_gram'
);
PREPARE sku_weight_stmt FROM @sku_weight_sql;
EXECUTE sku_weight_stmt;
DEALLOCATE PREPARE sku_weight_stmt;

CREATE TABLE IF NOT EXISTS trade_freight_template (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    template_name VARCHAR(64) NOT NULL COMMENT '模板名称',
    carrier_name VARCHAR(64) DEFAULT NULL COMMENT '承运商名称，发货时由承运商字典选择',
    base_weight_gram INT UNSIGNED NOT NULL DEFAULT 1000 COMMENT '首重（克）',
    base_fee DECIMAL(18,2) NOT NULL DEFAULT 15.00 COMMENT '首重费用',
    additional_weight_gram INT UNSIGNED NOT NULL DEFAULT 1000 COMMENT '续重计费单位（克）',
    additional_fee DECIMAL(18,2) NOT NULL DEFAULT 5.00 COMMENT '每续重单位费用',
    free_shipping_threshold DECIMAL(18,2) NOT NULL DEFAULT 99.00 COMMENT '包邮门槛，0表示不包邮',
    remote_surcharge DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '偏远地区附加费',
    remote_regions_csv VARCHAR(2000) DEFAULT NULL COMMENT '偏远地区关键词，逗号分隔',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    is_default TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否默认模板：1是，0否',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_trade_freight_template_name (template_name, is_deleted),
    KEY idx_trade_freight_template_default (status, is_default, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易运费模板表';

INSERT INTO trade_freight_template
    (template_name, carrier_name, base_weight_gram, base_fee, additional_weight_gram, additional_fee,
     free_shipping_threshold, remote_surcharge, remote_regions_csv, status, is_default)
VALUES
    ('全国配送模板', NULL, 1000, 15.00, 1000, 5.00, 99.00, 0.00,
     '西藏,新疆,港澳台', 1, 1)
ON DUPLICATE KEY UPDATE
    updated_at = CURRENT_TIMESTAMP(3);
