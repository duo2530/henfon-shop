-- =====================================================================
-- 工单回复：买家在门户看得到客服的处理结果
--
-- 原来的工单是单向的：买家提交后，运营在后台改状态、写处理备注，而 handle_note 的
-- 定位是"仅运营可见"的内部记录 —— 里面常写着"已联系客户"这类过程说明，直接透给买家
-- 既容易被误读，也不该由系统来决定披露边界。所以另开一列专门承载"给买家看的话"，
-- 两列职责不重叠：handle_note 内部留痕，reply_content 对外答复。
--
-- 同时给会员维度加索引：门户的「我的工单」按 member_id 查自己的记录，
-- 工单表将来会随会话量增长，没有索引就得全表扫。
-- =====================================================================

USE `henfon-shop`;

SET @reply_content_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ticket' AND COLUMN_NAME = 'reply_content'
);
SET @add_reply_content_sql = IF(@reply_content_exists = 0,
    'ALTER TABLE ai_ticket ADD COLUMN reply_content VARCHAR(1000) DEFAULT NULL COMMENT ''给买家的回复内容，门户可见'' AFTER handle_note',
    'SELECT 1');
PREPARE add_reply_content_stmt FROM @add_reply_content_sql;
EXECUTE add_reply_content_stmt;
DEALLOCATE PREPARE add_reply_content_stmt;

SET @replied_at_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ticket' AND COLUMN_NAME = 'replied_at'
);
SET @add_replied_at_sql = IF(@replied_at_exists = 0,
    'ALTER TABLE ai_ticket ADD COLUMN replied_at DATETIME(3) DEFAULT NULL COMMENT ''回复给买家的时间'' AFTER reply_content',
    'SELECT 1');
PREPARE add_replied_at_stmt FROM @add_replied_at_sql;
EXECUTE add_replied_at_stmt;
DEALLOCATE PREPARE add_replied_at_stmt;

SET @member_index_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ticket'
      AND INDEX_NAME = 'idx_ai_ticket_member'
);
SET @add_member_index_sql = IF(@member_index_exists = 0,
    'ALTER TABLE ai_ticket ADD INDEX idx_ai_ticket_member (member_id, created_at)',
    'SELECT 1');
PREPARE add_member_index_stmt FROM @add_member_index_sql;
EXECUTE add_member_index_stmt;
DEALLOCATE PREPARE add_member_index_stmt;
