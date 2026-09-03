-- 修复历史数据库中缺失的库存供应商表。
-- 供应商模块已上线但旧数据卷未执行 021 版本时，使用幂等建表补齐结构。

USE `henfon-shop`;

CREATE TABLE IF NOT EXISTS inventory_supplier (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    supplier_code VARCHAR(64) NOT NULL COMMENT '供应商编码',
    supplier_name VARCHAR(128) NOT NULL COMMENT '供应商名称',
    contact_name VARCHAR(64) DEFAULT NULL COMMENT '联系人',
    contact_phone VARCHAR(32) DEFAULT NULL COMMENT '联系电话',
    address VARCHAR(255) DEFAULT NULL COMMENT '供应商地址',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_supplier_code (supplier_code, is_deleted),
    KEY idx_inventory_supplier_status (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存供应商表';
