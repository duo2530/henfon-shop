-- RocketMQ 主题名只允许字母、数字、下划线、短横线，统一迁移历史 Outbox 主题。
USE `henfon-shop`;

UPDATE trade_event_outbox
SET topic = REPLACE(REPLACE(topic, '.', '_'), '-', '_')
WHERE topic IS NOT NULL
  AND topic REGEXP '[.-]';
