import type { CartItem } from '../types/ecommerce';

/**
 * 从服务端购物车中筛出本次结算已购买、需要清理的条目 ID。
 *
 * 结算条目的 productId 是 `prod-*` 字符串，而服务端购物车返回的是数字商品 ID，
 * 直接拼接比较永远不相等，已购商品会一直留在服务端购物车里、刷新后又被同步回列表。
 *
 * 只按商品维度匹配，不带 SKU：购物车条目的 sku_id 常为 NULL，最终 SKU 是下单时
 * 由服务端兜底选取的，带上 SKU 反而匹配不上。
 *
 * @param remoteItems 服务端购物车条目
 * @param purchasedItems 本次结算的商品条目
 * @author Henfon
 * @date 2026-09-19
 */
export function selectPurchasedCartItemIds(
  remoteItems: { id: number; productId: number }[],
  purchasedItems: Pick<CartItem, 'productId'>[]
): number[] {
  const purchasedProductIds = new Set(
    purchasedItems
      .map((item) => Number(String(item.productId).replace(/^prod-/, '')))
      .filter((productId) => Number.isInteger(productId) && productId > 0)
  );
  if (purchasedProductIds.size === 0) return [];
  return remoteItems
    .filter((item) => purchasedProductIds.has(item.productId))
    .map((item) => item.id);
}
