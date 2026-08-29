-- 门户端基础数据表
-- 根据 shop-portal 当前页面覆盖商品详情、评价、轮播、优惠券、会员地址、收藏和对比记录。

USE `henfon-shop`;

CREATE TABLE IF NOT EXISTS catalog_product_feature (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    product_id BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
    feature_text VARCHAR(500) NOT NULL COMMENT '卖点文案',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id), KEY idx_catalog_feature_product_sort (product_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品卖点表';

CREATE TABLE IF NOT EXISTS catalog_product_spec (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    product_id BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
    spec_name VARCHAR(128) NOT NULL COMMENT '规格名称',
    spec_value VARCHAR(500) NOT NULL COMMENT '规格值',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id), KEY idx_catalog_spec_product_sort (product_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品参数表';

CREATE TABLE IF NOT EXISTS content_banner (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    banner_title VARCHAR(200) NOT NULL COMMENT 'Banner标题',
    banner_tag VARCHAR(128) DEFAULT NULL COMMENT 'Banner标签',
    subtitle VARCHAR(500) DEFAULT NULL COMMENT '副标题',
    image_url VARCHAR(1024) NOT NULL COMMENT '图片地址',
    link_type VARCHAR(32) NOT NULL DEFAULT 'PRODUCT' COMMENT '跳转类型',
    link_target VARCHAR(128) DEFAULT NULL COMMENT '跳转目标',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    start_at DATETIME(3) DEFAULT NULL COMMENT '开始时间',
    end_at DATETIME(3) DEFAULT NULL COMMENT '结束时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id), KEY idx_content_banner_status_sort (status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门户Banner表';

CREATE TABLE IF NOT EXISTS content_review (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    product_id BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
    member_id BIGINT UNSIGNED DEFAULT NULL COMMENT '会员ID',
    member_name VARCHAR(128) NOT NULL COMMENT '会员名称快照',
    member_avatar_url VARCHAR(512) DEFAULT NULL COMMENT '会员头像',
    rating TINYINT UNSIGNED NOT NULL DEFAULT 5 COMMENT '评分：1-5',
    review_content VARCHAR(2000) NOT NULL COMMENT '评价内容',
    variant_summary VARCHAR(500) DEFAULT NULL COMMENT '购买规格',
    helpful_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '有帮助数量',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1展示，0隐藏',
    reviewed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '评价时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id), KEY idx_content_review_product_status (product_id, status, reviewed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品评价表';

CREATE TABLE IF NOT EXISTS member_address (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    member_id BIGINT UNSIGNED NOT NULL COMMENT '会员ID',
    receiver_name VARCHAR(64) NOT NULL COMMENT '收货人',
    receiver_phone VARCHAR(32) NOT NULL COMMENT '手机号',
    province VARCHAR(64) NOT NULL COMMENT '省',
    city VARCHAR(64) NOT NULL COMMENT '市',
    district VARCHAR(64) NOT NULL COMMENT '区县',
    detail_address VARCHAR(500) NOT NULL COMMENT '详细地址',
    address_tag VARCHAR(16) DEFAULT NULL COMMENT '地址标签',
    is_default TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否默认地址',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id), KEY idx_member_address_member_default (member_id, is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员收货地址表';

CREATE TABLE IF NOT EXISTS member_favorite (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    member_id BIGINT UNSIGNED NOT NULL COMMENT '会员ID',
    product_id BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id), UNIQUE KEY uk_member_favorite_product (member_id, product_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员收藏商品表';

CREATE TABLE IF NOT EXISTS member_compare_history (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    member_id BIGINT UNSIGNED NOT NULL COMMENT '会员ID',
    compared_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '对比时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id), KEY idx_member_compare_history_member_time (member_id, compared_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员商品对比历史表';

CREATE TABLE IF NOT EXISTS member_compare_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    history_id BIGINT UNSIGNED NOT NULL COMMENT '对比历史ID',
    product_id BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id), KEY idx_member_compare_item_history_sort (history_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员商品对比明细表';

CREATE TABLE IF NOT EXISTS marketing_coupon (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    coupon_code VARCHAR(64) NOT NULL COMMENT '优惠券编码',
    coupon_title VARCHAR(200) NOT NULL COMMENT '优惠券标题',
    discount_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '优惠金额',
    min_spend DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '最低消费金额',
    category_code VARCHAR(64) DEFAULT NULL COMMENT '适用类目编码，空为全场',
    tag VARCHAR(64) DEFAULT NULL COMMENT '展示标签',
    description VARCHAR(500) DEFAULT NULL COMMENT '优惠券说明',
    total_quantity INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '发行总量',
    claimed_quantity INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已领取数量',
    start_at DATETIME(3) NOT NULL COMMENT '生效时间',
    end_at DATETIME(3) NOT NULL COMMENT '失效时间',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id), UNIQUE KEY uk_marketing_coupon_code (coupon_code, is_deleted), KEY idx_marketing_coupon_status_time (status, start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='营销优惠券表';

CREATE TABLE IF NOT EXISTS marketing_member_coupon (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    coupon_id BIGINT UNSIGNED NOT NULL COMMENT '优惠券ID',
    member_id BIGINT UNSIGNED NOT NULL COMMENT '会员ID',
    receive_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '使用状态：0未使用，1已使用，2已过期',
    received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '领取时间',
    used_at DATETIME(3) DEFAULT NULL COMMENT '使用时间',
    order_id BIGINT UNSIGNED DEFAULT NULL COMMENT '使用订单ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id), UNIQUE KEY uk_marketing_member_coupon (coupon_id, member_id, is_deleted), KEY idx_marketing_member_coupon_member_status (member_id, receive_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员优惠券表';

CREATE TABLE IF NOT EXISTS trade_order_logistics (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    order_id BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
    tracking_no VARCHAR(128) NOT NULL COMMENT '物流单号',
    logistics_company VARCHAR(64) NOT NULL COMMENT '物流公司',
    logistics_status VARCHAR(64) DEFAULT NULL COMMENT '物流状态',
    event_time DATETIME(3) NOT NULL COMMENT '节点时间',
    event_description VARCHAR(500) NOT NULL COMMENT '节点描述',
    event_location VARCHAR(200) DEFAULT NULL COMMENT '节点地点',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id), KEY idx_trade_logistics_order_time (order_id, event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单物流轨迹表';
