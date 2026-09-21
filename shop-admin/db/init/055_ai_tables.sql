-- =====================================================================
-- AI 智能客服基础表
--
-- 客服对话、知识库与转人工工单的持久化结构。Qdrant 只存向量与检索用的 payload，
-- 业务事实一律以本组表为准——向量随时可以由商品、FAQ 重新生成，不作为数据源。
--
-- 会话与消息分开：ai_conversation 存会话级信息用于列表与筛选；ai_message 既是
-- 聊天记录，也是 Spring AI ChatMemory 的存储，(conversation_id, sequence) 唯一
-- 保证同一会话内消息顺序，读回上下文时按它排序。
-- =====================================================================

USE `henfon-shop`;

CREATE TABLE IF NOT EXISTS `ai_faq` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `question` varchar(255) NOT NULL COMMENT '标准问法，同时作为向量化文本的来源',
  `answer` text NOT NULL COMMENT '标准答案，命中后直接拼进模型上下文',
  `category` varchar(32) DEFAULT NULL COMMENT '业务分类：AFTER_SALE/SHIPPING/INVOICE/PAYMENT/MEMBER/OTHER',
  `keywords` varchar(255) DEFAULT NULL COMMENT '人工维护的检索关键词，英文逗号分隔，供关键词兜底召回',
  `sort_no` int NOT NULL DEFAULT 0 COMMENT '同分类内展示顺序，值小的在前',
  `enabled` tinyint unsigned NOT NULL DEFAULT 1 COMMENT '是否启用：1启用，0停用。停用后不参与向量化',
  `sync_status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '向量同步状态：PENDING待同步/SYNCED已同步/FAILED失败',
  `synced_at` datetime(3) DEFAULT NULL COMMENT '最近一次向量化成功时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：1已删除，0正常',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_ai_faq_category` (`category`, `enabled`, `sort_no`),
  KEY `idx_ai_faq_sync` (`sync_status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 客服知识库问答表';

CREATE TABLE IF NOT EXISTS `ai_conversation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `conversation_id` varchar(64) NOT NULL COMMENT '会话标识，对外暴露，由服务端生成',
  `channel` varchar(16) NOT NULL COMMENT '入口渠道：PORTAL门户买家/ADMIN管理端助手',
  `member_id` bigint unsigned DEFAULT NULL COMMENT '所属会员ID，未登录会话为空',
  `admin_id` bigint unsigned DEFAULT NULL COMMENT '所属管理员ID，管理端会话使用',
  `subject_type` varchar(32) DEFAULT NULL COMMENT '会话绑定的业务对象类型，如 PRODUCT/ORDER',
  `subject_id` varchar(64) DEFAULT NULL COMMENT '会话绑定的业务对象标识',
  `title` varchar(120) DEFAULT NULL COMMENT '会话标题，取首条用户提问的前若干字',
  `message_count` int unsigned NOT NULL DEFAULT 0 COMMENT '消息条数，含用户与助手消息',
  `last_message_at` datetime(3) DEFAULT NULL COMMENT '最近一条消息时间，会话列表按它倒序',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '会话状态：ACTIVE进行中/TICKETED已转人工/CLOSED已结束',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_conversation_id` (`conversation_id`),
  KEY `idx_ai_conversation_member` (`channel`, `member_id`, `last_message_at`),
  KEY `idx_ai_conversation_admin` (`channel`, `admin_id`, `last_message_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 客服会话表';

CREATE TABLE IF NOT EXISTS `ai_message` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `conversation_id` varchar(64) NOT NULL COMMENT '所属会话标识',
  `sequence` int unsigned NOT NULL COMMENT '会话内消息序号，从 1 递增，读回上下文按它排序',
  `role` varchar(16) NOT NULL COMMENT '消息角色：USER用户/ASSISTANT助手/TOOL工具/SYSTEM系统',
  `content` mediumtext COMMENT '消息正文，纯工具调用消息可为空',
  `tool_name` varchar(64) DEFAULT NULL COMMENT '工具调用名称，工具消息才有',
  `tool_payload` text COMMENT '工具调用参数与返回结果的 JSON 快照，便于排查错误回答',
  `model` varchar(64) DEFAULT NULL COMMENT '生成该条消息的模型名',
  `tokens_in` int unsigned DEFAULT NULL COMMENT '输入 token 数',
  `tokens_out` int unsigned DEFAULT NULL COMMENT '输出 token 数',
  `latency_ms` int unsigned DEFAULT NULL COMMENT '本次响应耗时，单位毫秒，用于效果分析',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_message_seq` (`conversation_id`, `sequence`),
  KEY `idx_ai_message_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 客服消息明细表';

CREATE TABLE IF NOT EXISTS `ai_ticket` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `ticket_no` varchar(32) DEFAULT NULL COMMENT '工单编号，形如 AI20260921-0001，插入后由主键回填',
  `conversation_id` varchar(64) DEFAULT NULL COMMENT '来源会话标识，用户直接提交留言时为空',
  `member_id` bigint unsigned DEFAULT NULL COMMENT '提交会员ID，未登录为空',
  `contact` varchar(120) NOT NULL COMMENT '联系方式，手机号或邮箱',
  `question` varchar(1000) NOT NULL COMMENT '用户原始问题',
  `ai_summary` varchar(500) DEFAULT NULL COMMENT 'AI 对问题的归类摘要，便于运营快速判断归属',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '处理状态：PENDING待处理/PROCESSING处理中/CLOSED已关闭',
  `handler_id` bigint unsigned DEFAULT NULL COMMENT '处理人管理员ID',
  `handler_name` varchar(64) DEFAULT NULL COMMENT '处理人名称快照，避免账号改名后历史工单无法辨认',
  `handle_note` varchar(500) DEFAULT NULL COMMENT '处理备注，仅运营可见',
  `handled_at` datetime(3) DEFAULT NULL COMMENT '处理完成时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '提交时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_ticket_no` (`ticket_no`),
  KEY `idx_ai_ticket_status` (`status`, `created_at`),
  KEY `idx_ai_ticket_conversation` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 客服转人工工单表';

CREATE TABLE IF NOT EXISTS `ai_document` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `title` varchar(200) NOT NULL COMMENT '文档标题',
  `doc_type` varchar(32) NOT NULL DEFAULT 'POLICY' COMMENT '文档类型：POLICY政策/SOP流程/OTHER其他',
  `source` varchar(500) DEFAULT NULL COMMENT '原文来源，MinIO 对象键或外部链接',
  `doc_version` varchar(32) DEFAULT NULL COMMENT '文档版本号，由运营人工维护',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '索引状态：DRAFT草稿/INDEXED已索引/FAILED索引失败',
  `chunk_count` int unsigned NOT NULL DEFAULT 0 COMMENT '切分后的片段数量',
  `error_message` varchar(500) DEFAULT NULL COMMENT '索引失败原因，直接展示给运营',
  `indexed_at` datetime(3) DEFAULT NULL COMMENT '最近一次索引完成时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT 0 COMMENT '逻辑删除：1已删除，0正常',
  PRIMARY KEY (`id`),
  KEY `idx_ai_document_status` (`status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 知识文档表';

CREATE TABLE IF NOT EXISTS `ai_vector_sync` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `source_type` varchar(32) NOT NULL COMMENT '向量来源类型：PRODUCT商品/FAQ问答/DOCUMENT文档',
  `source_id` varchar(64) NOT NULL COMMENT '来源记录标识，商品为商品ID，FAQ 为问答ID',
  `collection_name` varchar(64) NOT NULL COMMENT '写入的 Qdrant collection 名',
  `content_hash` char(64) NOT NULL COMMENT '向量化文本的 SHA-256，内容未变则跳过重新向量化',
  `point_id` varchar(64) DEFAULT NULL COMMENT 'Qdrant 中的点 ID，删除与覆盖时使用',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '同步状态：PENDING待同步/SYNCED已同步/FAILED失败',
  `retry_count` int unsigned NOT NULL DEFAULT 0 COMMENT '连续失败重试次数',
  `error_message` varchar(500) DEFAULT NULL COMMENT '最近一次失败原因',
  `synced_at` datetime(3) DEFAULT NULL COMMENT '最近一次同步成功时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_vector_sync_source` (`source_type`, `source_id`, `collection_name`),
  KEY `idx_ai_vector_sync_status` (`status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 向量同步位点表';
