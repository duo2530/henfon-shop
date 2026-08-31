-- 售后退款失败状态补充
-- 退款渠道失败后关闭处理中售后目标，允许会员重新提交申请。

USE `henfon-shop`;

ALTER TABLE trade_after_sale
    MODIFY COLUMN status TINYINT UNSIGNED NOT NULL DEFAULT 10
        COMMENT '状态：10待审核，20处理中，30已完成，40已拒绝，50已取消，60退款失败';
