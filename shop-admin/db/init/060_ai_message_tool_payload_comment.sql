-- 商品卡片落库：把 ai_message.tool_payload 的列注释改回它现在的实际用途。
--
-- 这一列最早只存工具调用的参数与返回，后来检索引文也写进来，现在客服推荐的商品卡片也走它。
-- 列名不改（改名要动实体、Mapper 与所有读路径，收益只有注释一句话），但注释得跟上，
-- 否则下一个人看到「工具调用快照」会以为卡片放错了地方，很容易再开一列存同样的东西。
--
-- 存放金额与图片地址：卡片是组装好的快照（含换发过的图片地址与两位小数价格），
-- 读回来直接渲染，不再回查商品表——这样历史消息里的价格与买家当时看到的是一致的。
--
-- 不改字段类型：text 上限 64KB，一条消息最多三张卡片、每张不到 500 字节，余量足够。
USE `henfon-shop`;

SET @payload_comment_sql = (
    SELECT IF(column_comment <> '工具调用、检索引文与商品卡片的 JSON 快照，便于排查错误回答并支持消息回读',
              'ALTER TABLE ai_message MODIFY COLUMN `tool_payload` TEXT COMMENT ''工具调用、检索引文与商品卡片的 JSON 快照，便于排查错误回答并支持消息回读''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_message' AND column_name = 'tool_payload'
);
PREPARE payload_comment_stmt FROM @payload_comment_sql;
EXECUTE payload_comment_stmt;
DEALLOCATE PREPARE payload_comment_stmt;
