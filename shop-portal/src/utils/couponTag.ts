/**
 * 券型标签的中文名。
 *
 * `marketing_coupon.tag` 存的是券型标识（cash / discount / shipping），管理端的类型筛选与
 * 保存都依赖这几个值，所以不能把库里的值改成中文 —— 翻译只能发生在展示层。
 * 演示数据（`AVAILABLE_COUPONS`）里的 tag 本来就是中文，未命中的值原样返回，兼容自定义文案。
 */
const COUPON_TAG_LABELS: Record<string, string> = {
  cash: '满减',
  discount: '折扣',
  shipping: '包邮',
};

export function couponTagLabel(tag?: string | null): string {
  if (!tag) return '';
  const key = tag.trim().toLowerCase();
  return COUPON_TAG_LABELS[key] ?? tag;
}
