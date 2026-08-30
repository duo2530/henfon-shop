-- 补齐历史表的表级注释
-- 部分开发库由早期脚本创建时没有保留 COMMENT，本迁移只修改元数据，不改变业务字段。

USE `henfon-shop`;

ALTER TABLE catalog_product_feature COMMENT = '商品卖点表';
ALTER TABLE catalog_product_spec COMMENT = '商品参数表';
ALTER TABLE content_banner COMMENT = '门户Banner表';
ALTER TABLE content_review COMMENT = '商品评价表';
ALTER TABLE marketing_coupon COMMENT = '营销优惠券表';
ALTER TABLE marketing_member_coupon COMMENT = '会员优惠券表';
ALTER TABLE member_address COMMENT = '会员收货地址表';
ALTER TABLE member_compare_history COMMENT = '会员商品对比历史表';
ALTER TABLE member_compare_item COMMENT = '会员商品对比明细表';
ALTER TABLE member_favorite COMMENT = '会员收藏商品表';
ALTER TABLE trade_order_logistics COMMENT = '订单物流轨迹表';
