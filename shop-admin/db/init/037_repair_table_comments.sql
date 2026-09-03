-- 修复历史数据库中业务表备注缺失的问题，不修改表结构和业务数据。

USE `henfon-shop`;

ALTER TABLE content_notification COMMENT = '会员站内通知表';
ALTER TABLE content_email_delivery COMMENT = '会员业务邮件投递记录表';
ALTER TABLE marketing_flash_sale COMMENT = '秒杀促销活动表';
ALTER TABLE marketing_flash_sale_item COMMENT = '秒杀促销活动商品表';
ALTER TABLE marketing_flash_sale_reservation COMMENT = '秒杀活动预约记录表';
