-- 优惠券核销流水表
USE `henfon-shop`;

CREATE TABLE IF NOT EXISTS marketing_coupon_usage (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    coupon_id BIGINT UNSIGNED NOT NULL COMMENT '优惠券ID',
    member_coupon_id BIGINT UNSIGNED NOT NULL COMMENT '会员优惠券ID',
    member_id BIGINT UNSIGNED NOT NULL COMMENT '会员ID',
    order_id BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
    discount_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '优惠金额',
    action TINYINT UNSIGNED NOT NULL COMMENT '动作：1核销，2回滚',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_marketing_coupon_usage_action (member_coupon_id, order_id, action),
    KEY idx_marketing_coupon_usage_order (order_id),
    KEY idx_marketing_coupon_usage_member_time (member_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券核销流水表';
