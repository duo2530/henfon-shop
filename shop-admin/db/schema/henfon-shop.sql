-- =============================================================================
-- henfon-shop 数据库结构基线（不含业务数据）
-- =============================================================================
--
-- 用途
--   1. 在新环境快速创建完整的空库结构（69 张表）。
--   2. 作为结构与 db/init/ 迁移脚本的一致性审计基准。
--
-- 生成方式
--   由迁移脚本重放后的库结构导出，请勿手工编辑。
--   步骤：把 db/init/ 下全部脚本按编号顺序重放至一个空库，用 mysqldump --no-data 导出，
--   再归一化（去掉 DROP TABLE 与 AUTO_INCREMENT 计数，建表改为 IF NOT EXISTS）。
--   重放而不是静态分析脚本的原因：有 6 张表在 repair 脚本里被重复 CREATE；
--   017/026/047/049 用 PREPARE/EXECUTE 动态拼 DDL，语句文本不在脚本里；
--   脚本里还混着数据迁移（015/028/033/034/056），静态分析分不清哪句改结构、哪句改数据。
--
-- 内容范围
--   全部表结构：列、索引、外键、表/列注释、字符集与排序规则。
--   系统基础数据（26 行 / 4 张表）：默认仓库、物流承运商字典、配送模板，
--   以及 056 写入的 20 条政策问答初始条目。
--   不含业务数据：商品、订单、会员、库存流水等一律不导出，演示数据见 db/seed/。
--
-- 排除的内容
--   RBAC 相关数据（部门、角色、菜单权限、admin 账号）由应用启动时自动写入，
--   见 shop-identity/.../config/IdentityDataInitializer.java。
--   db/init 中的业务回填脚本（015 会员消费统计、033 商品默认 SKU 回填）在空库上不产生
--   任何行，因此本文件不含其产物。
--
-- 使用方式
--   mysql -h <host> -P <port> -u <user> -p < shop-admin/db/schema/henfon-shop.sql
--   面向空库执行：建表用 IF NOT EXISTS，但基础数据是普通 INSERT，
--   在已有库上重复执行会遇到主键冲突。
--
-- 重放说明
--   045_payment_reconciliation_action.sql 与 046_catalog_product_audit.sql 使用了 MariaDB
--   专有的 ALTER TABLE ... ADD COLUMN IF NOT EXISTS，MySQL 8.4 无法解析。重放时按空库语义
--   去掉 IF NOT EXISTS 即可成功，得到的列定义与两个脚本的原始意图一致。
--
-- 已知结构不一致
--   inventory_purchase_order / inventory_purchase_item / inventory_supplier_stock
--   三张表在源脚本中未声明 COLLATE，MySQL 8.4 下落到 utf8mb4_0900_ai_ci，与其余
--   66 张表的 utf8mb4_unicode_ci 不一致。本文件忠实沿用，不做归一化。
--   存量开发库与这里另有 15 处差异：12 处列注释停留在旧版本、content_notification 多出
--   remark 列、marketing_flash_sale 两个列是 utf8mb4_0900_ai_ci —— 037 与 052 从未在那些
--   库上执行过。要用脚本补齐应新增编号脚本，不要直接改库。
-- =============================================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `henfon-shop`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `henfon-shop`;

SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================================
-- 第一部分：表结构（69 张表）
-- =============================================================================

