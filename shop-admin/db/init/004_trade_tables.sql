-- 交易模块基础表
-- 命名规范：交易模块使用 trade_ 前缀。

USE `henfon-shop`;

CREATE TABLE IF NOT EXISTS trade_order (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    order_no VARCHAR(64) NOT NULL COMMENT '订单号',
    member_id BIGINT UNSIGNED DEFAULT NULL COMMENT '会员ID',
    member_name VARCHAR(128) DEFAULT NULL COMMENT '会员名称快照',
    order_status TINYINT UNSIGNED NOT NULL DEFAULT 10 COMMENT '订单状态：10待付款，20待发货，30已发货，40已完成，50已取消，60退款中，70已退款',
    payment_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '支付状态：0未支付，1已支付，2已退款',
    payment_method VARCHAR(32) DEFAULT NULL COMMENT '支付方式',
    subtotal_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '商品金额',
    discount_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '优惠金额',
    freight_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '运费',
    payable_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '应付金额',
    paid_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '实付金额',
    receiver_name VARCHAR(64) NOT NULL COMMENT '收货人',
    receiver_phone VARCHAR(32) NOT NULL COMMENT '收货电话',
    receiver_province VARCHAR(64) DEFAULT NULL COMMENT '省',
    receiver_city VARCHAR(64) DEFAULT NULL COMMENT '市',
    receiver_district VARCHAR(64) DEFAULT NULL COMMENT '区县',
    receiver_address VARCHAR(500) NOT NULL COMMENT '详细地址',
    buyer_remark VARCHAR(500) DEFAULT NULL COMMENT '买家备注',
    seller_remark VARCHAR(500) DEFAULT NULL COMMENT '卖家备注',
    logistics_company VARCHAR(64) DEFAULT NULL COMMENT '物流公司',
    tracking_no VARCHAR(128) DEFAULT NULL COMMENT '物流单号',
    paid_at DATETIME(3) DEFAULT NULL COMMENT '支付时间',
    shipped_at DATETIME(3) DEFAULT NULL COMMENT '发货时间',
    completed_at DATETIME(3) DEFAULT NULL COMMENT '完成时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_trade_order_no (order_no, is_deleted),
    KEY idx_trade_order_member_status (member_id, order_status),
    KEY idx_trade_order_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易订单表';

CREATE TABLE IF NOT EXISTS trade_order_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    order_id BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
    product_id BIGINT UNSIGNED DEFAULT NULL COMMENT '商品ID',
    sku_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'SKU ID',
    product_name VARCHAR(200) NOT NULL COMMENT '商品名称快照',
    sku_name VARCHAR(200) DEFAULT NULL COMMENT 'SKU名称快照',
    sku_code VARCHAR(64) DEFAULT NULL COMMENT 'SKU编码快照',
    image_url VARCHAR(512) DEFAULT NULL COMMENT '商品图片快照',
    unit_price DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '成交单价',
    quantity INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '购买数量',
    item_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '明细金额',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_trade_order_item_order (order_id),
    KEY idx_trade_order_item_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易订单明细表';

CREATE TABLE IF NOT EXISTS trade_cart_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    member_id BIGINT UNSIGNED NOT NULL COMMENT '会员ID',
    product_id BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
    sku_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'SKU ID',
    quantity INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '数量',
    selected TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否选中：1是，0否',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_trade_cart_member_sku (member_id, product_id, sku_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车明细表';

CREATE TABLE IF NOT EXISTS trade_after_sale (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    after_sale_no VARCHAR(64) NOT NULL COMMENT '售后单号',
    order_id BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
    order_item_id BIGINT UNSIGNED DEFAULT NULL COMMENT '订单明细ID',
    member_id BIGINT UNSIGNED DEFAULT NULL COMMENT '会员ID',
    after_sale_type TINYINT UNSIGNED NOT NULL COMMENT '售后类型：1仅退款，2退货退款，3换货',
    status TINYINT UNSIGNED NOT NULL DEFAULT 10 COMMENT '状态：10待审核，20处理中，30已完成，40已拒绝，50已取消',
    reason VARCHAR(500) DEFAULT NULL COMMENT '申请原因',
    refund_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '退款金额',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：1是，0否',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_trade_after_sale_no (after_sale_no, is_deleted),
    KEY idx_trade_after_sale_order (order_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易售后表';
