-- =============================================================================
-- henfon-shop 数据库结构基线（不含业务数据）
-- =============================================================================
--
-- 用途
--   1. 在新环境快速创建完整的空库结构（60 张表）。
--   2. 作为结构与 db/init/ 迁移脚本的一致性审计基准。
--
-- 生成方式
--   由已落库的结构整体导出生成，请勿手工编辑。
--   结构来源：把 db/init/ 下全部脚本按编号顺序重放至一个空库，再整体导出。
--   之所以要重放而不是直接分析脚本，有三个原因：有 6 张表在 repair 脚本里被重复
--   CREATE；017/026/047/049 用 PREPARE/EXECUTE 动态拼 DDL，语句文本不在脚本里；
--   脚本里还混着数据迁移（015/028/033/034），静态分析分不清哪句改结构、哪句改数据。
--
-- 内容范围
--   ✅ 全部表结构：列、索引、外键、表/列注释、字符集与排序规则
--   ✅ 系统基础数据（6 行 / 3 张表）：默认仓库、物流承运商字典、配送模板
--   ❌ 不含任何业务数据（商品、订单、会员、库存流水等一律不导出）
--
-- 明确排除的内容
--   · RBAC 相关数据（部门、角色、菜单权限、admin 账号）由应用启动时自动写入，
--     见 shop-identity/.../config/IdentityDataInitializer.java，无需包含在脚本中。
--   · db/init/ 中的业务回填脚本（015 会员消费统计、033 商品默认 SKU 回填）在空库上
--     天然不产生任何行，因此本文件不含其产物。
--
-- 使用方式
--   mysql -h <host> -P <port> -u <user> -p < shop-admin/db/schema/henfon-shop.sql
--
-- 重要约定
--   · 全部使用 CREATE TABLE IF NOT EXISTS，不包含 DROP TABLE，重复执行不会破坏已有数据；
--     反过来说，它不会修改已存在的表——若表已存在但结构过旧，脚本会静默跳过，
--     此时应改用 db/init/ 下的增量迁移脚本。
--   · 目标库名默认为 henfon-shop，如需改库名请修改下方 CREATE DATABASE / USE 两行。
--
-- 已知结构不一致（来自线上库实测）
--   · db/init/045_payment_reconciliation_action.sql 与 046_catalog_product_audit.sql
--     使用了 MariaDB 专有的 ALTER TABLE ... ADD COLUMN IF NOT EXISTS，
--     MySQL 8.4 无法解析，两个脚本均执行失败。本文件已按两者的原始意图包含相应列。
--   · inventory_purchase_order / inventory_purchase_item / inventory_supplier_stock
--     三张表在源脚本中未声明 COLLATE，在 MySQL 8.4 下会落到 utf8mb4_0900_ai_ci，
--     与其余 56 张表的 utf8mb4_unicode_ci 不一致。
--   · 早期开发库的 content_notification 多出 remark 列，db/init/ 下没有任何脚本声明它，
--     因此本文件不含该列。052 在回填注释时对这列做了存在性判断，两种库结构均可执行。
-- =============================================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `henfon-shop`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `henfon-shop`;

SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================================
-- 第一部分：表结构（59 张表）
-- =============================================================================

-- ----------------------------
-- 表结构: catalog_category
-- ----------------------------
CREATE TABLE IF NOT EXISTS `catalog_category` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `parent_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT '父类目ID，根类目为0',
  `category_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类目名称',
  `category_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类目编码',
  `level_no` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '类目层级',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '状态：1启用，0停用',
  `icon_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '类目图标地址',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_catalog_category_code` (`category_code`,`is_deleted`),
  KEY `idx_catalog_category_parent_sort` (`parent_id`,`sort_no`),
  KEY `idx_catalog_category_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品类目表';

-- ----------------------------
-- 表结构: catalog_product
-- ----------------------------
CREATE TABLE IF NOT EXISTS `catalog_product` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `category_id` bigint unsigned DEFAULT NULL COMMENT '类目ID',
  `category_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '类目名称快照',
  `product_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '商品名称',
  `product_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '商品编码',
  `default_sku_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '默认SKU编码',
  `brand_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '品牌名称',
  `short_description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '商品摘要',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '商品描述',
  `price` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '销售价',
  `market_price` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '市场价',
  `cost_price` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '成本价',
  `current_stock` int NOT NULL DEFAULT '0' COMMENT '当前库存快照',
  `safety_stock` int NOT NULL DEFAULT '0' COMMENT '安全库存',
  `sales_count` bigint unsigned NOT NULL DEFAULT '0' COMMENT '累计销量',
  `main_image_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '主图地址',
  `tags_csv` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标签，逗号分隔',
  `status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '状态：0草稿，1上架，2下架',
  `audit_status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '审核状态：0待审核，1已通过，2已驳回',
  `audit_remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审核备注',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `weight_gram` int unsigned NOT NULL DEFAULT '1000' COMMENT '商品默认重量（克）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_catalog_product_code` (`product_code`,`is_deleted`),
  KEY `idx_catalog_product_category_status` (`category_id`,`status`),
  KEY `idx_catalog_product_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品主表';

