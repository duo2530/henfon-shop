-- 秒杀订单库存预占与会员限购流水。
USE `henfon-shop`;

CREATE TABLE IF NOT EXISTS marketing_flash_sale_reservation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    activity_id BIGINT UNSIGNED NOT NULL COMMENT '活动ID',
    activity_item_id BIGINT UNSIGNED NOT NULL COMMENT '活动商品明细ID',
    member_id BIGINT UNSIGNED NOT NULL COMMENT '会员ID',
    order_id BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
    quantity INT UNSIGNED NOT NULL COMMENT '预占数量',
    status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0已预占，1已释放',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    released_at DATETIME(3) DEFAULT NULL COMMENT '释放时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_flash_sale_reservation_order_item (order_id, activity_item_id),
    KEY idx_flash_sale_reservation_member (activity_id, activity_item_id, member_id, status),
    KEY idx_flash_sale_reservation_order (order_id, status),
    CONSTRAINT fk_flash_sale_reservation_activity FOREIGN KEY (activity_id) REFERENCES marketing_flash_sale (id),
    CONSTRAINT fk_flash_sale_reservation_item FOREIGN KEY (activity_item_id) REFERENCES marketing_flash_sale_item (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀库存预占流水';
