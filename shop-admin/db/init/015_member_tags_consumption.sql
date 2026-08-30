-- 会员标签与消费统计
-- 命名规范：会员模块使用 member_ 前缀。

USE `henfon-shop`;

CREATE TABLE IF NOT EXISTS member_tag (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '租户ID',
    tag_name VARCHAR(64) NOT NULL COMMENT '标签名称',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_tag_name (tenant_id, tag_name, is_deleted),
    KEY idx_member_tag_status_sort (tenant_id, status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员标签表';

CREATE TABLE IF NOT EXISTS member_user_tag (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    member_id BIGINT UNSIGNED NOT NULL COMMENT '会员ID',
    tag_id BIGINT UNSIGNED NOT NULL COMMENT '标签ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_user_tag (member_id, tag_id, is_deleted),
    KEY idx_member_user_tag_member (member_id, is_deleted),
    KEY idx_member_user_tag_tag (tag_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员标签关联表';

CREATE TABLE IF NOT EXISTS member_consumption_stat (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    member_id BIGINT UNSIGNED NOT NULL COMMENT '会员ID',
    paid_order_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '有效已支付订单数',
    paid_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '有效累计消费金额',
    last_order_at DATETIME(3) DEFAULT NULL COMMENT '最近支付时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_consumption_stat_member (member_id),
    KEY idx_member_consumption_stat_amount (paid_amount),
    KEY idx_member_consumption_stat_last_order (last_order_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员消费统计表';

-- 为已存在的历史订单初始化会员消费统计，后续支付/退款由交易事务增量维护。
INSERT INTO member_consumption_stat (member_id, paid_order_count, paid_amount, last_order_at)
SELECT member_id,
       COUNT(*) AS paid_order_count,
       COALESCE(SUM(paid_amount), 0.00) AS paid_amount,
       MAX(paid_at) AS last_order_at
  FROM trade_order
 WHERE is_deleted = 0
   AND member_id IS NOT NULL
   AND payment_status = 1
   AND order_status <> 70
 GROUP BY member_id
ON DUPLICATE KEY UPDATE
    paid_order_count = VALUES(paid_order_count),
    paid_amount = VALUES(paid_amount),
    last_order_at = VALUES(last_order_at);
