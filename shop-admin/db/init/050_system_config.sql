-- 管理端租户级系统配置。
-- 配置内容以 JSON 保存，服务端负责提供默认值，前端不再维护业务默认配置。

USE `henfon-shop`;

CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '租户/组织ID',
    config_json JSON NOT NULL COMMENT '系统配置JSON',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_config_tenant (tenant_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户级系统配置表';
