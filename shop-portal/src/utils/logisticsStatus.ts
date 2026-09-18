/**
 * 物流状态的中文名。
 *
 * `trade_order_logistics.logistics_status` 存的是系统枚举（发货时写 `SHIPPED`，快递100 同步时
 * 写 `PICKED_UP` / `IN_TRANSIT` / `DELIVERING` / `SIGNED` / `EXCEPTION`），不是给人看的文案。
 * 轨迹节点的展示文案优先取 `event_description`（已是最新物流原文），只有它为空时才会落到这里，
 * 所以这个函数是兜底 —— 没有它就会把英文枚举直接显示给用户。
 */
const LOGISTICS_STATUS_LABELS: Record<string, string> = {
  SHIPPED: '已发货',
  PICKED_UP: '已揽收',
  IN_TRANSIT: '运输中',
  DELIVERING: '派送中',
  SIGNED: '已签收',
  EXCEPTION: '运输异常',
};

export function logisticsStatusLabel(status?: string | null): string {
  if (!status) return '';
  const key = status.trim().toUpperCase();
  return LOGISTICS_STATUS_LABELS[key] ?? status;
}
