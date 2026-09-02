-- 为历史无 SKU 商品补建默认规格及库存台账。
-- 旧版商品允许直接使用商品库存，但交易库存模块要求订单明细必须关联 SKU。

USE `henfon-shop`;

-- 仅处理配置了默认 SKU 编码且尚未建立 SKU 的商品，脚本可重复执行。
INSERT INTO catalog_sku
    (product_id, sku_code, sku_name, attributes_json, price, market_price, cost_price,
     weight_gram, stock, safety_stock, status, remark)
SELECT p.id,
       p.default_sku_code,
       '默认规格',
       NULL,
       p.price,
       p.market_price,
       p.cost_price,
       COALESCE(p.weight_gram, 1000),
       COALESCE(p.current_stock, 0),
       COALESCE(p.safety_stock, 0),
       1,
       '历史无 SKU 商品自动补建默认规格'
FROM catalog_product p
WHERE p.is_deleted = 0
  AND p.status = 1
  AND p.default_sku_code IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM catalog_sku s
      WHERE s.product_id = p.id
        AND s.is_deleted = 0
  );

-- 为默认仓库补建对应库存台账，库存数量沿用商品当前库存。
INSERT INTO inventory_stock
    (warehouse_id, product_id, sku_id, available_stock, locked_stock, sold_stock,
     safety_stock, remark)
SELECT w.id,
       p.id,
       s.id,
       COALESCE(p.current_stock, 0),
       0,
       0,
       COALESCE(p.safety_stock, 0),
       '历史无 SKU 商品默认库存台账'
FROM catalog_product p
JOIN catalog_sku s ON s.product_id = p.id
    AND s.sku_code = p.default_sku_code
    AND s.is_deleted = 0
JOIN inventory_warehouse w ON w.is_default = 1
    AND w.status = 1
    AND w.is_deleted = 0
WHERE p.is_deleted = 0
  AND p.status = 1
  AND p.default_sku_code IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM inventory_stock i
      WHERE i.warehouse_id = w.id
        AND i.sku_id = s.id
        AND i.is_deleted = 0
  );

-- 现有购物车中的无 SKU 明细关联到商品默认 SKU，避免历史数据无法结算。
UPDATE trade_cart_item c
JOIN catalog_product p ON p.id = c.product_id
    AND p.is_deleted = 0
JOIN catalog_sku s ON s.product_id = p.id
    AND s.sku_code = p.default_sku_code
    AND s.is_deleted = 0
SET c.sku_id = s.id,
    c.updated_at = CURRENT_TIMESTAMP(3)
WHERE c.sku_id IS NULL
  AND c.is_deleted = 0
  AND p.default_sku_code IS NOT NULL;