-- ----------------------------
-- 表结构: ai_agent_status
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_agent_status` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `agent_id` bigint unsigned NOT NULL COMMENT '坐席管理员ID',
  `agent_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '坐席名称快照，避免账号改名后历史记录无法辨认',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OFFLINE' COMMENT '坐席状态：ONLINE在线/BREAK小休/OFFLINE离线',
  `last_heartbeat_at` datetime(3) DEFAULT NULL COMMENT '最近心跳时间，超过阈值即视为掉线',
  `online_at` datetime(3) DEFAULT NULL COMMENT '本次上线时间',
  `offline_at` datetime(3) DEFAULT NULL COMMENT '本次下线时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_agent_status_agent` (`agent_id`),
  KEY `idx_ai_agent_status_live` (`status`,`last_heartbeat_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 客服坐席在线状态表';

-- ----------------------------
-- 表结构: ai_conversation
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_conversation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `conversation_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话标识，对外暴露，由服务端生成',
  `channel` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '入口渠道：PORTAL门户买家/ADMIN管理端助手',
  `member_id` bigint unsigned DEFAULT NULL COMMENT '所属会员ID，未登录会话为空',
  `admin_id` bigint unsigned DEFAULT NULL COMMENT '所属管理员ID，管理端会话使用',
  `subject_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会话绑定的业务对象类型，如 PRODUCT/ORDER',
  `subject_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会话绑定的业务对象标识',
  `title` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会话标题，取首条用户提问的前若干字',
  `message_count` int unsigned NOT NULL DEFAULT '0' COMMENT '消息条数，含用户与助手消息',
  `last_message_at` datetime(3) DEFAULT NULL COMMENT '最近一条消息时间，会话列表按它倒序',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '会话状态：ACTIVE进行中/TICKETED已转人工/CLOSED已结束',
  `service_mode` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'AI' COMMENT '接待模式：AI智能客服/WAITING等待人工/HUMAN人工接待中',
  `agent_id` bigint unsigned DEFAULT NULL COMMENT '接管会话的管理员ID，未接管为空',
  `agent_requested_at` datetime(3) DEFAULT NULL COMMENT '买家请求转人工的时间，等待时长按它计算',
  `agent_joined_at` datetime(3) DEFAULT NULL COMMENT '客服接入时间',
  `agent_ended_at` datetime(3) DEFAULT NULL COMMENT '人工会话结束时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_conversation_id` (`conversation_id`),
  KEY `idx_ai_conversation_member` (`channel`,`member_id`,`last_message_at`),
  KEY `idx_ai_conversation_admin` (`channel`,`admin_id`,`last_message_at`),
  KEY `idx_ai_conversation_waiting` (`service_mode`,`agent_requested_at`),
  KEY `idx_ai_conversation_agent` (`agent_id`,`service_mode`,`last_message_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 客服会话表';

-- ----------------------------
-- 表结构: ai_document
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_document` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档标题',
  `doc_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'POLICY' COMMENT '文档类型：POLICY政策/SOP流程/OTHER其他',
  `source` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '原文来源，MinIO 对象键或外部链接',
  `doc_version` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文档版本号，由运营人工维护',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '索引状态：DRAFT草稿/INDEXED已索引/FAILED索引失败',
  `chunk_count` int unsigned NOT NULL DEFAULT '0' COMMENT '切分后的片段数量',
  `error_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '索引失败原因，直接展示给运营',
  `indexed_at` datetime(3) DEFAULT NULL COMMENT '最近一次索引完成时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1已删除，0正常',
  PRIMARY KEY (`id`),
  KEY `idx_ai_document_status` (`status`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 知识文档表';

-- ----------------------------
-- 表结构: ai_faq
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_faq` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `question` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标准问法，同时作为向量化文本的来源',
  `answer` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标准答案，命中后直接拼进模型上下文',
  `category` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务分类：AFTER_SALE/SHIPPING/INVOICE/PAYMENT/MEMBER/OTHER',
  `keywords` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '人工维护的检索关键词，英文逗号分隔，供关键词兜底召回',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '同分类内展示顺序，值小的在前',
  `enabled` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '是否启用：1启用，0停用。停用后不参与向量化',
  `sync_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '向量同步状态：PENDING待同步/SYNCED已同步/FAILED失败',
  `synced_at` datetime(3) DEFAULT NULL COMMENT '最近一次向量化成功时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：1已删除，0正常',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_ai_faq_category` (`category`,`enabled`,`sort_no`),
  KEY `idx_ai_faq_sync` (`sync_status`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 客服知识库问答表';

-- ----------------------------
-- 表结构: ai_message
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_message` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `conversation_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属会话标识',
  `sequence` int unsigned NOT NULL COMMENT '会话内消息序号，从 1 递增，读回上下文按它排序',
  `role` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息角色：USER用户/ASSISTANT助手/TOOL工具/SYSTEM系统',
  `sender` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发送方：MEMBER买家/AI智能客服/AGENT人工客服；工具与系统消息为空',
  `content` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '消息正文，纯工具调用消息可为空',
  `tool_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工具调用名称，工具消息才有',
  `tool_payload` text COLLATE utf8mb4_unicode_ci COMMENT '工具调用、检索引文与商品卡片的 JSON 快照，便于排查错误回答并支持消息回读',
  `model` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '生成该条消息的模型名',
  `tokens_in` int unsigned DEFAULT NULL COMMENT '输入 token 数',
  `tokens_out` int unsigned DEFAULT NULL COMMENT '输出 token 数',
  `latency_ms` int unsigned DEFAULT NULL COMMENT '本次响应耗时，单位毫秒，用于效果分析',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_message_seq` (`conversation_id`,`sequence`),
  KEY `idx_ai_message_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 客服消息明细表';

-- ----------------------------
-- 表结构: ai_rating
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_rating` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `conversation_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '被评价的会话标识',
  `member_id` bigint unsigned DEFAULT NULL COMMENT '评价人会员ID',
  `agent_id` bigint unsigned NOT NULL COMMENT '被评价的坐席ID，从会话上带过来，便于按客服统计',
  `agent_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '坐席名称快照',
  `score` tinyint unsigned NOT NULL COMMENT '满意度评分：1-5 星',
  `tags` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '评价标签，英文逗号分隔，如 响应快,态度好',
  `comment` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '评价留言，可空',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '评价时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_rating_conversation` (`conversation_id`),
  KEY `idx_ai_rating_agent` (`agent_id`,`created_at`),
  KEY `idx_ai_rating_score` (`score`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 客服会话满意度评价表';

-- ----------------------------
-- 表结构: ai_schedule
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_schedule` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `agent_id` bigint unsigned NOT NULL COMMENT '坐席管理员ID',
  `agent_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '坐席名称快照',
  `weekday` tinyint unsigned NOT NULL COMMENT '周几：1 周一 至 7 周日',
  `start_time` time NOT NULL COMMENT '班次开始时间',
  `end_time` time NOT NULL COMMENT '班次结束时间',
  `enabled` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '是否启用：1启用，0停用。停用后不参与服务时间判断',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_schedule_agent_weekday` (`agent_id`,`weekday`),
  KEY `idx_ai_schedule_weekday` (`weekday`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 客服坐席排班表';

-- ----------------------------
-- 表结构: ai_ticket
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_ticket` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `ticket_no` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工单编号，形如 AI20260921-0001，插入后由主键回填',
  `conversation_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源会话标识，用户直接提交留言时为空',
  `member_id` bigint unsigned DEFAULT NULL COMMENT '提交会员ID，未登录为空',
  `contact` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '联系方式，手机号或邮箱',
  `question` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户原始问题',
  `ai_summary` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'AI 对问题的归类摘要，便于运营快速判断归属',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '处理状态：PENDING待处理/PROCESSING处理中/CLOSED已关闭',
  `handler_id` bigint unsigned DEFAULT NULL COMMENT '处理人管理员ID',
  `handler_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理人名称快照，避免账号改名后历史工单无法辨认',
  `handle_note` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理备注，仅运营可见',
  `reply_content` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '给买家的回复内容，门户可见',
  `replied_at` datetime(3) DEFAULT NULL COMMENT '回复给买家的时间',
  `handled_at` datetime(3) DEFAULT NULL COMMENT '处理完成时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '提交时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_ticket_no` (`ticket_no`),
  KEY `idx_ai_ticket_status` (`status`,`created_at`),
  KEY `idx_ai_ticket_conversation` (`conversation_id`),
  KEY `idx_ai_ticket_member` (`member_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 客服转人工工单表';

-- ----------------------------
-- 表结构: ai_vector_sync
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_vector_sync` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '向量来源类型：PRODUCT商品/FAQ问答/DOCUMENT文档',
  `source_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源记录标识，商品为商品ID，FAQ 为问答ID',
  `collection_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '写入的 Qdrant collection 名',
  `content_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '向量化文本的 SHA-256，内容未变则跳过重新向量化',
  `point_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Qdrant 中的点 ID，删除与覆盖时使用',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '同步状态：PENDING待同步/SYNCED已同步/FAILED失败',
  `retry_count` int unsigned NOT NULL DEFAULT '0' COMMENT '连续失败重试次数',
  `error_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近一次失败原因',
  `synced_at` datetime(3) DEFAULT NULL COMMENT '最近一次同步成功时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_vector_sync_source` (`source_type`,`source_id`,`collection_name`),
  KEY `idx_ai_vector_sync_status` (`status`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 向量同步位点表';

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
  `admin_read_status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '运营已读：0未读，1已读',
  `admin_read_at` datetime(3) DEFAULT NULL COMMENT '运营阅读时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '逻辑删除：0否，1是',
  `version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_content_notification_dedupe` (`member_id`,`dedupe_key`,`is_deleted`),
  KEY `idx_content_notification_member_read` (`member_id`,`read_status`,`created_at`),
  KEY `idx_content_notification_order` (`order_id`,`created_at`),
  KEY `idx_content_notification_admin_read` (`admin_read_status`,`created_at`)
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
  `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '状态：1展示，0隐藏',
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
-- 第二部分：系统基础数据（26 行）
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

-- ----------------------------
-- 系统基础数据: ai_faq（非业务数据，来自 db/init/056_ai_faq_policy.sql）
-- ----------------------------
INSERT INTO `ai_faq` (`id`, `question`, `answer`, `category`, `keywords`, `sort_no`, `enabled`, `sync_status`, `synced_at`, `created_at`, `updated_at`, `is_deleted`, `version`) VALUES (1,'商品支持七天无理由退货吗？','支持。自签收次日起 7 天内，商品不影响二次销售（吊牌、包装、配件齐全，未使用、未洗涤、未安装）可以申请无理由退货。定制商品、贴身用品以及已拆封的耗材类商品不适用无理由退货。申请入口：在「我的订单」里找到对应订单，点「申请售后」选择「退货退款」，填写原因后提交即可。','AFTER_SALE','七天无理由,7天无理由,无理由退货,退货条件,影响二次销售,能不能退',10,1,'PENDING',NULL,'2026-09-22 19:59:21.346','2026-09-22 19:59:21.346',0,0);;
INSERT INTO `ai_faq` (`id`, `question`, `answer`, `category`, `keywords`, `sort_no`, `enabled`, `sync_status`, `synced_at`, `created_at`, `updated_at`, `is_deleted`, `version`) VALUES (2,'退换货的运费由谁承担？','商品存在质量问题、发错货或运输破损的，退换货运费由本商城承担，您可以先垫付寄回，之后凭快递单号联系客服报销。无理由退货（比如不喜欢、拍错、尺码不合适）的往返运费由您承担；订单原本已包邮的，退回时仍需承担寄回的运费。','AFTER_SALE','退货运费,运费谁出,寄回运费,报销运费,免运费退回',20,1,'PENDING',NULL,'2026-09-22 19:59:21.346','2026-09-22 19:59:21.346',0,0);;
INSERT INTO `ai_faq` (`id`, `question`, `answer`, `category`, `keywords`, `sort_no`, `enabled`, `sync_status`, `synced_at`, `created_at`, `updated_at`, `is_deleted`, `version`) VALUES (3,'订单还没发货，可以取消吗？','可以。待付款订单直接在「我的订单」取消即可；已付款但还没发货的订单，请申请售后并选择「仅退款」，说明取消原因。待发货订单不支持退货退款和换货。已经发货的订单需要按退货退款流程处理，等商品寄回后安排退款。','AFTER_SALE','未发货退款,取消订单,仅退款,不想买了,还没发货',30,1,'PENDING',NULL,'2026-09-22 19:59:21.346','2026-09-22 19:59:21.346',0,0);;
INSERT INTO `ai_faq` (`id`, `question`, `answer`, `category`, `keywords`, `sort_no`, `enabled`, `sync_status`, `synced_at`, `created_at`, `updated_at`, `is_deleted`, `version`) VALUES (4,'售后申请提交后多久处理？各个状态是什么意思？','在「我的订单」页面的「售后进度」里可以看到状态：待审核表示已提交、等客服审核；处理中表示审核通过，退款或换货正在处理；已完成表示处理结束；已驳回表示不符合售后条件，会说明原因；已取消表示本次申请已撤销。提交后 24 小时内会有人工审核。','AFTER_SALE','售后进度,售后状态,待审核,处理中,已驳回,已取消,售后多久',40,1,'PENDING',NULL,'2026-09-22 19:59:21.346','2026-09-22 19:59:21.346',0,0);;
INSERT INTO `ai_faq` (`id`, `question`, `answer`, `category`, `keywords`, `sort_no`, `enabled`, `sync_status`, `synced_at`, `created_at`, `updated_at`, `is_deleted`, `version`) VALUES (5,'换货可以同时退款吗？','换货不支持退款金额，只做同款商品的换尺码或换颜色。质量问题引起的换货，来回运费由本商城承担；非质量问题（比如不喜欢、拍错颜色）的换货，来回运费由您承担。','AFTER_SALE','换货,只换不退,换尺码,换颜色,换货运费',50,1,'PENDING',NULL,'2026-09-22 19:59:21.346','2026-09-22 19:59:21.346',0,0);;
INSERT INTO `ai_faq` (`id`, `question`, `answer`, `category`, `keywords`, `sort_no`, `enabled`, `sync_status`, `synced_at`, `created_at`, `updated_at`, `is_deleted`, `version`) VALUES (6,'退款退到哪里？多久到账？','退款原路退回，退到您下单时使用的微信支付账户。审核通过后一般 1-3 个工作日到账，具体到账时间以微信支付的处理结果为准，可以在微信支付的账单记录里查看。','AFTER_SALE','退款到账,退款时间,原路退回,钱退到哪里,退款多久到',60,1,'PENDING',NULL,'2026-09-22 19:59:21.346','2026-09-22 19:59:21.346',0,0);;
INSERT INTO `ai_faq` (`id`, `question`, `answer`, `category`, `keywords`, `sort_no`, `enabled`, `sync_status`, `synced_at`, `created_at`, `updated_at`, `is_deleted`, `version`) VALUES (7,'运费怎么算？多少钱可以包邮？','单笔订单满 99 元免运费。未满 99 元时按物流计费规则收取，首重 1kg 起步，超出部分每 1kg 加收 5 元。西藏、新疆以及中国香港、中国澳门、中国台湾等偏远地区按物流实际报价收取。下单结算页显示的运费为最终金额，也可以先领取运费券抵扣。','SHIPPING','运费,包邮,满99包邮,邮费怎么算,运费多少钱,免运费',10,1,'PENDING',NULL,'2026-09-22 19:59:21.346','2026-09-22 19:59:21.346',0,0);;
INSERT INTO `ai_faq` (`id`, `question`, `answer`, `category`, `keywords`, `sort_no`, `enabled`, `sync_status`, `synced_at`, `created_at`, `updated_at`, `is_deleted`, `version`) VALUES (8,'哪些地区可以配送？偏远地区发货吗？','全国大部分地区正常配送。西藏、新疆以及中国香港、中国澳门、中国台湾属于偏远配送范围，运费与时效按物流实际报价和执行。海外地址暂不支持下单。','SHIPPING','配送范围,偏远地区,港澳台,海外发货,发不发货,能不能送到',20,1,'PENDING',NULL,'2026-09-22 19:59:21.346','2026-09-22 19:59:21.346',0,0);;
INSERT INTO `ai_faq` (`id`, `question`, `answer`, `category`, `keywords`, `sort_no`, `enabled`, `sync_status`, `synced_at`, `created_at`, `updated_at`, `is_deleted`, `version`) VALUES (9,'什么时候发货？多久能收到？','现货商品在付款成功后 48 小时内安排发出，预售、大促和定制商品以商品详情页标注的发货时间为准。发货后可以在「我的订单」的物流信息里查看轨迹，实际送达时间以承运商的运输时效为准。','SHIPPING','发货时间,多久发货,什么时候发货,几天到,物流时效,什么时候到货',30,1,'PENDING',NULL,'2026-09-22 19:59:21.346','2026-09-22 19:59:21.346',0,0);;
INSERT INTO `ai_faq` (`id`, `question`, `answer`, `category`, `keywords`, `sort_no`, `enabled`, `sync_status`, `synced_at`, `created_at`, `updated_at`, `is_deleted`, `version`) VALUES (10,'物流信息一直不更新怎么办？','快递揽收后一般 24 小时内会出现第一条物流轨迹。如果发货超过 48 小时仍然没有更新，请把订单号发给在线客服，我们帮您向承运商核实，也可以留下手机号或邮箱由人工跟进。','SHIPPING','物流不更新,快递没动静,物流没更新,查不到物流,快递停滞',40,1,'PENDING',NULL,'2026-09-22 19:59:21.346','2026-09-22 19:59:21.346',0,0);;
INSERT INTO `ai_faq` (`id`, `question`, `answer`, `category`, `keywords`, `sort_no`, `enabled`, `sync_status`, `synced_at`, `created_at`, `updated_at`, `is_deleted`, `version`) VALUES (11,'可以指定快递公司或者加急发货吗？','目前订单由合作的承运商统一安排发出，暂不支持指定快递公司。确实需要加急的可以联系客服说明情况，我们会尽量协调，但不保证提前送达。','SHIPPING','指定快递,加急发货,顺丰,快递公司,催发货',50,1,'PENDING',NULL,'2026-09-22 19:59:21.346','2026-09-22 19:59:21.346',0,0);;
INSERT INTO `ai_faq` (`id`, `question`, `answer`, `category`, `keywords`, `sort_no`, `enabled`, `sync_status`, `synced_at`, `created_at`, `updated_at`, `is_deleted`, `version`) VALUES (12,'支持哪些支付方式？','目前仅支持微信支付：下单后在支付页面用微信扫码完成付款。支付宝、银行卡等其它付款方式暂不支持。','PAYMENT','支付方式,怎么付款,微信支付,支付宝,银行卡,付款方式,能刷什么卡',10,1,'PENDING',NULL,'2026-09-22 19:59:21.346','2026-09-22 19:59:21.346',0,0);;
INSERT INTO `ai_faq` (`id`, `question`, `answer`, `category`, `keywords`, `sort_no`, `enabled`, `sync_status`, `synced_at`, `created_at`, `updated_at`, `is_deleted`, `version`) VALUES (13,'下单后多久要付款？超时会怎样？','下单后请在 30 分钟内完成支付。超时未支付的订单会被系统自动关闭，占用的库存同时释放，需要时重新下单即可，不会产生额外费用。','PAYMENT','支付超时,多久付款,订单自动关闭,30分钟,付款时间限制',20,1,'PENDING',NULL,'2026-09-22 19:59:21.346','2026-09-22 19:59:21.346',0,0);;
INSERT INTO `ai_faq` (`id`, `question`, `answer`, `category`, `keywords`, `sort_no`, `enabled`, `sync_status`, `synced_at`, `created_at`, `updated_at`, `is_deleted`, `version`) VALUES (14,'已经付款了，但订单还是显示待付款怎么办？','支付结果回传会有短暂延迟，一般 1-2 分钟内订单状态就会更新。如果超过 10 分钟仍显示待付款，请先确认微信是否已经实际扣款，再联系客服并提供订单号，我们人工核实。请不要重复下单或重复支付。','PAYMENT','支付成功订单未更新,重复支付,扣款了还是待付款,支付异常,付了钱没反应',30,1,'PENDING',NULL,'2026-09-22 19:59:21.346','2026-09-22 19:59:21.346',0,0);;
INSERT INTO `ai_faq` (`id`, `question`, `answer`, `category`, `keywords`, `sort_no`, `enabled`, `sync_status`, `synced_at`, `created_at`, `updated_at`, `is_deleted`, `version`) VALUES (15,'优惠券有哪些？怎么使用？','当前可领取的优惠券有：新人专享满 199 元减 50 元、户外商品满 299 元减 30 元、全场满 99 元最高减免 15 元运费券，每张券每位会员限领 1 张，入口是页面顶部的「领券中心」。在结算页面的优惠券一栏选择后即可抵扣，注意有效期和使用门槛，逾期或未达门槛无法使用；已领取的券在「个人中心 → 我的券包」查看。','PAYMENT','优惠券,满减券,怎么用券,运费券,领券中心,优惠码,领券',40,1,'PENDING',NULL,'2026-09-22 19:59:21.346','2026-09-22 19:59:21.346',0,0);;
INSERT INTO `ai_faq` (`id`, `question`, `answer`, `category`, `keywords`, `sort_no`, `enabled`, `sync_status`, `synced_at`, `created_at`, `updated_at`, `is_deleted`, `version`) VALUES (16,'可以开发票吗？支持哪些发票类型？','可以。支持电子普通发票和增值税专用发票，开票金额为订单实付金额，一个订单只能开一张发票。开具增值税专用发票需要提供公司名称和纳税人识别号。','INVOICE','开发票,发票类型,专票,普票,增值税发票,能开票吗',10,1,'PENDING',NULL,'2026-09-22 19:59:21.346','2026-09-22 19:59:21.346',0,0);;
INSERT INTO `ai_faq` (`id`, `question`, `answer`, `category`, `keywords`, `sort_no`, `enabled`, `sync_status`, `synced_at`, `created_at`, `updated_at`, `is_deleted`, `version`) VALUES (17,'怎么申请发票？','订单支付成功后，在「我的订单」里找到对应订单并点「申请发票」，填写发票抬头、纳税人识别号（专用发票必填）和接收邮箱，提交后由财务审核开具，开好后会发送到您填写的邮箱。','INVOICE','申请发票,怎么开票,开票入口,发票抬头,发票邮箱,开票流程',20,1,'PENDING',NULL,'2026-09-22 19:59:21.346','2026-09-22 19:59:21.346',0,0);;
INSERT INTO `ai_faq` (`id`, `question`, `answer`, `category`, `keywords`, `sort_no`, `enabled`, `sync_status`, `synced_at`, `created_at`, `updated_at`, `is_deleted`, `version`) VALUES (18,'发票开错了或者需要重开怎么办？','已开具的发票如需更换抬头、金额或类型，请把订单号和已开发票信息提供给客服，由人工核实后处理。未支付的订单以及已全额退款的订单不支持开票。','INVOICE','发票开错,重开发票,红冲,换抬头,退款后开票,发票抬头填错',30,1,'PENDING',NULL,'2026-09-22 19:59:21.346','2026-09-22 19:59:21.346',0,0);;
INSERT INTO `ai_faq` (`id`, `question`, `answer`, `category`, `keywords`, `sort_no`, `enabled`, `sync_status`, `synced_at`, `created_at`, `updated_at`, `is_deleted`, `version`) VALUES (19,'会员等级怎么划分？有什么权益？','注册后即为普通会员，会员等级分为普通会员、黄金 VIP、黑金 SVIP 三档，等级越高权益越多。普通会员：新人礼包、实付满 99 元包邮、购物 1:1 累计积分、7 天无理由退换；黄金 VIP：全场自营 9.5 折、每月 1 张免邮券、1.5 倍积分返还、优先极速退款；黑金 SVIP：全场自营 9.2 折、每月 3 张免邮券、2 倍积分返还、1 对 1 专属管家。会员等级由平台根据消费情况评估调整，当前等级、积分、余额和累计消费在「个人中心 → 资料与特权」查看。','MEMBER','会员等级,会员权益,怎么升级,黄金VIP,黑金SVIP,会员有什么用,特权',10,1,'PENDING',NULL,'2026-09-22 19:59:21.346','2026-09-22 19:59:21.346',0,0);;
INSERT INTO `ai_faq` (`id`, `question`, `answer`, `category`, `keywords`, `sort_no`, `enabled`, `sync_status`, `synced_at`, `created_at`, `updated_at`, `is_deleted`, `version`) VALUES (20,'怎么查看我的优惠券和积分？','登录后在「个人中心 → 资料与特权」可以看到商城积分、账户余额和累计消费；已领取的优惠券在「个人中心 → 我的券包」查看，券上会同时显示有效期和使用门槛。','MEMBER','我的优惠券,积分查询,账户余额,个人中心,我的券包,我的资产',20,1,'PENDING',NULL,'2026-09-22 19:59:21.346','2026-09-22 19:59:21.346',0,0);;


SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 结束
-- =============================================================================