-- ----------------------------
-- 表结构: catalog_product_feature
-- ----------------------------
CREATE TABLE IF NOT EXISTS `catalog_product_feature` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_id` bigint unsigned NOT NULL COMMENT '商品ID',
  `feature_text` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '卖点文案',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_catalog_feature_product_sort` (`product_id`,`sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品卖点表';

-- ----------------------------
-- 表结构: catalog_product_media
-- ----------------------------
CREATE TABLE IF NOT EXISTS `catalog_product_media` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_id` bigint unsigned NOT NULL COMMENT '商品ID',
  `sku_id` bigint unsigned DEFAULT NULL COMMENT 'SKU ID，可为空',
  `media_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'IMAGE' COMMENT '媒体类型：IMAGE、VIDEO',
  `object_key` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MinIO对象键',
  `media_url` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '媒体访问地址',
  `is_cover` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '是否主图：1是，0否',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_catalog_media_product_sort` (`product_id`,`sort_no`),
  KEY `idx_catalog_media_cover` (`product_id`,`is_cover`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品媒体表';

-- ----------------------------
-- 表结构: catalog_product_spec
-- ----------------------------
CREATE TABLE IF NOT EXISTS `catalog_product_spec` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_id` bigint unsigned NOT NULL COMMENT '商品ID',
  `spec_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规格名称',
  `spec_value` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规格值',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_catalog_spec_product_sort` (`product_id`,`sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品参数表';

-- ----------------------------
-- 表结构: catalog_sku
-- ----------------------------
CREATE TABLE IF NOT EXISTS `catalog_sku` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_id` bigint unsigned NOT NULL COMMENT '商品ID',
  `sku_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SKU编码',
  `barcode` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '商品条码',
  `sku_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SKU名称',
  `attributes_json` json DEFAULT NULL COMMENT 'SKU销售属性JSON',
  `price` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '销售价',
  `market_price` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '市场价',
  `cost_price` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '成本价',
  `stock` int NOT NULL DEFAULT '0' COMMENT '库存快照',
  `safety_stock` int NOT NULL DEFAULT '0' COMMENT '安全库存',
  `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '状态：1启用，0停用',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `weight_gram` int unsigned NOT NULL DEFAULT '1000' COMMENT 'SKU重量（克）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_catalog_sku_code` (`sku_code`,`is_deleted`),
  KEY `idx_catalog_sku_product_status` (`product_id`,`status`),
  KEY `idx_catalog_sku_barcode` (`barcode`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品SKU表';

-- ----------------------------
-- 表结构: content_banner
-- ----------------------------
CREATE TABLE IF NOT EXISTS `content_banner` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `banner_title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Banner标题',
  `banner_tag` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Banner标签',
  `subtitle` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '副标题',
  `image_url` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '图片地址',
  `link_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PRODUCT' COMMENT '跳转类型',
  `link_target` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '跳转目标',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '状态：1启用，0停用',
  `start_at` datetime(3) DEFAULT NULL COMMENT '开始时间',
  `end_at` datetime(3) DEFAULT NULL COMMENT '结束时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_content_banner_status_sort` (`status`,`sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门户Banner表';

-- ----------------------------
-- 表结构: content_email_delivery
-- ----------------------------
CREATE TABLE IF NOT EXISTS `content_email_delivery` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `member_id` bigint unsigned NOT NULL COMMENT '会员ID',
  `dedupe_key` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件幂等键',
  `recipient` varchar(320) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '收件人邮箱',
  `event_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件类型',
  `subject` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '邮件主题',
  `status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '投递状态：0待发送，1已发送，2发送中',
  `sending_token` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发送占用令牌',
  `sending_at` datetime(3) DEFAULT NULL COMMENT '开始发送时间',
  `sent_at` datetime(3) DEFAULT NULL COMMENT '发送成功时间',
  `last_error` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近一次发送错误',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_content_email_delivery_dedupe` (`member_id`,`dedupe_key`),
  KEY `idx_content_email_delivery_status` (`status`,`sending_at`),
  KEY `idx_content_email_delivery_recipient` (`recipient`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员业务邮件投递记录表';

-- ----------------------------
-- 表结构: content_notification
-- ----------------------------
CREATE TABLE IF NOT EXISTS `content_notification` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `member_id` bigint unsigned NOT NULL COMMENT '会员ID',
  `order_id` bigint unsigned DEFAULT NULL COMMENT '关联订单ID',
  `business_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联业务单号或业务标识',
  `event_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件类型',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知标题',
  `content` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知内容',
  `dedupe_key` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知幂等键',
  `read_status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '已读状态：0未读，1已读',
  `read_at` datetime(3) DEFAULT NULL COMMENT '阅读时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_content_notification_dedupe` (`member_id`,`dedupe_key`,`is_deleted`),
  KEY `idx_content_notification_member_read` (`member_id`,`read_status`,`created_at`),
  KEY `idx_content_notification_order` (`order_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员站内通知表';

-- ----------------------------
-- 表结构: content_review
-- ----------------------------
CREATE TABLE IF NOT EXISTS `content_review` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_id` bigint unsigned NOT NULL COMMENT '商品ID',
  `member_id` bigint unsigned DEFAULT NULL COMMENT '会员ID',
  `member_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会员名称快照',
  `member_avatar_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会员头像',
  `rating` tinyint unsigned NOT NULL DEFAULT '5' COMMENT '评分：1-5',
  `review_content` varchar(2000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评价内容',
  `variant_summary` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '购买规格',
  `image_urls` text COLLATE utf8mb4_unicode_ci COMMENT '评价图片 URL JSON 数组',
  `helpful_count` int unsigned NOT NULL DEFAULT '0' COMMENT '有帮助数量',
  `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '状态：1展示，0待审核，2审核未通过',
  `reviewed_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '评价时间',
  `reply_content` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '商家回复内容',
  `replied_at` datetime(3) DEFAULT NULL COMMENT '商家回复时间',
  `replied_by` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '回复管理员',
  `followup_content` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会员追评内容',
  `followup_at` datetime(3) DEFAULT NULL COMMENT '会员追评时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_content_review_product_status` (`product_id`,`status`,`reviewed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品评价表';

-- ----------------------------
-- 表结构: export_task
-- ----------------------------
CREATE TABLE IF NOT EXISTS `export_task` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_no` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务编号，形如 EXP20260916-0001，插入后由主键回填',
  `export_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '导出类型：PRODUCT/ORDER/MEMBER/COUPON/FINANCE/PRODUCT_RANKING/LOGIN_LOG/OPERATION_LOG/STOCK_LOG',
  `export_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '导出名称，如商品库数据导出',
  `query_params` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '提交时的筛选条件 JSON 快照，用于追溯与重跑',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING待执行/RUNNING生成中/SUCCESS成功/FAILED失败/EXPIRED已过期',
  `file_name` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '生成的文件名，含导出时间戳',
  `object_key` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Excel 文件在 MinIO 的对象键',
  `file_size` bigint unsigned DEFAULT NULL COMMENT '文件大小，单位字节',
  `row_count` int unsigned DEFAULT NULL COMMENT '导出的数据行数，不含表头',
  `error_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '失败原因，列表直接展示给提交人',
  `requested_by` bigint unsigned NOT NULL COMMENT '提交人管理员ID',
  `requested_by_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '提交人名称快照，避免账号改名后历史任务无法辨认',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '任务提交时间',
  `started_at` datetime(3) DEFAULT NULL COMMENT '开始生成时间',
  `finished_at` datetime(3) DEFAULT NULL COMMENT '生成结束时间',
  `claimed_at` datetime(3) DEFAULT NULL COMMENT '最近一次被认领执行的时间，超时未完成的任务据此判为僵尸任务',
  `expires_at` datetime(3) DEFAULT NULL COMMENT '文件过期时间，到期后清理 MinIO 对象并置为 EXPIRED',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '提交人是否已移除记录：1已移除，0正常',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_export_task_no` (`task_no`),
  KEY `idx_export_task_owner` (`requested_by`,`is_deleted`,`id`),
  KEY `idx_export_task_status` (`status`,`claimed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导出任务表';

-- ----------------------------
-- 表结构: inventory_purchase_item
-- ----------------------------
CREATE TABLE IF NOT EXISTS `inventory_purchase_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `purchase_order_id` bigint unsigned NOT NULL COMMENT '采购单ID',
  `product_id` bigint unsigned NOT NULL COMMENT '商品ID',
  `sku_id` bigint unsigned NOT NULL COMMENT 'SKU ID',
  `quantity` int unsigned NOT NULL COMMENT '采购数量',
  `received_quantity` int unsigned NOT NULL DEFAULT '0' COMMENT '已入库数量',
  `unit_price` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '采购单价',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_inventory_purchase_item_order` (`purchase_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存采购单明细表';

-- ----------------------------
-- 表结构: inventory_purchase_order
-- ----------------------------
CREATE TABLE IF NOT EXISTS `inventory_purchase_order` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `purchase_no` varchar(64) NOT NULL COMMENT '采购单号',
  `supplier_id` bigint unsigned NOT NULL COMMENT '供应商ID',
  `warehouse_id` bigint unsigned NOT NULL COMMENT '入库仓库ID',
  `status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '采购单状态：0待入库，1已入库',
  `total_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '采购总金额',
  `received_at` datetime(3) DEFAULT NULL COMMENT '入库完成时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_purchase_no` (`purchase_no`,`is_deleted`),
  KEY `idx_inventory_purchase_status` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存采购单表';

-- ----------------------------
-- 表结构: inventory_stock
-- ----------------------------
CREATE TABLE IF NOT EXISTS `inventory_stock` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `warehouse_id` bigint unsigned NOT NULL COMMENT '仓库ID',
  `product_id` bigint unsigned DEFAULT NULL COMMENT '商品ID',
  `sku_id` bigint unsigned NOT NULL COMMENT 'SKU ID',
  `available_stock` int unsigned NOT NULL DEFAULT '0' COMMENT '可用库存',
  `locked_stock` int unsigned NOT NULL DEFAULT '0' COMMENT '锁定库存',
  `sold_stock` bigint unsigned NOT NULL DEFAULT '0' COMMENT '已售库存',
  `safety_stock` int unsigned NOT NULL DEFAULT '0' COMMENT '安全库存',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_stock_warehouse_sku` (`warehouse_id`,`sku_id`,`is_deleted`),
  KEY `idx_inventory_stock_sku` (`sku_id`),
  KEY `idx_inventory_stock_available` (`available_stock`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存台账表';

-- ----------------------------
-- 表结构: inventory_stock_lock
-- ----------------------------
CREATE TABLE IF NOT EXISTS `inventory_stock_lock` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `lock_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '锁定流水号',
  `order_id` bigint unsigned NOT NULL COMMENT '订单ID',
  `order_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单号',
  `stock_id` bigint unsigned NOT NULL COMMENT '库存台账ID',
  `sku_id` bigint unsigned NOT NULL COMMENT 'SKU ID',
  `quantity` int unsigned NOT NULL COMMENT '锁定数量',
  `status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '状态：0锁定，1已释放，2已扣减',
  `expire_at` datetime(3) DEFAULT NULL COMMENT '锁定过期时间',
  `released_at` datetime(3) DEFAULT NULL COMMENT '释放时间',
  `deducted_at` datetime(3) DEFAULT NULL COMMENT '扣减时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_lock_no` (`lock_no`,`is_deleted`),
  KEY `idx_inventory_lock_order_status` (`order_id`,`status`),
  KEY `idx_inventory_lock_expire` (`status`,`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存锁定流水表';

-- ----------------------------
-- 表结构: inventory_stock_log
-- ----------------------------
CREATE TABLE IF NOT EXISTS `inventory_stock_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `stock_id` bigint unsigned NOT NULL COMMENT '库存台账ID',
  `sku_id` bigint unsigned NOT NULL COMMENT 'SKU ID',
  `biz_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务类型：RESERVE、RELEASE、DEDUCT、ADJUST',
  `biz_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务单号',
  `change_quantity` int NOT NULL COMMENT '库存变化量，正数增加、负数减少',
  `before_available` int unsigned NOT NULL COMMENT '变更前可用库存',
  `after_available` int unsigned NOT NULL COMMENT '变更后可用库存',
  `before_locked` int unsigned NOT NULL COMMENT '变更前锁定库存',
  `after_locked` int unsigned NOT NULL COMMENT '变更后锁定库存',
  `operator_id` bigint unsigned DEFAULT NULL COMMENT '操作人ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_inventory_log_stock_time` (`stock_id`,`created_at`),
  KEY `idx_inventory_log_biz` (`biz_type`,`biz_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存流水表';

-- ----------------------------
-- 表结构: inventory_stocktake
-- ----------------------------
CREATE TABLE IF NOT EXISTS `inventory_stocktake` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `take_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '盘点单号',
  `warehouse_id` bigint unsigned NOT NULL COMMENT '仓库ID',
  `status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '状态：0进行中，1已完成，2已取消',
  `operator_id` bigint unsigned DEFAULT NULL COMMENT '创建人ID',
  `started_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '盘点开始时间',
  `completed_at` datetime(3) DEFAULT NULL COMMENT '盘点完成时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '盘点备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_stocktake_no` (`take_no`,`is_deleted`),
  KEY `idx_inventory_stocktake_warehouse_status` (`warehouse_id`,`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存盘点单';

-- ----------------------------
-- 表结构: inventory_stocktake_item
-- ----------------------------
CREATE TABLE IF NOT EXISTS `inventory_stocktake_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `stocktake_id` bigint unsigned NOT NULL COMMENT '盘点单ID',
  `stock_id` bigint unsigned NOT NULL COMMENT '库存台账ID',
  `sku_id` bigint unsigned NOT NULL COMMENT 'SKU ID',
  `book_quantity` int unsigned NOT NULL DEFAULT '0' COMMENT '盘点开始账面可用库存',
  `actual_quantity` int unsigned DEFAULT NULL COMMENT '实盘数量',
  `difference_quantity` int NOT NULL DEFAULT '0' COMMENT '盘盈盘亏数量',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '明细备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_stocktake_item` (`stocktake_id`,`stock_id`,`is_deleted`),
  KEY `idx_inventory_stocktake_item_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存盘点明细';

-- ----------------------------
-- 表结构: inventory_supplier
-- ----------------------------
CREATE TABLE IF NOT EXISTS `inventory_supplier` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `supplier_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '供应商编码',
  `supplier_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '供应商名称',
  `contact_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系电话',
  `address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商地址',
  `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '状态：1启用，0停用',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_supplier_code` (`supplier_code`,`is_deleted`),
  KEY `idx_inventory_supplier_status` (`status`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存供应商表';

-- ----------------------------
-- 表结构: inventory_supplier_stock
-- ----------------------------
CREATE TABLE IF NOT EXISTS `inventory_supplier_stock` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `supplier_id` bigint unsigned NOT NULL COMMENT '供应商ID',
  `product_id` bigint unsigned NOT NULL COMMENT '商品ID',
  `sku_id` bigint unsigned NOT NULL COMMENT 'SKU ID',
  `supply_price` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '供货价',
  `min_order_quantity` int unsigned NOT NULL DEFAULT '1' COMMENT '最小起订量',
  `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '状态：1启用，0停用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_supplier_stock` (`supplier_id`,`sku_id`,`is_deleted`),
  KEY `idx_inventory_supplier_stock_sku` (`sku_id`,`status`),
  KEY `idx_inventory_supplier_stock_supplier` (`supplier_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存供应商供货关系表';

-- ----------------------------
-- 表结构: inventory_warehouse
-- ----------------------------
CREATE TABLE IF NOT EXISTS `inventory_warehouse` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `warehouse_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '仓库名称',
  `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '状态：1启用，0停用',
  `is_default` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '是否默认仓库：1是，0否',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `active_default_guard` tinyint GENERATED ALWAYS AS (if(((`status` = 1) and (`is_default` = 1) and (`is_deleted` = 0)),1,NULL)) STORED COMMENT '启用默认仓库唯一约束辅助列',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_warehouse_code` (`warehouse_code`,`is_deleted`),
  UNIQUE KEY `uk_inventory_warehouse_active_default` (`active_default_guard`),
  KEY `idx_inventory_warehouse_status` (`status`,`is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存仓库表';

-- ----------------------------
-- 表结构: marketing_coupon
-- ----------------------------
CREATE TABLE IF NOT EXISTS `marketing_coupon` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `coupon_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '优惠券编码',
  `coupon_title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '优惠券标题',
  `discount_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '优惠金额',
  `min_spend` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '最低消费金额',
  `category_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '适用类目编码，空为全场',
  `tag` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '展示标签',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '优惠券说明',
  `total_quantity` int unsigned NOT NULL DEFAULT '0' COMMENT '发行总量',
  `per_member_limit` int unsigned NOT NULL DEFAULT '1' COMMENT '单会员领取上限',
  `claimed_quantity` int unsigned NOT NULL DEFAULT '0' COMMENT '已领取数量',
  `start_at` datetime(3) NOT NULL COMMENT '生效时间',
  `end_at` datetime(3) NOT NULL COMMENT '失效时间',
  `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '状态：1启用，0停用',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_marketing_coupon_code` (`coupon_code`,`is_deleted`),
  KEY `idx_marketing_coupon_status_time` (`status`,`start_at`,`end_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='营销优惠券表';

-- ----------------------------
-- 表结构: marketing_coupon_usage
-- ----------------------------
CREATE TABLE IF NOT EXISTS `marketing_coupon_usage` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `coupon_id` bigint unsigned NOT NULL COMMENT '优惠券ID',
  `member_coupon_id` bigint unsigned NOT NULL COMMENT '会员优惠券ID',
  `member_id` bigint unsigned NOT NULL COMMENT '会员ID',
  `order_id` bigint unsigned NOT NULL COMMENT '订单ID',
  `discount_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '优惠金额',
  `action` tinyint unsigned NOT NULL COMMENT '动作：1核销，2回滚，3部分退款分摊',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_marketing_coupon_usage_action` (`member_coupon_id`,`order_id`,`action`),
  KEY `idx_marketing_coupon_usage_order` (`order_id`),
  KEY `idx_marketing_coupon_usage_member_time` (`member_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券核销流水表';

-- ----------------------------
-- 表结构: marketing_flash_sale
-- ----------------------------
CREATE TABLE IF NOT EXISTS `marketing_flash_sale` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `activity_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '活动编码',
  `activity_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '活动名称',
  `start_at` datetime(3) NOT NULL COMMENT '开始时间',
  `end_at` datetime(3) NOT NULL COMMENT '结束时间',
  `limit_per_member` int unsigned NOT NULL DEFAULT '1' COMMENT '会员限购数量',
  `status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '状态：0草稿，1启用，2停用',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_marketing_flash_sale_code` (`activity_code`,`is_deleted`),
  KEY `idx_marketing_flash_sale_window` (`status`,`start_at`,`end_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀促销活动表';

-- ----------------------------
-- 表结构: marketing_flash_sale_item
-- ----------------------------
CREATE TABLE IF NOT EXISTS `marketing_flash_sale_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `activity_id` bigint unsigned NOT NULL COMMENT '活动ID',
  `product_id` bigint unsigned NOT NULL COMMENT '商品ID',
  `sku_id` bigint unsigned DEFAULT NULL COMMENT 'SKU ID',
  `activity_price` decimal(18,2) NOT NULL COMMENT '活动价',
  `total_stock` int unsigned NOT NULL DEFAULT '0' COMMENT '活动库存',
  `sold_stock` int unsigned NOT NULL DEFAULT '0' COMMENT '已售库存',
  `limit_per_member` int unsigned NOT NULL DEFAULT '1' COMMENT '单会员限购数量',
  `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '状态：1启用，0停用',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_marketing_flash_sale_item` (`activity_id`,`product_id`,`sku_id`,`is_deleted`),
  KEY `idx_marketing_flash_sale_item_product` (`product_id`,`sku_id`),
  CONSTRAINT `fk_marketing_flash_sale_item_activity` FOREIGN KEY (`activity_id`) REFERENCES `marketing_flash_sale` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀促销活动商品表';

-- ----------------------------
-- 表结构: marketing_flash_sale_reservation
-- ----------------------------
CREATE TABLE IF NOT EXISTS `marketing_flash_sale_reservation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `activity_id` bigint unsigned NOT NULL COMMENT '活动ID',
  `activity_item_id` bigint unsigned NOT NULL COMMENT '活动商品明细ID',
  `member_id` bigint unsigned NOT NULL COMMENT '会员ID',
  `order_id` bigint unsigned NOT NULL COMMENT '订单ID',
  `quantity` int unsigned NOT NULL COMMENT '预占数量',
  `status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '状态：0已预占，1已释放',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `released_at` datetime(3) DEFAULT NULL COMMENT '释放时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flash_sale_reservation_order_item` (`order_id`,`activity_item_id`),
  KEY `idx_flash_sale_reservation_member` (`activity_id`,`activity_item_id`,`member_id`,`status`),
  KEY `idx_flash_sale_reservation_order` (`order_id`,`status`),
  KEY `fk_flash_sale_reservation_item` (`activity_item_id`),
  CONSTRAINT `fk_flash_sale_reservation_activity` FOREIGN KEY (`activity_id`) REFERENCES `marketing_flash_sale` (`id`),
  CONSTRAINT `fk_flash_sale_reservation_item` FOREIGN KEY (`activity_item_id`) REFERENCES `marketing_flash_sale_item` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀活动预约记录表';

-- ----------------------------
-- 表结构: marketing_member_coupon
-- ----------------------------
CREATE TABLE IF NOT EXISTS `marketing_member_coupon` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `coupon_id` bigint unsigned NOT NULL COMMENT '优惠券ID',
  `member_id` bigint unsigned NOT NULL COMMENT '会员ID',
  `receive_status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '使用状态：0未使用，1已使用，2已过期',
  `received_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '领取时间',
  `used_at` datetime(3) DEFAULT NULL COMMENT '使用时间',
  `order_id` bigint unsigned DEFAULT NULL COMMENT '使用订单ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_marketing_member_coupon_member_status` (`member_id`,`receive_status`),
  KEY `idx_marketing_member_coupon_member_coupon_status` (`member_id`,`coupon_id`,`receive_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员优惠券表';

-- ----------------------------
-- 表结构: member_address
-- ----------------------------
CREATE TABLE IF NOT EXISTS `member_address` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `member_id` bigint unsigned NOT NULL COMMENT '会员ID',
  `receiver_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '收货人',
  `receiver_phone` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '手机号',
  `province` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '省',
  `city` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '市',
  `district` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '区县',
  `detail_address` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '详细地址',
  `address_tag` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '地址标签',
  `is_default` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '是否默认地址',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `default_member_id` bigint unsigned GENERATED ALWAYS AS (if(((`is_default` = 1) and (`is_deleted` = 0)),`member_id`,NULL)) STORED COMMENT '默认地址唯一约束辅助列',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_address_default_member` (`default_member_id`),
  KEY `idx_member_address_member_default` (`member_id`,`is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员收货地址表';

-- ----------------------------
-- 表结构: member_asset_audit
-- ----------------------------
CREATE TABLE IF NOT EXISTS `member_asset_audit` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `member_id` bigint unsigned NOT NULL COMMENT '会员ID',
  `points_delta` bigint NOT NULL DEFAULT '0' COMMENT '积分变动值',
  `points_before` bigint NOT NULL DEFAULT '0' COMMENT '变动前积分',
  `points_after` bigint NOT NULL DEFAULT '0' COMMENT '变动后积分',
  `balance_delta` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '余额变动值',
  `balance_before` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '变动前余额',
  `balance_after` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '变动后余额',
  `operation` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ADMIN_ADJUST' COMMENT '操作类型',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作备注',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_member_asset_audit_member_time` (`member_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员资产审计流水表';

-- ----------------------------
-- 表结构: member_compare_history
-- ----------------------------
CREATE TABLE IF NOT EXISTS `member_compare_history` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `member_id` bigint unsigned NOT NULL COMMENT '会员ID',
  `compared_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '对比时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_member_compare_history_member_time` (`member_id`,`compared_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员商品对比历史表';

-- ----------------------------
-- 表结构: member_compare_item
-- ----------------------------
CREATE TABLE IF NOT EXISTS `member_compare_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `history_id` bigint unsigned NOT NULL COMMENT '对比历史ID',
  `product_id` bigint unsigned NOT NULL COMMENT '商品ID',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_member_compare_item_history_sort` (`history_id`,`sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员商品对比明细表';

-- ----------------------------
-- 表结构: member_consumption_stat
-- ----------------------------
CREATE TABLE IF NOT EXISTS `member_consumption_stat` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `member_id` bigint unsigned NOT NULL COMMENT '会员ID',
  `paid_order_count` bigint unsigned NOT NULL DEFAULT '0' COMMENT '有效已支付订单数',
  `paid_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '有效累计消费金额',
  `last_order_at` datetime(3) DEFAULT NULL COMMENT '最近支付时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_consumption_stat_member` (`member_id`),
  KEY `idx_member_consumption_stat_amount` (`paid_amount`),
  KEY `idx_member_consumption_stat_last_order` (`last_order_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员消费统计表';

-- ----------------------------
-- 表结构: member_favorite
-- ----------------------------
CREATE TABLE IF NOT EXISTS `member_favorite` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `member_id` bigint unsigned NOT NULL COMMENT '会员ID',
  `product_id` bigint unsigned NOT NULL COMMENT '商品ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_favorite_product` (`member_id`,`product_id`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员收藏商品表';

-- ----------------------------
-- 表结构: member_tag
-- ----------------------------
CREATE TABLE IF NOT EXISTS `member_tag` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT '租户ID',
  `tag_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签名称',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '状态：1启用，0停用',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_tag_name` (`tenant_id`,`tag_name`,`is_deleted`),
  KEY `idx_member_tag_status_sort` (`tenant_id`,`status`,`sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员标签表';

-- ----------------------------
-- 表结构: member_user
-- ----------------------------
CREATE TABLE IF NOT EXISTS `member_user` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT '租户/组织ID',
  `member_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会员编号',
  `username` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '登录用户名',
  `password_hash` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '密码哈希',
  `nickname` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会员昵称',
  `phone` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `email` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `avatar_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像地址',
  `member_level` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'REGULAR' COMMENT '会员等级',
  `points` bigint NOT NULL DEFAULT '0' COMMENT '积分余额',
  `balance` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '账户余额',
  `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '状态：1正常，0冻结',
  `registered_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '注册时间',
  `last_login_at` datetime(3) DEFAULT NULL COMMENT '最后登录时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_user_tenant_no` (`tenant_id`,`member_no`,`is_deleted`),
  UNIQUE KEY `uk_member_user_tenant_username` (`tenant_id`,`username`,`is_deleted`),
  UNIQUE KEY `uk_member_user_tenant_phone` (`tenant_id`,`phone`,`is_deleted`),
  UNIQUE KEY `uk_member_user_tenant_email` (`tenant_id`,`email`,`is_deleted`),
  KEY `idx_member_user_level_status` (`member_level`,`status`),
  KEY `idx_member_user_registered_at` (`registered_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商城会员表';

-- ----------------------------
-- 表结构: member_user_tag
-- ----------------------------
CREATE TABLE IF NOT EXISTS `member_user_tag` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `member_id` bigint unsigned NOT NULL COMMENT '会员ID',
  `tag_id` bigint unsigned NOT NULL COMMENT '标签ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_user_tag` (`member_id`,`tag_id`,`is_deleted`),
  KEY `idx_member_user_tag_member` (`member_id`,`is_deleted`),
  KEY `idx_member_user_tag_tag` (`tag_id`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员标签关联表';

-- ----------------------------
-- 表结构: payment_invoice
-- ----------------------------
CREATE TABLE IF NOT EXISTS `payment_invoice` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `invoice_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发票申请号',
  `order_id` bigint unsigned NOT NULL COMMENT '订单ID',
  `order_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单号快照',
  `member_id` bigint unsigned NOT NULL COMMENT '会员ID',
  `invoice_type` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '发票类型：1普通，2专用',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发票抬头',
  `tax_no` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '税号',
  `email` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '接收邮箱',
  `amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '开票金额',
  `status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '状态：0待开票，1开票中，2已开票，3失败，4取消',
  `invoice_url` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发票文件地址',
  `failure_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '失败原因',
  `requested_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '申请时间',
  `issued_at` datetime(3) DEFAULT NULL COMMENT '开票时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_invoice_no` (`invoice_no`,`is_deleted`),
  UNIQUE KEY `uk_payment_invoice_order` (`order_id`,`is_deleted`),
  KEY `idx_payment_invoice_member_status` (`member_id`,`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单发票申请表';

-- ----------------------------
-- 表结构: payment_order
-- ----------------------------
CREATE TABLE IF NOT EXISTS `payment_order` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `payment_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '支付单号',
  `order_id` bigint unsigned NOT NULL COMMENT '订单ID',
  `order_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单号',
  `member_id` bigint unsigned DEFAULT NULL COMMENT '会员ID',
  `channel` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '支付渠道：WECHAT_NATIVE等',
  `status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '状态：0待支付，1支付中，2成功，3已关闭，4失败',
  `amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '支付金额',
  `transaction_no` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '第三方交易号',
  `notify_payload` text COLLATE utf8mb4_unicode_ci COMMENT '最近一次异步通知原文',
  `paid_at` datetime(3) DEFAULT NULL COMMENT '支付成功时间',
  `expire_at` datetime(3) DEFAULT NULL COMMENT '支付过期时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `reconciliation_status` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '人工对账状态覆盖值',
  `reconciliation_remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '人工对账处理备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_order_no` (`payment_no`,`is_deleted`),
  UNIQUE KEY `uk_payment_order_order_channel` (`order_id`,`channel`,`is_deleted`),
  KEY `idx_payment_order_member_status` (`member_id`,`status`,`created_at`),
  KEY `idx_payment_order_expire` (`status`,`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付单表';

-- ----------------------------
-- 表结构: payment_refund_order
-- ----------------------------
CREATE TABLE IF NOT EXISTS `payment_refund_order` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `refund_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '退款单号',
  `idempotency_key` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '退款幂等键',
  `payment_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '支付单号',
  `order_id` bigint unsigned NOT NULL COMMENT '订单ID',
  `order_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单号',
  `member_id` bigint unsigned DEFAULT NULL COMMENT '会员ID',
  `amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '退款金额',
  `reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '退款原因',
  `status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '状态：0待退款，1退款中，2成功，3失败，4关闭',
  `transaction_no` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '第三方退款交易号',
  `notify_payload` text COLLATE utf8mb4_unicode_ci COMMENT '最近一次退款通知原文',
  `requested_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '申请时间',
  `refunded_at` datetime(3) DEFAULT NULL COMMENT '退款成功时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `reconciliation_status` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '人工对账状态覆盖值',
  `reconciliation_remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '人工对账处理备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_refund_no` (`refund_no`,`is_deleted`),
  UNIQUE KEY `uk_payment_refund_idempotency` (`idempotency_key`,`is_deleted`),
  KEY `idx_payment_refund_payment_status` (`payment_no`,`status`,`created_at`),
  KEY `idx_payment_refund_order_status` (`order_id`,`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付退款单表';

-- ----------------------------
-- 表结构: reporting_event_projection
-- ----------------------------
CREATE TABLE IF NOT EXISTS `reporting_event_projection` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `event_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '领域事件唯一编号',
  `event_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件类型',
  `topic` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'RocketMQ主题',
  `aggregate_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '聚合根编号',
  `occurred_at` datetime(3) DEFAULT NULL COMMENT '事件发生时间',
  `payload` json DEFAULT NULL COMMENT '事件载荷',
  `projected_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '投影处理时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reporting_event_projection_event` (`event_id`,`is_deleted`),
  KEY `idx_reporting_event_projection_type_time` (`event_type`,`occurred_at`),
  KEY `idx_reporting_event_projection_aggregate` (`aggregate_id`,`occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报表领域事件投影表';

-- ----------------------------
-- 表结构: sys_config
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_config` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT '租户/组织ID',
  `config_json` json NOT NULL COMMENT '系统配置JSON',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_config_tenant` (`tenant_id`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户级系统配置表';

-- ----------------------------
-- 表结构: sys_data_rule
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_data_rule` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT '租户/组织ID',
  `rule_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则名称',
  `module_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务模块标识',
  `scope_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '范围类型：ALL、DEPT_AND_SUB、DEPT、SELF、CUSTOM',
  `custom_dept_ids` json DEFAULT NULL COMMENT '自定义部门ID列表',
  `field_masks` json DEFAULT NULL COMMENT '字段脱敏配置',
  `filter_expression` text COLLATE utf8mb4_unicode_ci COMMENT '行级过滤表达式',
  `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '状态：1启用，0停用',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_sys_data_rule_module_status` (`module_key`,`status`),
  KEY `idx_sys_data_rule_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统数据权限规则表';

-- ----------------------------
-- 表结构: sys_dept
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_dept` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT '租户/组织ID',
  `parent_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT '父部门ID，根部门为0',
  `dept_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '部门名称',
  `dept_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '部门编码',
  `leader_user_id` bigint unsigned DEFAULT NULL COMMENT '负责人用户ID',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '状态：1启用，0停用',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_dept_tenant_code` (`tenant_id`,`dept_code`,`is_deleted`),
  KEY `idx_sys_dept_parent_sort` (`parent_id`,`sort_no`),
  KEY `idx_sys_dept_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统部门表';

-- ----------------------------
-- 表结构: sys_dictionary_item
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_dictionary_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT '租户ID，0表示平台公共字典',
  `dict_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典类型',
  `item_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典编码',
  `item_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典名称',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序值',
  `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '状态：1启用，0停用',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_dict_item` (`tenant_id`,`dict_type`,`item_code`,`is_deleted`),
  KEY `idx_sys_dict_type_status_sort` (`dict_type`,`status`,`sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统字典项表';

-- ----------------------------
-- 表结构: sys_login_log
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_login_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint unsigned DEFAULT NULL COMMENT '系统用户ID',
  `username` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录用户名',
  `login_status` tinyint unsigned NOT NULL COMMENT '登录结果：1成功，0失败',
  `login_ip` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '登录IP',
  `user_agent` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户端信息',
  `failure_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '失败原因',
  `login_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '登录时间',
  PRIMARY KEY (`id`),
  KEY `idx_sys_login_log_user_time` (`user_id`,`login_at`),
  KEY `idx_sys_login_log_username_time` (`username`,`login_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统登录日志表';

-- ----------------------------
-- 表结构: sys_menu
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_menu` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `parent_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT '父菜单ID，根节点为0',
  `menu_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '菜单名称',
  `menu_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '菜单类型：DIRECTORY、MENU、BUTTON',
  `route_path` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '路由路径',
  `component` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '前端组件路径',
  `icon` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '菜单图标',
  `permission_code` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '权限标识',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `visible` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '是否可见：1是，0否',
  `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '状态：1启用，0停用',
  `keep_alive` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '是否缓存页面：1是，0否',
  `external_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '外部链接',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_menu_permission` (`permission_code`,`is_deleted`),
  KEY `idx_sys_menu_parent_sort` (`parent_id`,`sort_no`),
  KEY `idx_sys_menu_type_status` (`menu_type`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统菜单与按钮权限表';

-- ----------------------------
-- 表结构: sys_oper_log
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_oper_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `trace_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求链路ID',
  `user_id` bigint unsigned DEFAULT NULL COMMENT '操作用户ID',
  `username` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作用户名',
  `module_key` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务模块标识',
  `operation` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作描述',
  `request_method` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求方法',
  `request_uri` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求地址',
  `request_params` json DEFAULT NULL COMMENT '请求参数',
  `response_status` int DEFAULT NULL COMMENT '响应状态码',
  `client_ip` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户端IP',
  `duration_ms` bigint unsigned DEFAULT NULL COMMENT '耗时毫秒',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_sys_oper_log_user_time` (`user_id`,`created_at`),
  KEY `idx_sys_oper_log_module_time` (`module_key`,`created_at`),
  KEY `idx_sys_oper_log_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统操作审计日志表';

-- ----------------------------
-- 表结构: sys_role
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_role` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT '租户/组织ID',
  `role_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色标识',
  `role_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
  `role_sort` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '状态：1启用，0停用',
  `data_scope` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SELF' COMMENT '数据范围：ALL、CUSTOM、DEPT、DEPT_AND_SUB、SELF',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '角色描述',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_tenant_key` (`tenant_id`,`role_key`,`is_deleted`),
  KEY `idx_sys_role_status_sort` (`status`,`role_sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';

-- ----------------------------
-- 表结构: sys_role_data_rule
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_role_data_rule` (
  `role_id` bigint unsigned NOT NULL COMMENT '角色ID',
  `rule_id` bigint unsigned NOT NULL COMMENT '数据规则ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`role_id`,`rule_id`),
  KEY `idx_sys_role_data_rule_rule_id` (`rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色数据权限规则关联表';

-- ----------------------------
-- 表结构: sys_role_menu
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_role_menu` (
  `role_id` bigint unsigned NOT NULL COMMENT '角色ID',
  `menu_id` bigint unsigned NOT NULL COMMENT '菜单或按钮ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`role_id`,`menu_id`),
  KEY `idx_sys_role_menu_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色菜单权限关联表';

-- ----------------------------
-- 表结构: sys_user
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT '租户/组织ID，默认平台组织',
  `username` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录用户名',
  `password_hash` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码哈希',
  `real_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '真实姓名',
  `nickname` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '昵称',
  `phone` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `email` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `avatar_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像地址',
  `dept_id` bigint unsigned DEFAULT NULL COMMENT '所属部门ID',
  `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '状态：1启用，0停用',
  `user_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ADMIN' COMMENT '用户类型',
  `last_login_at` datetime(3) DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最后登录IP',
  `password_updated_at` datetime(3) DEFAULT NULL COMMENT '密码更新时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_tenant_username` (`tenant_id`,`username`,`is_deleted`),
  UNIQUE KEY `uk_sys_user_tenant_phone` (`tenant_id`,`phone`,`is_deleted`),
  UNIQUE KEY `uk_sys_user_tenant_email` (`tenant_id`,`email`,`is_deleted`),
  KEY `idx_sys_user_dept_status` (`dept_id`,`status`),
  KEY `idx_sys_user_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- ----------------------------
-- 表结构: sys_user_role
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `user_id` bigint unsigned NOT NULL COMMENT '系统用户ID',
  `role_id` bigint unsigned NOT NULL COMMENT '角色ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`user_id`,`role_id`),
  KEY `idx_sys_user_role_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户角色关联表';

-- ----------------------------
-- 表结构: trade_after_sale
-- ----------------------------
CREATE TABLE IF NOT EXISTS `trade_after_sale` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `after_sale_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '售后单号',
  `order_id` bigint unsigned NOT NULL COMMENT '订单ID',
  `order_item_id` bigint unsigned DEFAULT NULL COMMENT '订单明细ID',
  `member_id` bigint unsigned DEFAULT NULL COMMENT '会员ID',
  `after_sale_type` tinyint unsigned NOT NULL COMMENT '售后类型：1仅退款，2退货退款，3换货',
  `status` tinyint unsigned NOT NULL DEFAULT '10' COMMENT '状态：10待审核，20处理中，30已完成，40已拒绝，50已取消，60退款失败',
  `reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '申请原因',
  `evidence_urls` text COLLATE utf8mb4_unicode_ci COMMENT '售后凭证 URL JSON 数组',
  `refund_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '退款金额',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `active_target_key` varchar(100) COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case when ((`status` in (10,20)) and (`is_deleted` = 0)) then concat(`order_id`,_utf8mb4':',coalesce(`order_item_id`,0)) else NULL end)) STORED COMMENT '处理中售后目标唯一键',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trade_after_sale_no` (`after_sale_no`,`is_deleted`),
  UNIQUE KEY `uk_trade_after_sale_active_target` (`active_target_key`),
  KEY `idx_trade_after_sale_order` (`order_id`,`status`),
  KEY `idx_trade_after_sale_member_status` (`member_id`,`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易售后表';

-- ----------------------------
-- 表结构: trade_cart_item
-- ----------------------------
CREATE TABLE IF NOT EXISTS `trade_cart_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `member_id` bigint unsigned NOT NULL COMMENT '会员ID',
  `product_id` bigint unsigned NOT NULL COMMENT '商品ID',
  `sku_id` bigint unsigned DEFAULT NULL COMMENT 'SKU ID',
  `quantity` int unsigned NOT NULL DEFAULT '1' COMMENT '数量',
  `selected` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '是否选中：1是，0否',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trade_cart_member_sku` (`member_id`,`product_id`,`sku_id`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车明细表';

-- ----------------------------
-- 表结构: trade_event_outbox
-- ----------------------------
CREATE TABLE IF NOT EXISTS `trade_event_outbox` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `event_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件唯一标识',
  `event_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件类型',
  `aggregate_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '聚合类型',
  `aggregate_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '聚合ID',
  `topic` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'RocketMQ主题',
  `payload` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件载荷JSON',
  `status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '状态：0待发送，1已发送，2死信',
  `retry_count` int unsigned NOT NULL DEFAULT '0' COMMENT '重试次数',
  `next_retry_at` datetime(3) DEFAULT NULL COMMENT '下一次重试时间',
  `published_at` datetime(3) DEFAULT NULL COMMENT '发送成功时间',
  `last_error` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近一次发送错误',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `manual_retry_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '人工重试操作人',
  `manual_retry_at` datetime(3) DEFAULT NULL COMMENT '人工重试时间',
  `manual_retry_result` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '人工重试结果',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trade_event_outbox_event` (`event_id`,`is_deleted`),
  KEY `idx_trade_event_outbox_pending` (`status`,`next_retry_at`,`id`),
  KEY `idx_trade_event_outbox_aggregate` (`aggregate_type`,`aggregate_id`),
  KEY `idx_trade_event_outbox_manual_retry` (`manual_retry_at`,`manual_retry_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易领域事件Outbox表';

-- ----------------------------
-- 表结构: trade_event_outbox_retry_audit
-- ----------------------------
CREATE TABLE IF NOT EXISTS `trade_event_outbox_retry_audit` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `event_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Outbox事件标识',
  `event_type` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '事件类型',
  `operator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人',
  `result` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '结果：SUCCESS/FAILED',
  `error_message` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '失败原因',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_outbox_retry_audit_event` (`event_id`,`created_at`),
  KEY `idx_outbox_retry_audit_operator` (`operator`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Outbox人工补偿审计记录';

-- ----------------------------
-- 表结构: trade_freight_template
-- ----------------------------
CREATE TABLE IF NOT EXISTS `trade_freight_template` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `template_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板名称',
  `carrier_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '承运商名称，发货时由承运商字典选择',
  `base_weight_gram` int unsigned NOT NULL DEFAULT '1000' COMMENT '首重（克）',
  `base_fee` decimal(18,2) NOT NULL DEFAULT '15.00' COMMENT '首重费用',
  `additional_weight_gram` int unsigned NOT NULL DEFAULT '1000' COMMENT '续重计费单位（克）',
  `additional_fee` decimal(18,2) NOT NULL DEFAULT '5.00' COMMENT '每续重单位费用',
  `free_shipping_threshold` decimal(18,2) NOT NULL DEFAULT '99.00' COMMENT '包邮门槛，0表示不包邮',
  `remote_surcharge` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '偏远地区附加费',
  `remote_regions_csv` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '偏远地区关键词，逗号分隔',
  `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '状态：1启用，0停用',
  `is_default` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '是否默认模板：1是，0否',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trade_freight_template_name` (`template_name`,`is_deleted`),
  KEY `idx_trade_freight_template_default` (`status`,`is_default`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易运费模板表';

-- ----------------------------
-- 表结构: trade_order
-- ----------------------------
CREATE TABLE IF NOT EXISTS `trade_order` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单号',
  `idempotency_key` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会员订单幂等键',
  `member_id` bigint unsigned DEFAULT NULL COMMENT '会员ID',
  `member_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会员名称快照',
  `order_status` tinyint unsigned NOT NULL DEFAULT '10' COMMENT '订单状态：10待付款，20待发货，30已发货，40已完成，50已取消，60退款中，70已退款',
  `audit_status` tinyint unsigned NOT NULL DEFAULT '10' COMMENT '审核状态：10待审核，20已通过，30已驳回',
  `audit_remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审核备注',
  `audited_at` datetime(3) DEFAULT NULL COMMENT '审核时间',
  `audited_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审核管理员用户名',
  `payment_status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '支付状态：0未支付，1已支付，2已退款',
  `payment_method` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '支付方式',
  `subtotal_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '商品金额',
  `discount_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '优惠金额',
  `freight_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '运费',
  `payable_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '应付金额',
  `paid_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '实付金额',
  `receiver_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '收货人',
  `receiver_phone` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '收货电话',
  `receiver_province` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '省',
  `receiver_city` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '市',
  `receiver_district` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '区县',
  `receiver_address` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '详细地址',
  `buyer_remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '买家备注',
  `seller_remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '卖家备注',
  `flag_color` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '订单运营标旗颜色',
  `logistics_company` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '物流公司',
  `tracking_no` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '物流单号',
  `paid_at` datetime(3) DEFAULT NULL COMMENT '支付时间',
  `shipped_at` datetime(3) DEFAULT NULL COMMENT '发货时间',
  `completed_at` datetime(3) DEFAULT NULL COMMENT '完成时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trade_order_no` (`order_no`,`is_deleted`),
  UNIQUE KEY `uk_trade_order_member_idempotency` (`member_id`,`idempotency_key`,`is_deleted`),
  KEY `idx_trade_order_member_status` (`member_id`,`order_status`),
  KEY `idx_trade_order_created_at` (`created_at`),
  KEY `idx_trade_order_audit_status` (`audit_status`,`order_status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易订单表';

-- ----------------------------
-- 表结构: trade_order_item
-- ----------------------------
CREATE TABLE IF NOT EXISTS `trade_order_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` bigint unsigned NOT NULL COMMENT '订单ID',
  `product_id` bigint unsigned DEFAULT NULL COMMENT '商品ID',
  `sku_id` bigint unsigned DEFAULT NULL COMMENT 'SKU ID',
  `product_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '商品名称快照',
  `sku_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SKU名称快照',
  `sku_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SKU编码快照',
  `image_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '商品图片快照',
  `unit_price` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '成交单价',
  `quantity` int unsigned NOT NULL DEFAULT '1' COMMENT '购买数量',
  `item_amount` decimal(18,2) NOT NULL DEFAULT '0.00' COMMENT '明细金额',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1是，0否',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_trade_order_item_order` (`order_id`),
  KEY `idx_trade_order_item_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易订单明细表';

-- ----------------------------
-- 表结构: trade_order_logistics
-- ----------------------------
CREATE TABLE IF NOT EXISTS `trade_order_logistics` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` bigint unsigned NOT NULL COMMENT '订单ID',
  `tracking_no` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物流单号',
  `logistics_company` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物流公司',
  `logistics_status` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '物流状态',
  `event_time` datetime(3) NOT NULL COMMENT '节点时间',
  `event_description` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点描述',
  `event_location` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '节点地点',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_trade_logistics_order_time` (`order_id`,`event_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单物流轨迹表';

-- =============================================================================
-- 第二部分：系统基础数据（6 行）
-- 说明：以下为应用运行所需的基础配置数据，属于系统初始化数据而非业务数据。
-- =============================================================================

-- ----------------------------
-- 系统基础数据: inventory_warehouse（非业务数据）
-- ----------------------------
INSERT INTO `inventory_warehouse` (`id`, `warehouse_code`, `warehouse_name`, `status`, `is_default`, `created_at`, `updated_at`, `is_deleted`, `version`, `remark`) VALUES (1,'DEFAULT','默认仓库',1,1,'2026-09-16 13:08:09.842','2026-09-16 13:08:09.842',0,0,NULL);

-- ----------------------------
-- 系统基础数据: sys_dictionary_item（非业务数据）
-- ----------------------------
INSERT INTO `sys_dictionary_item` (`id`, `tenant_id`, `dict_type`, `item_code`, `item_name`, `sort_no`, `status`, `created_at`, `updated_at`, `is_deleted`, `remark`) VALUES (1,0,'LOGISTICS_CARRIER','SF','顺丰速运',10,1,'2026-09-16 13:08:12.544','2026-09-16 13:08:12.544',0,NULL),(2,0,'LOGISTICS_CARRIER','ZTO','中通快递',20,1,'2026-09-16 13:08:12.544','2026-09-16 13:08:12.544',0,NULL),(3,0,'LOGISTICS_CARRIER','YTO','圆通速递',30,1,'2026-09-16 13:08:12.544','2026-09-16 13:08:12.544',0,NULL),(4,0,'LOGISTICS_CARRIER','JD','京东快递',40,1,'2026-09-16 13:08:12.544','2026-09-16 13:08:12.544',0,NULL);

-- ----------------------------
-- 系统基础数据: trade_freight_template（非业务数据）
-- ----------------------------
INSERT INTO `trade_freight_template` (`id`, `template_name`, `carrier_name`, `base_weight_gram`, `base_fee`, `additional_weight_gram`, `additional_fee`, `free_shipping_threshold`, `remote_surcharge`, `remote_regions_csv`, `status`, `is_default`, `created_at`, `updated_at`, `is_deleted`, `version`, `remark`) VALUES (1,'全国配送模板',NULL,1000,15.00,1000,5.00,99.00,0.00,'西藏,新疆,港澳台',1,1,'2026-09-16 13:08:11.694','2026-09-16 13:08:11.694',0,0,NULL);


SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 结束
-- =============================================================================
