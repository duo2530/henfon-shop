-- =====================================================================
-- 客服控制台：坐席在线状态、会话评价与按周排班
--
-- 三张表各自解决一个当下缺的能力：
--
-- 1. ai_agent_status —— 买家转人工之前要知道「现在到底有没有人」。
--    判定依据是坐席主动上线的状态，而不是排班表：排班是计划，上线才是事实，
--    两者不一致时以事实为准（排班只用来决定「非服务时间」的提示口径）。
--    last_heartbeat_at 是必需的兜底：客服下班忘了点下线、浏览器被直接关掉、
--    机器休眠，这些情况都不会触发下线接口，机器只能靠心跳过期来判定掉线。
--
-- 2. ai_rating —— 会话结束后的满意度评价。独立成表而不是往 ai_conversation
--    加列：评价是买家的独立动作，且后续可能拆成多个维度（态度/专业/响应），
--    加列会让这张主表越来越宽。conversation_id 唯一，一条会话只评一次。
--    没有评价记录不等于差评，评价率由「已结束且有坐席的会话数」作分母另算。
--
-- 3. ai_schedule —— 按周固定班（weekday 粒度），一个客服一天一段班。
--    没有做成「班次模板 + 日期排班」两层：中小客服团队就是按周固定上班，
--    两层模型带来的是排班维护成本而不是灵活性。真要一天分早晚两段时，
--    去掉 uk_ai_schedule_agent_weekday 即可放开成多段。
-- =====================================================================

USE `henfon-shop`;

CREATE TABLE IF NOT EXISTS `ai_agent_status` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `agent_id` bigint unsigned NOT NULL COMMENT '坐席管理员ID',
  `agent_name` varchar(64) DEFAULT NULL COMMENT '坐席名称快照，避免账号改名后历史记录无法辨认',
  `status` varchar(16) NOT NULL DEFAULT 'OFFLINE' COMMENT '坐席状态：ONLINE在线/BREAK小休/OFFLINE离线',
  `last_heartbeat_at` datetime(3) DEFAULT NULL COMMENT '最近心跳时间，超过阈值即视为掉线',
  `online_at` datetime(3) DEFAULT NULL COMMENT '本次上线时间',
  `offline_at` datetime(3) DEFAULT NULL COMMENT '本次下线时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_agent_status_agent` (`agent_id`),
  KEY `idx_ai_agent_status_live` (`status`, `last_heartbeat_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 客服坐席在线状态表';

CREATE TABLE IF NOT EXISTS `ai_rating` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `conversation_id` varchar(64) NOT NULL COMMENT '被评价的会话标识',
  `member_id` bigint unsigned DEFAULT NULL COMMENT '评价人会员ID',
  `agent_id` bigint unsigned NOT NULL COMMENT '被评价的坐席ID，从会话上带过来，便于按客服统计',
  `agent_name` varchar(64) DEFAULT NULL COMMENT '坐席名称快照',
  `score` tinyint unsigned NOT NULL COMMENT '满意度评分：1-5 星',
  `tags` varchar(120) DEFAULT NULL COMMENT '评价标签，英文逗号分隔，如 响应快,态度好',
  `comment` varchar(500) DEFAULT NULL COMMENT '评价留言，可空',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '评价时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_rating_conversation` (`conversation_id`),
  KEY `idx_ai_rating_agent` (`agent_id`, `created_at`),
  KEY `idx_ai_rating_score` (`score`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 客服会话满意度评价表';

CREATE TABLE IF NOT EXISTS `ai_schedule` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `agent_id` bigint unsigned NOT NULL COMMENT '坐席管理员ID',
  `agent_name` varchar(64) DEFAULT NULL COMMENT '坐席名称快照',
  `weekday` tinyint unsigned NOT NULL COMMENT '周几：1 周一 至 7 周日',
  `start_time` time NOT NULL COMMENT '班次开始时间',
  `end_time` time NOT NULL COMMENT '班次结束时间',
  `enabled` tinyint unsigned NOT NULL DEFAULT 1 COMMENT '是否启用：1启用，0停用。停用后不参与服务时间判断',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_schedule_agent_weekday` (`agent_id`, `weekday`),
  KEY `idx_ai_schedule_weekday` (`weekday`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 客服坐席排班表';
