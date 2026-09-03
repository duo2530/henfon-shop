-- 修复订单审核字段长度及操作日志 JSON 参数格式问题。

USE `henfon-shop`;

ALTER TABLE trade_order
    MODIFY COLUMN audited_by VARCHAR(64) DEFAULT NULL COMMENT '审核管理员用户名';
