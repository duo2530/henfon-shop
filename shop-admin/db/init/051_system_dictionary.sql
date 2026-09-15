-- 管理端可变业务字典。
-- 物流承运商由服务端统一维护，设置页和订单发货页共用此字典。

USE `henfon-shop`;

CREATE TABLE IF NOT EXISTS sys_dictionary_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '租户ID，0表示平台公共字典',
    dict_type VARCHAR(64) NOT NULL COMMENT '字典类型',
    item_code VARCHAR(64) NOT NULL COMMENT '字典编码',
    item_name VARCHAR(128) NOT NULL COMMENT '字典名称',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序值',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_dict_item (tenant_id, dict_type, item_code, is_deleted),
    KEY idx_sys_dict_type_status_sort (dict_type, status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统字典项表';

-- 兼容已执行旧版初始化脚本，解除运费模板对单一承运商的默认绑定。
SET @carrier_nullable_sql = (
    SELECT IF(COUNT(*) = 1,
              'ALTER TABLE trade_freight_template MODIFY carrier_name VARCHAR(64) DEFAULT NULL COMMENT ''承运商名称，发货时由承运商字典选择''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'trade_freight_template' AND column_name = 'carrier_name'
);
PREPARE carrier_nullable_stmt FROM @carrier_nullable_sql;
EXECUTE carrier_nullable_stmt;
DEALLOCATE PREPARE carrier_nullable_stmt;

UPDATE trade_freight_template
SET template_name = '全国配送模板', carrier_name = NULL
WHERE template_name = '全国顺丰配送模板' AND is_deleted = 0;

INSERT INTO sys_dictionary_item (tenant_id, dict_type, item_code, item_name, sort_no, status)
VALUES
    (0, 'LOGISTICS_CARRIER', 'SF', '顺丰速运', 10, 1),
    (0, 'LOGISTICS_CARRIER', 'ZTO', '中通快递', 20, 1),
    (0, 'LOGISTICS_CARRIER', 'YTO', '圆通速递', 30, 1),
    (0, 'LOGISTICS_CARRIER', 'JD', '京东快递', 40, 1)
ON DUPLICATE KEY UPDATE
    item_name = VALUES(item_name), sort_no = VALUES(sort_no);
