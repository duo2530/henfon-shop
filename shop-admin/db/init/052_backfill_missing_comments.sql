-- 回填缺失的表级与列级注释，只改元数据，不动字段类型与业务数据。
-- 成因：005 等早期建表脚本未写 COMMENT，后续脚本因 CREATE TABLE IF NOT EXISTS 被整体跳过，
--       早期库的注释一直没补上；043/046 两张采购相关脚本则从未写过注释。

USE `henfon-shop`;


-- 表级注释：以下 3 张表建表脚本从未声明过表注释
ALTER TABLE `inventory_purchase_order` COMMENT = '库存采购单表';
ALTER TABLE `inventory_purchase_item` COMMENT = '库存采购单明细表';
ALTER TABLE `inventory_supplier_stock` COMMENT = '库存供应商供货关系表';

-- marketing_coupon
ALTER TABLE `marketing_coupon` MODIFY COLUMN `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID';
ALTER TABLE `marketing_coupon` MODIFY COLUMN `coupon_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '优惠券编码';
ALTER TABLE `marketing_coupon` MODIFY COLUMN `coupon_title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '优惠券标题';
ALTER TABLE `marketing_coupon` MODIFY COLUMN `discount_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '优惠金额';
ALTER TABLE `marketing_coupon` MODIFY COLUMN `min_spend` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '最低消费金额';
ALTER TABLE `marketing_coupon` MODIFY COLUMN `category_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '适用类目编码，空为全场';
ALTER TABLE `marketing_coupon` MODIFY COLUMN `tag` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '展示标签';
ALTER TABLE `marketing_coupon` MODIFY COLUMN `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '优惠券说明';
ALTER TABLE `marketing_coupon` MODIFY COLUMN `total_quantity` int unsigned NOT NULL DEFAULT '0' COMMENT '发行总量';
ALTER TABLE `marketing_coupon` MODIFY COLUMN `claimed_quantity` int unsigned NOT NULL DEFAULT '0' COMMENT '已领取数量';
ALTER TABLE `marketing_coupon` MODIFY COLUMN `start_at` datetime(3) NOT NULL COMMENT '生效时间';
ALTER TABLE `marketing_coupon` MODIFY COLUMN `end_at` datetime(3) NOT NULL COMMENT '失效时间';
ALTER TABLE `marketing_coupon` MODIFY COLUMN `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '状态：1启用，0停用';
ALTER TABLE `marketing_coupon` MODIFY COLUMN `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间';
ALTER TABLE `marketing_coupon` MODIFY COLUMN `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间';
ALTER TABLE `marketing_coupon` MODIFY COLUMN `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除';
ALTER TABLE `marketing_coupon` MODIFY COLUMN `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本';

-- content_banner
ALTER TABLE `content_banner` MODIFY COLUMN `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID';
ALTER TABLE `content_banner` MODIFY COLUMN `banner_title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Banner标题';
ALTER TABLE `content_banner` MODIFY COLUMN `banner_tag` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Banner标签';
ALTER TABLE `content_banner` MODIFY COLUMN `subtitle` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '副标题';
ALTER TABLE `content_banner` MODIFY COLUMN `image_url` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '图片地址';
ALTER TABLE `content_banner` MODIFY COLUMN `link_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PRODUCT' COMMENT '跳转类型';
ALTER TABLE `content_banner` MODIFY COLUMN `link_target` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '跳转目标';
ALTER TABLE `content_banner` MODIFY COLUMN `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号';
ALTER TABLE `content_banner` MODIFY COLUMN `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '状态：1启用，0停用';
ALTER TABLE `content_banner` MODIFY COLUMN `start_at` datetime(3) DEFAULT NULL COMMENT '开始时间';
ALTER TABLE `content_banner` MODIFY COLUMN `end_at` datetime(3) DEFAULT NULL COMMENT '结束时间';
ALTER TABLE `content_banner` MODIFY COLUMN `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间';
ALTER TABLE `content_banner` MODIFY COLUMN `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间';
ALTER TABLE `content_banner` MODIFY COLUMN `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除';
ALTER TABLE `content_banner` MODIFY COLUMN `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本';
ALTER TABLE `content_banner` MODIFY COLUMN `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注';

-- content_notification
ALTER TABLE `content_notification` MODIFY COLUMN `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID';
ALTER TABLE `content_notification` MODIFY COLUMN `member_id` bigint unsigned NOT NULL COMMENT '会员ID';
ALTER TABLE `content_notification` MODIFY COLUMN `order_id` bigint unsigned DEFAULT NULL COMMENT '关联订单ID';
ALTER TABLE `content_notification` MODIFY COLUMN `business_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联业务单号或业务标识';
ALTER TABLE `content_notification` MODIFY COLUMN `event_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件类型';
ALTER TABLE `content_notification` MODIFY COLUMN `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知标题';
ALTER TABLE `content_notification` MODIFY COLUMN `content` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知内容';
ALTER TABLE `content_notification` MODIFY COLUMN `dedupe_key` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知幂等键';
ALTER TABLE `content_notification` MODIFY COLUMN `read_status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '已读状态：0未读，1已读';
ALTER TABLE `content_notification` MODIFY COLUMN `read_at` datetime(3) DEFAULT NULL COMMENT '阅读时间';
ALTER TABLE `content_notification` MODIFY COLUMN `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间';
ALTER TABLE `content_notification` MODIFY COLUMN `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间';
ALTER TABLE `content_notification` MODIFY COLUMN `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是';
ALTER TABLE `content_notification` MODIFY COLUMN `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本';
-- remark 列脚本未声明、仅存在于部分历史库，先判断存在性再补注释
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema = DATABASE() AND table_name = 'content_notification' AND column_name = 'remark');
SET @ddl := IF(@col_exists = 1, 'ALTER TABLE `content_notification` MODIFY COLUMN `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''备注''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- content_review
ALTER TABLE `content_review` MODIFY COLUMN `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID';
ALTER TABLE `content_review` MODIFY COLUMN `product_id` bigint unsigned NOT NULL COMMENT '商品ID';
ALTER TABLE `content_review` MODIFY COLUMN `member_id` bigint unsigned DEFAULT NULL COMMENT '会员ID';
ALTER TABLE `content_review` MODIFY COLUMN `member_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会员名称快照';
ALTER TABLE `content_review` MODIFY COLUMN `member_avatar_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会员头像';
ALTER TABLE `content_review` MODIFY COLUMN `rating` tinyint unsigned NOT NULL DEFAULT '5' COMMENT '评分：1-5';
ALTER TABLE `content_review` MODIFY COLUMN `review_content` varchar(2000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评价内容';
ALTER TABLE `content_review` MODIFY COLUMN `variant_summary` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '购买规格';
ALTER TABLE `content_review` MODIFY COLUMN `helpful_count` int unsigned NOT NULL DEFAULT '0' COMMENT '有帮助数量';
ALTER TABLE `content_review` MODIFY COLUMN `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '状态：1展示，0隐藏';
ALTER TABLE `content_review` MODIFY COLUMN `reviewed_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '评价时间';
ALTER TABLE `content_review` MODIFY COLUMN `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间';
ALTER TABLE `content_review` MODIFY COLUMN `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间';
ALTER TABLE `content_review` MODIFY COLUMN `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除';
ALTER TABLE `content_review` MODIFY COLUMN `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本';

-- member_address
ALTER TABLE `member_address` MODIFY COLUMN `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID';
ALTER TABLE `member_address` MODIFY COLUMN `member_id` bigint unsigned NOT NULL COMMENT '会员ID';
ALTER TABLE `member_address` MODIFY COLUMN `receiver_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '收货人';
ALTER TABLE `member_address` MODIFY COLUMN `receiver_phone` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '手机号';
ALTER TABLE `member_address` MODIFY COLUMN `province` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '省';
ALTER TABLE `member_address` MODIFY COLUMN `city` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '市';
ALTER TABLE `member_address` MODIFY COLUMN `district` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '区县';
ALTER TABLE `member_address` MODIFY COLUMN `detail_address` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '详细地址';
ALTER TABLE `member_address` MODIFY COLUMN `address_tag` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '地址标签';
ALTER TABLE `member_address` MODIFY COLUMN `is_default` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '是否默认地址';
ALTER TABLE `member_address` MODIFY COLUMN `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间';
ALTER TABLE `member_address` MODIFY COLUMN `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间';
ALTER TABLE `member_address` MODIFY COLUMN `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除';
ALTER TABLE `member_address` MODIFY COLUMN `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本';

-- content_email_delivery
ALTER TABLE `content_email_delivery` MODIFY COLUMN `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID';
ALTER TABLE `content_email_delivery` MODIFY COLUMN `member_id` bigint unsigned NOT NULL COMMENT '会员ID';
ALTER TABLE `content_email_delivery` MODIFY COLUMN `dedupe_key` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件幂等键';
ALTER TABLE `content_email_delivery` MODIFY COLUMN `recipient` varchar(320) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '收件人邮箱';
ALTER TABLE `content_email_delivery` MODIFY COLUMN `event_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件类型';
ALTER TABLE `content_email_delivery` MODIFY COLUMN `subject` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '邮件主题';
ALTER TABLE `content_email_delivery` MODIFY COLUMN `status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '投递状态：0待发送，1已发送，2发送中';
ALTER TABLE `content_email_delivery` MODIFY COLUMN `sending_token` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发送占用令牌';
ALTER TABLE `content_email_delivery` MODIFY COLUMN `sending_at` datetime(3) DEFAULT NULL COMMENT '开始发送时间';
ALTER TABLE `content_email_delivery` MODIFY COLUMN `sent_at` datetime(3) DEFAULT NULL COMMENT '发送成功时间';
ALTER TABLE `content_email_delivery` MODIFY COLUMN `last_error` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近一次发送错误';
ALTER TABLE `content_email_delivery` MODIFY COLUMN `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间';
ALTER TABLE `content_email_delivery` MODIFY COLUMN `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间';

-- marketing_flash_sale_item
ALTER TABLE `marketing_flash_sale_item` MODIFY COLUMN `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID';
ALTER TABLE `marketing_flash_sale_item` MODIFY COLUMN `activity_id` bigint unsigned NOT NULL COMMENT '活动ID';
ALTER TABLE `marketing_flash_sale_item` MODIFY COLUMN `product_id` bigint unsigned NOT NULL COMMENT '商品ID';
ALTER TABLE `marketing_flash_sale_item` MODIFY COLUMN `sku_id` bigint unsigned DEFAULT NULL COMMENT 'SKU ID';
ALTER TABLE `marketing_flash_sale_item` MODIFY COLUMN `activity_price` decimal(18,2) NOT NULL COMMENT '活动价';
ALTER TABLE `marketing_flash_sale_item` MODIFY COLUMN `total_stock` int unsigned NOT NULL DEFAULT '0' COMMENT '活动库存';
ALTER TABLE `marketing_flash_sale_item` MODIFY COLUMN `sold_stock` int unsigned NOT NULL DEFAULT '0' COMMENT '已售库存';
ALTER TABLE `marketing_flash_sale_item` MODIFY COLUMN `limit_per_member` int unsigned NOT NULL DEFAULT '1' COMMENT '单会员限购数量';
ALTER TABLE `marketing_flash_sale_item` MODIFY COLUMN `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '状态：1启用，0停用';
ALTER TABLE `marketing_flash_sale_item` MODIFY COLUMN `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间';
ALTER TABLE `marketing_flash_sale_item` MODIFY COLUMN `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间';
ALTER TABLE `marketing_flash_sale_item` MODIFY COLUMN `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否';
ALTER TABLE `marketing_flash_sale_item` MODIFY COLUMN `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本';

-- trade_order_logistics
ALTER TABLE `trade_order_logistics` MODIFY COLUMN `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID';
ALTER TABLE `trade_order_logistics` MODIFY COLUMN `order_id` bigint unsigned NOT NULL COMMENT '订单ID';
ALTER TABLE `trade_order_logistics` MODIFY COLUMN `tracking_no` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物流单号';
ALTER TABLE `trade_order_logistics` MODIFY COLUMN `logistics_company` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物流公司';
ALTER TABLE `trade_order_logistics` MODIFY COLUMN `logistics_status` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '物流状态';
ALTER TABLE `trade_order_logistics` MODIFY COLUMN `event_time` datetime(3) NOT NULL COMMENT '节点时间';
ALTER TABLE `trade_order_logistics` MODIFY COLUMN `event_description` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点描述';
ALTER TABLE `trade_order_logistics` MODIFY COLUMN `event_location` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '节点地点';
ALTER TABLE `trade_order_logistics` MODIFY COLUMN `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号';
ALTER TABLE `trade_order_logistics` MODIFY COLUMN `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间';
ALTER TABLE `trade_order_logistics` MODIFY COLUMN `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间';
ALTER TABLE `trade_order_logistics` MODIFY COLUMN `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除';
ALTER TABLE `trade_order_logistics` MODIFY COLUMN `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本';

-- inventory_purchase_item
ALTER TABLE `inventory_purchase_item` MODIFY COLUMN `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID';
ALTER TABLE `inventory_purchase_item` MODIFY COLUMN `purchase_order_id` bigint unsigned NOT NULL COMMENT '采购单ID';
ALTER TABLE `inventory_purchase_item` MODIFY COLUMN `product_id` bigint unsigned NOT NULL COMMENT '商品ID';
ALTER TABLE `inventory_purchase_item` MODIFY COLUMN `sku_id` bigint unsigned NOT NULL COMMENT 'SKU ID';
ALTER TABLE `inventory_purchase_item` MODIFY COLUMN `quantity` int unsigned NOT NULL COMMENT '采购数量';
ALTER TABLE `inventory_purchase_item` MODIFY COLUMN `received_quantity` int unsigned NOT NULL DEFAULT '0' COMMENT '已入库数量';
ALTER TABLE `inventory_purchase_item` MODIFY COLUMN `unit_price` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '采购单价';
ALTER TABLE `inventory_purchase_item` MODIFY COLUMN `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间';
ALTER TABLE `inventory_purchase_item` MODIFY COLUMN `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间';
ALTER TABLE `inventory_purchase_item` MODIFY COLUMN `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否';
ALTER TABLE `inventory_purchase_item` MODIFY COLUMN `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本';
ALTER TABLE `inventory_purchase_item` MODIFY COLUMN `remark` varchar(500) DEFAULT NULL COMMENT '备注';

-- inventory_purchase_order
ALTER TABLE `inventory_purchase_order` MODIFY COLUMN `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID';
ALTER TABLE `inventory_purchase_order` MODIFY COLUMN `purchase_no` varchar(64) NOT NULL COMMENT '采购单号';
ALTER TABLE `inventory_purchase_order` MODIFY COLUMN `supplier_id` bigint unsigned NOT NULL COMMENT '供应商ID';
ALTER TABLE `inventory_purchase_order` MODIFY COLUMN `warehouse_id` bigint unsigned NOT NULL COMMENT '入库仓库ID';
ALTER TABLE `inventory_purchase_order` MODIFY COLUMN `status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '采购单状态：0待入库，1已入库';
ALTER TABLE `inventory_purchase_order` MODIFY COLUMN `total_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '采购总金额';
ALTER TABLE `inventory_purchase_order` MODIFY COLUMN `received_at` datetime(3) DEFAULT NULL COMMENT '入库完成时间';
ALTER TABLE `inventory_purchase_order` MODIFY COLUMN `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间';
ALTER TABLE `inventory_purchase_order` MODIFY COLUMN `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间';
ALTER TABLE `inventory_purchase_order` MODIFY COLUMN `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否';
ALTER TABLE `inventory_purchase_order` MODIFY COLUMN `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本';
ALTER TABLE `inventory_purchase_order` MODIFY COLUMN `remark` varchar(500) DEFAULT NULL COMMENT '备注';

-- inventory_supplier_stock
ALTER TABLE `inventory_supplier_stock` MODIFY COLUMN `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID';
ALTER TABLE `inventory_supplier_stock` MODIFY COLUMN `supplier_id` bigint unsigned NOT NULL COMMENT '供应商ID';
ALTER TABLE `inventory_supplier_stock` MODIFY COLUMN `product_id` bigint unsigned NOT NULL COMMENT '商品ID';
ALTER TABLE `inventory_supplier_stock` MODIFY COLUMN `sku_id` bigint unsigned NOT NULL COMMENT 'SKU ID';
ALTER TABLE `inventory_supplier_stock` MODIFY COLUMN `supply_price` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '供货价';
ALTER TABLE `inventory_supplier_stock` MODIFY COLUMN `min_order_quantity` int unsigned NOT NULL DEFAULT '1' COMMENT '最小起订量';
ALTER TABLE `inventory_supplier_stock` MODIFY COLUMN `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '状态：1启用，0停用';
ALTER TABLE `inventory_supplier_stock` MODIFY COLUMN `remark` varchar(500) DEFAULT NULL COMMENT '备注';
ALTER TABLE `inventory_supplier_stock` MODIFY COLUMN `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间';
ALTER TABLE `inventory_supplier_stock` MODIFY COLUMN `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间';
ALTER TABLE `inventory_supplier_stock` MODIFY COLUMN `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否';
ALTER TABLE `inventory_supplier_stock` MODIFY COLUMN `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本';

-- marketing_flash_sale
ALTER TABLE `marketing_flash_sale` MODIFY COLUMN `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID';
ALTER TABLE `marketing_flash_sale` MODIFY COLUMN `activity_code` varchar(64) NOT NULL COMMENT '活动编码';
ALTER TABLE `marketing_flash_sale` MODIFY COLUMN `activity_name` varchar(128) NOT NULL COMMENT '活动名称';
ALTER TABLE `marketing_flash_sale` MODIFY COLUMN `start_at` datetime(3) NOT NULL COMMENT '开始时间';
ALTER TABLE `marketing_flash_sale` MODIFY COLUMN `end_at` datetime(3) NOT NULL COMMENT '结束时间';
ALTER TABLE `marketing_flash_sale` MODIFY COLUMN `limit_per_member` int unsigned NOT NULL DEFAULT '1' COMMENT '会员限购数量';
ALTER TABLE `marketing_flash_sale` MODIFY COLUMN `status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '状态：0草稿，1启用，2停用';
ALTER TABLE `marketing_flash_sale` MODIFY COLUMN `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间';
ALTER TABLE `marketing_flash_sale` MODIFY COLUMN `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间';
ALTER TABLE `marketing_flash_sale` MODIFY COLUMN `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否';
ALTER TABLE `marketing_flash_sale` MODIFY COLUMN `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本';

-- marketing_member_coupon
ALTER TABLE `marketing_member_coupon` MODIFY COLUMN `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID';
ALTER TABLE `marketing_member_coupon` MODIFY COLUMN `coupon_id` bigint unsigned NOT NULL COMMENT '优惠券ID';
ALTER TABLE `marketing_member_coupon` MODIFY COLUMN `member_id` bigint unsigned NOT NULL COMMENT '会员ID';
ALTER TABLE `marketing_member_coupon` MODIFY COLUMN `receive_status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '使用状态：0未使用，1已使用，2已过期';
ALTER TABLE `marketing_member_coupon` MODIFY COLUMN `received_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '领取时间';
ALTER TABLE `marketing_member_coupon` MODIFY COLUMN `used_at` datetime(3) DEFAULT NULL COMMENT '使用时间';
ALTER TABLE `marketing_member_coupon` MODIFY COLUMN `order_id` bigint unsigned DEFAULT NULL COMMENT '使用订单ID';
ALTER TABLE `marketing_member_coupon` MODIFY COLUMN `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间';
ALTER TABLE `marketing_member_coupon` MODIFY COLUMN `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间';
ALTER TABLE `marketing_member_coupon` MODIFY COLUMN `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除';
ALTER TABLE `marketing_member_coupon` MODIFY COLUMN `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本';

-- catalog_product_spec
ALTER TABLE `catalog_product_spec` MODIFY COLUMN `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID';
ALTER TABLE `catalog_product_spec` MODIFY COLUMN `product_id` bigint unsigned NOT NULL COMMENT '商品ID';
ALTER TABLE `catalog_product_spec` MODIFY COLUMN `spec_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规格名称';
ALTER TABLE `catalog_product_spec` MODIFY COLUMN `spec_value` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规格值';
ALTER TABLE `catalog_product_spec` MODIFY COLUMN `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号';
ALTER TABLE `catalog_product_spec` MODIFY COLUMN `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间';
ALTER TABLE `catalog_product_spec` MODIFY COLUMN `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间';
ALTER TABLE `catalog_product_spec` MODIFY COLUMN `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除';
ALTER TABLE `catalog_product_spec` MODIFY COLUMN `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本';

-- marketing_flash_sale_reservation
ALTER TABLE `marketing_flash_sale_reservation` MODIFY COLUMN `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID';
ALTER TABLE `marketing_flash_sale_reservation` MODIFY COLUMN `activity_id` bigint unsigned NOT NULL COMMENT '活动ID';
ALTER TABLE `marketing_flash_sale_reservation` MODIFY COLUMN `activity_item_id` bigint unsigned NOT NULL COMMENT '活动商品明细ID';
ALTER TABLE `marketing_flash_sale_reservation` MODIFY COLUMN `member_id` bigint unsigned NOT NULL COMMENT '会员ID';
ALTER TABLE `marketing_flash_sale_reservation` MODIFY COLUMN `order_id` bigint unsigned NOT NULL COMMENT '订单ID';
ALTER TABLE `marketing_flash_sale_reservation` MODIFY COLUMN `quantity` int unsigned NOT NULL COMMENT '预占数量';
ALTER TABLE `marketing_flash_sale_reservation` MODIFY COLUMN `status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '状态：0已预占，1已释放';
ALTER TABLE `marketing_flash_sale_reservation` MODIFY COLUMN `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间';
ALTER TABLE `marketing_flash_sale_reservation` MODIFY COLUMN `released_at` datetime(3) DEFAULT NULL COMMENT '释放时间';

-- catalog_product_feature
ALTER TABLE `catalog_product_feature` MODIFY COLUMN `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID';
ALTER TABLE `catalog_product_feature` MODIFY COLUMN `product_id` bigint unsigned NOT NULL COMMENT '商品ID';
ALTER TABLE `catalog_product_feature` MODIFY COLUMN `feature_text` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '卖点文案';
ALTER TABLE `catalog_product_feature` MODIFY COLUMN `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号';
ALTER TABLE `catalog_product_feature` MODIFY COLUMN `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间';
ALTER TABLE `catalog_product_feature` MODIFY COLUMN `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间';
ALTER TABLE `catalog_product_feature` MODIFY COLUMN `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除';
ALTER TABLE `catalog_product_feature` MODIFY COLUMN `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本';

-- member_compare_item
ALTER TABLE `member_compare_item` MODIFY COLUMN `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID';
ALTER TABLE `member_compare_item` MODIFY COLUMN `history_id` bigint unsigned NOT NULL COMMENT '对比历史ID';
ALTER TABLE `member_compare_item` MODIFY COLUMN `product_id` bigint unsigned NOT NULL COMMENT '商品ID';
ALTER TABLE `member_compare_item` MODIFY COLUMN `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号';
ALTER TABLE `member_compare_item` MODIFY COLUMN `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间';
ALTER TABLE `member_compare_item` MODIFY COLUMN `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间';
ALTER TABLE `member_compare_item` MODIFY COLUMN `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除';
ALTER TABLE `member_compare_item` MODIFY COLUMN `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本';

-- member_compare_history
ALTER TABLE `member_compare_history` MODIFY COLUMN `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID';
ALTER TABLE `member_compare_history` MODIFY COLUMN `member_id` bigint unsigned NOT NULL COMMENT '会员ID';
ALTER TABLE `member_compare_history` MODIFY COLUMN `compared_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '对比时间';
ALTER TABLE `member_compare_history` MODIFY COLUMN `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间';
ALTER TABLE `member_compare_history` MODIFY COLUMN `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间';
ALTER TABLE `member_compare_history` MODIFY COLUMN `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除';
ALTER TABLE `member_compare_history` MODIFY COLUMN `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本';

-- member_favorite
ALTER TABLE `member_favorite` MODIFY COLUMN `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID';
ALTER TABLE `member_favorite` MODIFY COLUMN `member_id` bigint unsigned NOT NULL COMMENT '会员ID';
ALTER TABLE `member_favorite` MODIFY COLUMN `product_id` bigint unsigned NOT NULL COMMENT '商品ID';
ALTER TABLE `member_favorite` MODIFY COLUMN `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间';
ALTER TABLE `member_favorite` MODIFY COLUMN `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间';
ALTER TABLE `member_favorite` MODIFY COLUMN `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除';
ALTER TABLE `member_favorite` MODIFY COLUMN `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本';
