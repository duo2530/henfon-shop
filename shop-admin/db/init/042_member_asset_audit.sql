-- 会员资产调账审计流水
USE `henfon-shop`;

CREATE TABLE IF NOT EXISTS member_asset_audit (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    member_id BIGINT UNSIGNED NOT NULL COMMENT '会员ID',
    points_delta BIGINT NOT NULL DEFAULT 0 COMMENT '积分变动值',
    points_before BIGINT NOT NULL DEFAULT 0 COMMENT '变动前积分',
    points_after BIGINT NOT NULL DEFAULT 0 COMMENT '变动后积分',
    balance_delta DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '余额变动值',
    balance_before DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '变动前余额',
    balance_after DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '变动后余额',
    operation VARCHAR(32) NOT NULL DEFAULT 'ADMIN_ADJUST' COMMENT '操作类型',
    remark VARCHAR(500) DEFAULT NULL COMMENT '操作备注',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_member_asset_audit_member_time (member_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员资产审计流水表';
