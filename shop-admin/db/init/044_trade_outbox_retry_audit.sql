-- Outbox 人工补偿审计字段，记录最近一次人工重试的操作者、时间和结果。
USE `henfon-shop`;

-- MySQL 8.0 部分版本不支持 ALTER TABLE ... ADD COLUMN IF NOT EXISTS，使用元数据判断保证可重复执行。
SET @retry_by_sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE trade_event_outbox ADD COLUMN manual_retry_by VARCHAR(64) DEFAULT NULL COMMENT ''人工重试操作人''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'trade_event_outbox' AND column_name = 'manual_retry_by'
);
PREPARE retry_by_stmt FROM @retry_by_sql;
EXECUTE retry_by_stmt;
DEALLOCATE PREPARE retry_by_stmt;

SET @retry_at_sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE trade_event_outbox ADD COLUMN manual_retry_at DATETIME(3) DEFAULT NULL COMMENT ''人工重试时间''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'trade_event_outbox' AND column_name = 'manual_retry_at'
);
PREPARE retry_at_stmt FROM @retry_at_sql;
EXECUTE retry_at_stmt;
DEALLOCATE PREPARE retry_at_stmt;

SET @retry_result_sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE trade_event_outbox ADD COLUMN manual_retry_result VARCHAR(32) DEFAULT NULL COMMENT ''人工重试结果''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'trade_event_outbox' AND column_name = 'manual_retry_result'
);
PREPARE retry_result_stmt FROM @retry_result_sql;
EXECUTE retry_result_stmt;
DEALLOCATE PREPARE retry_result_stmt;

SET @retry_index_sql = (
    SELECT IF(COUNT(*) = 0,
              'CREATE INDEX idx_trade_event_outbox_manual_retry ON trade_event_outbox (manual_retry_at, manual_retry_by)',
              'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'trade_event_outbox' AND index_name = 'idx_trade_event_outbox_manual_retry'
);
PREPARE retry_index_stmt FROM @retry_index_sql;
EXECUTE retry_index_stmt;
DEALLOCATE PREPARE retry_index_stmt;
