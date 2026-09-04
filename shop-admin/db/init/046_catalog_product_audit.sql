-- 商品审核与批量上下架闭环字段
USE `henfon-shop`;

ALTER TABLE catalog_product
    ADD COLUMN IF NOT EXISTS audit_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '审核状态：0待审核，1已通过，2已驳回' AFTER status,
    ADD COLUMN IF NOT EXISTS audit_remark VARCHAR(500) DEFAULT NULL COMMENT '审核备注' AFTER audit_status;
