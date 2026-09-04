-- 财务对账差异人工处理字段
USE `henfon-shop`;

ALTER TABLE payment_order
    ADD COLUMN IF NOT EXISTS reconciliation_status VARCHAR(32) DEFAULT NULL COMMENT '人工对账状态覆盖值' AFTER remark,
    ADD COLUMN IF NOT EXISTS reconciliation_remark VARCHAR(500) DEFAULT NULL COMMENT '人工对账处理备注' AFTER reconciliation_status;

ALTER TABLE payment_refund_order
    ADD COLUMN IF NOT EXISTS reconciliation_status VARCHAR(32) DEFAULT NULL COMMENT '人工对账状态覆盖值' AFTER remark,
    ADD COLUMN IF NOT EXISTS reconciliation_remark VARCHAR(500) DEFAULT NULL COMMENT '人工对账处理备注' AFTER reconciliation_status;
