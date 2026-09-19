import { Order } from '../types/ecommerce';

/**
 * 订单中心左栏的状态页签。
 *
 * 口径按用户能理解的动作分组，而不是照搬服务端六态：服务端的 paid/processing 对用户而言
 * 都是「已付款等发货」，shipped/out_for_delivery 都是「在路上」，拆开只会让页签和订单对不上。
 */
export type OrderStatusFilter =
  | 'all'
  | 'unpaid'
  | 'unshipped'
  | 'shipped'
  | 'finished'
  | 'afterSale'
  | 'cancelled';

export const ORDER_STATUS_FILTERS: { key: OrderStatusFilter; label: string }[] = [
  { key: 'all', label: '全部订单' },
  { key: 'unpaid', label: '待付款' },
  { key: 'unshipped', label: '待发货' },
  { key: 'shipped', label: '待收货' },
  { key: 'finished', label: '已完成' },
  { key: 'afterSale', label: '退款/售后' },
  { key: 'cancelled', label: '已取消' },
];

/**
 * 判断订单是否落在指定页签下。
 *
 * `Order['status']` 是宽 union（含服务端未穷举的字符串），所以这里一律先归一化再比字符串，
 * 不做穷举 switch 兜底 —— 遇到未知状态时它只会出现在「全部订单」里，不会被错误塞进某个页签。
 */
export function matchesOrderStatusFilter(order: Order, filter: OrderStatusFilter): boolean {
  if (filter === 'all') return true;
  const status = String(order?.status || '').trim().toLowerCase();
  switch (filter) {
    case 'unpaid':
      return status === 'placed';
    case 'unshipped':
      return status === 'paid' || status === 'processing' || status === 'preparing';
    case 'shipped':
      return status === 'shipped' || status === 'out_for_delivery';
    case 'finished':
      return status === 'delivered';
    case 'afterSale':
      return status === 'refunding' || status === 'refunded';
    case 'cancelled':
      return status === 'cancelled';
    default:
      return true;
  }
}

/**
 * 把订单时间统一成 `YYYY-MM-DD HH:mm`。
 *
 * 两个来源格式不同：服务端返回 `2026-09-19 17:51:13.061`，本地待付款快照用
 * `toLocaleString('zh-CN')` 得到 `2026/9/19 17:51:13`。列表里带秒和小数位太长，
 * 统一裁到分钟；认不出的格式原样返回，不做猜测。
 */
export function formatOrderTime(value?: string): string {
  const trimmed = String(value || '').trim();
  if (!trimmed) return '-';
  const matched = trimmed.match(/^(\d{4})[-/](\d{1,2})[-/](\d{1,2})[ T](\d{1,2}):(\d{2})/);
  if (!matched) return trimmed;
  const [, year, month, day, hour, minute] = matched;
  return `${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')} ${hour.padStart(2, '0')}:${minute}`;
}

/** 支付渠道码到中文标签。支付单回写的是渠道码，结算页提交的是中文标签，两边都会进同一个字段。 */
const PAYMENT_METHOD_LABELS: Record<string, string> = {
  WECHAT: '微信支付',
  WECHAT_NATIVE: '微信支付',
  WECHAT_JSAPI: '微信支付',
  WECHAT_H5: '微信支付',
  ALIPAY: '支付宝',
  ALIPAY_NATIVE: '支付宝',
  BALANCE: '余额支付',
  COD: '货到付款',
};

/**
 * 支付方式展示文案。
 *
 * `trade_order.payment_method` 里混存两种口径：走支付单的订单回写渠道码（`WECHAT_NATIVE`），
 * 结算页直接提交的订单存中文（`微信支付`）。渠道码对用户没有意义，认不出的值若不含中文
 * 就收起为「在线支付」；本来就是中文的原样保留，别把新渠道显示成读不懂的英文串。
 */
export function formatPaymentMethod(value?: string): string {
  const raw = String(value || '').trim();
  if (!raw) return '在线支付';
  const known = PAYMENT_METHOD_LABELS[raw.toUpperCase()];
  if (known) return known;
  return /[\u4e00-\u9fa5]/.test(raw) ? raw : '在线支付';
}
