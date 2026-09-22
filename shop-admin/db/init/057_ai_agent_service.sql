-- =====================================================================
-- 实时转人工：会话接待模式与人工消息标识
--
-- 原有的「转人工」是提交工单后就结束的单向通道，买家留完言再也看不到任何后续。
-- 这批改动给会话补上「谁在接待」这一维度，让 AI、等待队列、人工客服三种状态可以在
-- 同一条会话上流转，聊天记录仍然只有一份。
--
-- 两处设计要点：
--
-- 1. service_mode 与 status 分工，不重叠。
--    status 管会话生命周期（ACTIVE 进行中 / TICKETED 已提交工单 / CLOSED 已结束），
--    service_mode 管当下由谁接待（AI 智能客服 / WAITING 等待人工 / HUMAN 人工接待中）。
--    人工会话结束后 service_mode 退回 AI，买家可以继续问 AI，不必新开会话。
--
-- 2. ai_message.sender 是人工消息与模型上下文之间的隔离依据，不只是展示字段。
--    ai_message 同时是 Spring AI ChatMemory 的存储，人工客服的回复若以 ASSISTANT 角色
--    被读回，模型会把客服说过的话当成自己说过的话，下一轮顺着编。读取侧据此排除
--    sender='AGENT' 的消息。历史数据按 role 回填，使新旧数据口径一致。
-- =====================================================================

USE `henfon-shop`;

SET @service_mode_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_conversation' AND COLUMN_NAME = 'service_mode'
);
SET @add_service_mode_sql = IF(@service_mode_exists = 0,
    'ALTER TABLE ai_conversation ADD COLUMN service_mode VARCHAR(16) NOT NULL DEFAULT ''AI'' COMMENT ''接待模式：AI智能客服/WAITING等待人工/HUMAN人工接待中'' AFTER status',
    'SELECT 1');
PREPARE add_service_mode_stmt FROM @add_service_mode_sql;
EXECUTE add_service_mode_stmt;
DEALLOCATE PREPARE add_service_mode_stmt;

SET @agent_id_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_conversation' AND COLUMN_NAME = 'agent_id'
);
SET @add_agent_id_sql = IF(@agent_id_exists = 0,
    'ALTER TABLE ai_conversation ADD COLUMN agent_id BIGINT UNSIGNED DEFAULT NULL COMMENT ''接管会话的管理员ID，未接管为空'' AFTER service_mode',
    'SELECT 1');
PREPARE add_agent_id_stmt FROM @add_agent_id_sql;
EXECUTE add_agent_id_stmt;
DEALLOCATE PREPARE add_agent_id_stmt;

SET @agent_requested_at_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_conversation' AND COLUMN_NAME = 'agent_requested_at'
);
SET @add_agent_requested_at_sql = IF(@agent_requested_at_exists = 0,
    'ALTER TABLE ai_conversation ADD COLUMN agent_requested_at DATETIME(3) DEFAULT NULL COMMENT ''买家请求转人工的时间，等待时长按它计算'' AFTER agent_id',
    'SELECT 1');
PREPARE add_agent_requested_at_stmt FROM @add_agent_requested_at_sql;
EXECUTE add_agent_requested_at_stmt;
DEALLOCATE PREPARE add_agent_requested_at_stmt;

SET @agent_joined_at_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_conversation' AND COLUMN_NAME = 'agent_joined_at'
);
SET @add_agent_joined_at_sql = IF(@agent_joined_at_exists = 0,
    'ALTER TABLE ai_conversation ADD COLUMN agent_joined_at DATETIME(3) DEFAULT NULL COMMENT ''客服接入时间'' AFTER agent_requested_at',
    'SELECT 1');
PREPARE add_agent_joined_at_stmt FROM @add_agent_joined_at_sql;
EXECUTE add_agent_joined_at_stmt;
DEALLOCATE PREPARE add_agent_joined_at_stmt;

SET @agent_ended_at_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_conversation' AND COLUMN_NAME = 'agent_ended_at'
);
SET @add_agent_ended_at_sql = IF(@agent_ended_at_exists = 0,
    'ALTER TABLE ai_conversation ADD COLUMN agent_ended_at DATETIME(3) DEFAULT NULL COMMENT ''人工会话结束时间'' AFTER agent_joined_at',
    'SELECT 1');
PREPARE add_agent_ended_at_stmt FROM @add_agent_ended_at_sql;
EXECUTE add_agent_ended_at_stmt;
DEALLOCATE PREPARE add_agent_ended_at_stmt;

SET @waiting_index_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_conversation'
      AND INDEX_NAME = 'idx_ai_conversation_waiting'
);
SET @add_waiting_index_sql = IF(@waiting_index_exists = 0,
    'ALTER TABLE ai_conversation ADD INDEX idx_ai_conversation_waiting (service_mode, agent_requested_at)',
    'SELECT 1');
PREPARE add_waiting_index_stmt FROM @add_waiting_index_sql;
EXECUTE add_waiting_index_stmt;
DEALLOCATE PREPARE add_waiting_index_stmt;

SET @agent_index_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_conversation'
      AND INDEX_NAME = 'idx_ai_conversation_agent'
);
SET @add_agent_index_sql = IF(@agent_index_exists = 0,
    'ALTER TABLE ai_conversation ADD INDEX idx_ai_conversation_agent (agent_id, service_mode, last_message_at)',
    'SELECT 1');
PREPARE add_agent_index_stmt FROM @add_agent_index_sql;
EXECUTE add_agent_index_stmt;
DEALLOCATE PREPARE add_agent_index_stmt;

SET @sender_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_message' AND COLUMN_NAME = 'sender'
);
SET @add_sender_sql = IF(@sender_exists = 0,
    'ALTER TABLE ai_message ADD COLUMN sender VARCHAR(16) DEFAULT NULL COMMENT ''发送方：MEMBER买家/AI智能客服/AGENT人工客服；工具与系统消息为空'' AFTER role',
    'SELECT 1');
PREPARE add_sender_stmt FROM @add_sender_sql;
EXECUTE add_sender_stmt;
DEALLOCATE PREPARE add_sender_stmt;

-- 回填历史消息的发送方。人工客服是本次才引入的，历史数据里 ASSISTANT 一律是模型输出。
-- 不依赖 ORDER BY 或自增序号：按 role 判定即可，重复执行结果相同。
UPDATE ai_message SET sender = 'MEMBER' WHERE sender IS NULL AND role = 'USER';
UPDATE ai_message SET sender = 'AI' WHERE sender IS NULL AND role = 'ASSISTANT';
