import React, { useMemo, useState } from 'react';
import { useBodyScrollLock } from '../hooks/useBodyScrollLock';
import { Order, OrderItem } from '../types/ecommerce';
import { PortalAfterSaleCreatePayload, PortalAfterSaleRecord, uploadPortalMedia } from '../api/portalApi';
import {
  Check,
  ChevronLeft,
  ChevronRight,
  Copy,
  MapPin,
  Package,
  Receipt,
  RotateCcw,
  Trash2,
  Upload,
  X,
} from 'lucide-react';
import { OrderTracking, maskDetailAddress, maskPhone } from './OrderTracking';
import {
  ORDER_STATUS_FILTERS,
  OrderStatusFilter,
  formatOrderTime,
  formatPaymentMethod,
  matchesOrderStatusFilter,
} from '../utils/orderDisplay';

interface OrdersPageProps {
  orders: Order[];
  /** 返回商城首页：整页订单中心不占侧边导航，靠这条回主页。 */
  onBackToHome: () => void;
  onCancelOrder?: (order: Order) => Promise<void> | void;
  onConfirmOrder?: (order: Order) => Promise<void> | void;
  onRetryPayment?: (order: Order) => Promise<void> | void;
  afterSales?: PortalAfterSaleRecord[];
  afterSalesLoading?: boolean;
  afterSalesError?: string | null;
  onRetryAfterSales?: () => Promise<void> | void;
  onApplyAfterSale?: (order: Order, payload: PortalAfterSaleCreatePayload) => Promise<void> | void;
  onCancelAfterSale?: (afterSale: PortalAfterSaleRecord) => Promise<void> | void;
  onApplyInvoice?: (order: Order) => Promise<void> | void;
  onRetryLogistics?: (order: Order) => Promise<Order['trackingSteps']>;
  /** 点击订单商品行的「评价」，跳转到该商品的买家评价页签。 */
  onReviewOrderItem?: (item: OrderItem) => Promise<void> | void;
  /** 点击已评价商品行的「查看」，跳到个人中心的我的评价。 */
  onViewReview?: (item: OrderItem) => void;
  /** 已评价过的商品ID集合，这些商品行不再提供评价入口。 */
  reviewedProductIds?: string[];
  /**
   * 当前状态页签。由地址承载（`?status=`），从商品详情或评价页返回时页签还停在原处；
   * 缺省 `all`，便于静态渲染与测试直接省略。
   */
  statusFilter?: OrderStatusFilter;
  /** 右栏选中的订单 ID；窄屏靠它判断停在列表还是详情。 */
  selectedOrderId?: string | null;
  /**
   * 页内状态改由地址承载，页签与选中订单一并回写。
   *
   * 合并成一个回调而不是两个：换页签要同时丢掉选中项，两次回写会各带一份旧状态，
   * 后一次把前一次的结果盖掉。
   */
  onQueryChange?: (next: { status: OrderStatusFilter; order: string | null }) => void;
}

/** 订单状态标签的配色：待付款要显眼、已取消要弱化，其余按流转阶段给色。 */
function statusBadgeClass(status: string): string {
  switch (String(status || '').toLowerCase()) {
    case 'placed':
      return 'bg-orange-50 text-orange-700 border-orange-200/70';
    case 'paid':
    case 'processing':
    case 'preparing':
      return 'bg-sky-50 text-sky-700 border-sky-200/70';
    case 'shipped':
    case 'out_for_delivery':
      return 'bg-emerald-50 text-emerald-700 border-emerald-200/70';
    case 'refunding':
    case 'refunded':
      return 'bg-amber-50 text-amber-800 border-amber-200/70';
    default:
      return 'bg-zinc-100 text-zinc-600 border-zinc-200';
  }
}

const AFTER_SALE_STATUS_LABELS: Record<number, string> = {
  10: '待审核',
  20: '处理中',
  30: '已完成',
  40: '已驳回',
  50: '已取消',
};

const AFTER_SALE_TYPE_LABELS: Record<number, string> = {
  1: '仅退款',
  2: '退货退款',
  3: '换货',
};

/**
 * 门户「我的订单」整页。
 *
 * 与旧弹窗的区别：占满 max-w-7xl 的整页、状态页签分流、左订单列表 + 右订单详情双栏。
 * 详情做成独立栏位而不是卡片内展开，是为了让物流时间轴有足够宽度 —— 弹窗形态下
 * 物流区块要占掉展开卡的 74%，一单就把 564px 的可视区撑破，实际每次只能看一单。
 */
export const OrdersPage: React.FC<OrdersPageProps> = ({
  orders,
  onBackToHome,
  onCancelOrder,
  onConfirmOrder,
  onRetryPayment,
  afterSales = [],
  afterSalesLoading = false,
  afterSalesError,
  onRetryAfterSales,
  onApplyAfterSale,
  onCancelAfterSale,
  onApplyInvoice,
  onRetryLogistics,
  onReviewOrderItem,
  onViewReview,
  reviewedProductIds = [],
  statusFilter = 'all',
  selectedOrderId = null,
  onQueryChange,
}: OrdersPageProps) => {
  /**
   * 窄屏只有一栏可用：列表 ↔ 详情靠它切换，桌面端两栏同时显示，这个值不起作用。
   * 它不单独存一份 state —— 选中订单本身就是「停在详情」的意思，两处状态并存迟早对不上。
   */
  const activePane: 'list' | 'detail' = selectedOrderId ? 'detail' : 'list';
  const [actioningOrderAction, setActioningOrderAction] = useState<{ orderId: string; action: string } | null>(null);
  const [actioningAfterSaleId, setActioningAfterSaleId] = useState<number | null>(null);
  const [logisticsRetryingOrderId, setLogisticsRetryingOrderId] = useState<string | null>(null);
  const [logisticsOverrides, setLogisticsOverrides] = useState<Record<string, Order['trackingSteps']>>({});
  const [copiedOrderNumber, setCopiedOrderNumber] = useState<string | null>(null);

  const [afterSaleOrder, setAfterSaleOrder] = useState<Order | null>(null);
  const [afterSaleType, setAfterSaleType] = useState<1 | 2 | 3>(1);
  const [afterSaleAmount, setAfterSaleAmount] = useState('0');
  const [afterSaleReason, setAfterSaleReason] = useState('');
  const [afterSaleEvidenceUrls, setAfterSaleEvidenceUrls] = useState<string[]>([]);
  const [afterSaleUploading, setAfterSaleUploading] = useState(false);
  const [afterSaleSubmitting, setAfterSaleSubmitting] = useState(false);
  const [afterSaleError, setAfterSaleError] = useState<string | null>(null);

  // 售后申请表单是叠在整页之上的浮层，打开期间才锁 body 滚动；订单页本身要能正常滚动。
  useBodyScrollLock(Boolean(afterSaleOrder));

  const filterCounts = useMemo(() => {
    const counts = {} as Record<OrderStatusFilter, number>;
    ORDER_STATUS_FILTERS.forEach(({ key }) => {
      counts[key] = orders.filter((order) => matchesOrderStatusFilter(order, key)).length;
    });
    return counts;
  }, [orders]);

  const visibleOrders = useMemo(
    () => orders.filter((order) => matchesOrderStatusFilter(order, statusFilter)),
    [orders, statusFilter]
  );

  // 选中的单被筛掉（或还没选过）时回落到当前列表的首单，避免右侧留下空白栏。
  const selectedOrder = useMemo(() => {
    if (visibleOrders.length === 0) return null;
    return visibleOrders.find((order) => order.id === selectedOrderId) || visibleOrders[0];
  }, [visibleOrders, selectedOrderId]);

  const runOrderAction = async (
    order: Order,
    action: ((order: Order) => Promise<void> | void) | undefined,
    actionName: string
  ) => {
    if (!action) return;
    setActioningOrderAction({ orderId: order.id, action: actionName });
    try {
      await action(order);
    } finally {
      setActioningOrderAction(null);
    }
  };

  const runAfterSaleAction = async (
    afterSale: PortalAfterSaleRecord,
    action?: (afterSale: PortalAfterSaleRecord) => Promise<void> | void
  ) => {
    if (!action) return;
    setActioningAfterSaleId(afterSale.id);
    try {
      await action(afterSale);
    } finally {
      setActioningAfterSaleId(null);
    }
  };

  /** 重试加载指定订单物流轨迹，只更新当前页展示，避免刷掉用户已选的订单与页签。 */
  const retryOrderLogistics = async (order: Order) => {
    if (!onRetryLogistics) return;
    setLogisticsRetryingOrderId(order.id);
    try {
      const steps = await onRetryLogistics(order);
      setLogisticsOverrides((current) => ({ ...current, [order.id]: steps }));
    } catch (error) {
      // 父组件负责提示具体失败原因，这里只结束加载状态，避免产生未处理 Promise。
      console.warn('刷新订单物流失败', error);
    } finally {
      setLogisticsRetryingOrderId(null);
    }
  };

  const copyOrderNumber = (orderNumber: string) => {
    void navigator.clipboard?.writeText(orderNumber);
    setCopiedOrderNumber(orderNumber);
    window.setTimeout(() => setCopiedOrderNumber((current) => (current === orderNumber ? null : current)), 2000);
  };

  const openAfterSaleForm = (order: Order) => {
    setAfterSaleOrder(order);
    setAfterSaleType(1);
    setAfterSaleAmount(String(order.totalPaid));
    setAfterSaleReason('');
    setAfterSaleEvidenceUrls([]);
    setAfterSaleError(null);
  };

  const handleAfterSaleEvidenceChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files || []) as File[];
    event.target.value = '';
    if (!files.length) return;
    if (afterSaleEvidenceUrls.length + files.length > 9) {
      setAfterSaleError('售后凭证最多上传9张');
      return;
    }
    const invalid = files.find((file) => !['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(file.type));
    if (invalid) {
      setAfterSaleError('仅支持 JPG、PNG、WEBP、GIF 图片');
      return;
    }
    const oversized = files.find((file) => file.size > 10 * 1024 * 1024);
    if (oversized) {
      setAfterSaleError('单张图片不能超过10MB');
      return;
    }
    setAfterSaleUploading(true);
    setAfterSaleError(null);
    try {
      const uploaded = await Promise.all(files.map((file) => uploadPortalMedia(file)));
      setAfterSaleEvidenceUrls((current) => [...current, ...uploaded.map((item) => item.url)]);
    } catch (error) {
      setAfterSaleError(error instanceof Error ? error.message : '售后凭证上传失败，请稍后重试');
    } finally {
      setAfterSaleUploading(false);
    }
  };

  const submitAfterSaleForm = async () => {
    if (!afterSaleOrder || !onApplyAfterSale) return;
    const amount = Number(afterSaleAmount);
    if (!Number.isFinite(amount) || amount < 0 || amount > afterSaleOrder.totalPaid) {
      setAfterSaleError('退款金额不合法，不能超过订单实付金额');
      return;
    }
    if (!afterSaleReason.trim()) {
      setAfterSaleError('请填写售后原因');
      return;
    }
    setAfterSaleSubmitting(true);
    setAfterSaleError(null);
    try {
      await onApplyAfterSale(afterSaleOrder, {
        afterSaleType,
        refundAmount: afterSaleType === 3 ? 0 : amount,
        reason: afterSaleReason.trim(),
        evidenceUrls: afterSaleEvidenceUrls,
      });
      setAfterSaleOrder(null);
    } catch (error) {
      setAfterSaleError(error instanceof Error ? error.message : '申请售后失败，请稍后重试');
    } finally {
      setAfterSaleSubmitting(false);
    }
  };

  const parseEvidenceUrls = (value?: string | string[]) => {
    if (Array.isArray(value)) return value.filter(Boolean);
    if (!value) return [];
    try {
      const parsed = JSON.parse(value);
      return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string' && item.length > 0) : [value];
    } catch {
      return value.split(',').map((item) => item.trim()).filter(Boolean);
    }
  };

  const renderOrderActions = (order: Order) => (
    <div className="flex flex-wrap justify-end gap-2">
      {(order.paymentState === 'failed' || order.paymentState === 'expired') && onRetryPayment && (
        <button
          type="button"
          disabled={actioningOrderAction?.orderId === order.id}
          onClick={() => void runOrderAction(order, onRetryPayment, 'retry-payment')}
          className="rounded-lg bg-orange-600 px-3.5 py-2 text-xs font-semibold text-white transition hover:bg-orange-700 disabled:opacity-50"
        >
          {actioningOrderAction?.orderId === order.id && actioningOrderAction.action === 'retry-payment' ? '处理中…' : '重新支付'}
        </button>
      )}
      {['paid', 'processing', 'shipped', 'delivered'].includes(order.status) && onApplyAfterSale && (
        <button
          type="button"
          disabled={actioningOrderAction?.orderId === order.id}
          onClick={() => openAfterSaleForm(order)}
          className="rounded-lg border border-amber-200 px-3.5 py-2 text-xs font-semibold text-amber-700 transition hover:bg-amber-50 disabled:opacity-50"
        >
          {actioningOrderAction?.orderId === order.id && actioningOrderAction.action === 'after-sale' ? '提交中…' : '申请售后'}
        </button>
      )}
      {['paid', 'processing', 'shipped', 'delivered'].includes(order.status) && onApplyInvoice && (
        <button
          type="button"
          disabled={actioningOrderAction?.orderId === order.id}
          onClick={() => void runOrderAction(order, onApplyInvoice, 'invoice')}
          className="rounded-lg border border-sky-200 px-3.5 py-2 text-xs font-semibold text-sky-700 transition hover:bg-sky-50 disabled:opacity-50"
        >
          {actioningOrderAction?.orderId === order.id && actioningOrderAction.action === 'invoice' ? (
            '处理中…'
          ) : (
            <>
              <Receipt className="mr-1 inline h-3.5 w-3.5" />
              申请发票
            </>
          )}
        </button>
      )}
      {(order.status === 'placed' || order.status === 'paid' || order.status === 'processing') && (
        <button
          type="button"
          disabled={actioningOrderAction?.orderId === order.id}
          onClick={() => void runOrderAction(order, onCancelOrder, 'cancel')}
          className="rounded-lg border border-rose-200 px-3.5 py-2 text-xs font-semibold text-rose-600 transition hover:bg-rose-50 disabled:opacity-50"
        >
          {actioningOrderAction?.orderId === order.id && actioningOrderAction.action === 'cancel' ? '处理中…' : '取消订单'}
        </button>
      )}
      {order.status === 'shipped' && (
        <button
          type="button"
          disabled={actioningOrderAction?.orderId === order.id}
          onClick={() => void runOrderAction(order, onConfirmOrder, 'confirm')}
          className="rounded-lg bg-emerald-600 px-3.5 py-2 text-xs font-semibold text-white transition hover:bg-emerald-700 disabled:opacity-50"
        >
          {actioningOrderAction?.orderId === order.id && actioningOrderAction.action === 'confirm' ? '处理中…' : '确认收货'}
        </button>
      )}
    </div>
  );

  const renderDetail = (order: Order) => {
    const orderAfterSales = afterSales.filter((item) => String(item.orderId) === order.id);
    // 与后端 hasPurchasedProduct 口径一致：已支付且未取消的订单才允许评价。
    const canReviewOrder = ['paid', 'processing', 'shipped', 'delivered', 'refunding', 'refunded'].includes(order.status);
    const address = order.shippingAddress;

    return (
      <div className="space-y-4">
        <div className="rounded-2xl border border-zinc-200/90 bg-white shadow-xs">
          <div className="flex flex-wrap items-start justify-between gap-3 border-b border-zinc-200/80 p-4 sm:p-5">
            <div className="min-w-0 space-y-1.5">
              <div className="flex flex-wrap items-center gap-2">
                <span className={`rounded-md border px-2 py-0.5 text-[11px] font-semibold ${statusBadgeClass(order.status)}`}>
                  {order.statusLabel}
                </span>
                <span className="font-mono text-sm font-bold text-zinc-900">{order.orderNumber}</span>
                <button
                  type="button"
                  onClick={() => copyOrderNumber(order.orderNumber)}
                  className="rounded p-1 text-zinc-400 transition hover:bg-zinc-100 hover:text-zinc-700"
                  aria-label="复制订单号"
                  title="复制订单号"
                >
                  {copiedOrderNumber === order.orderNumber ? <Check className="h-3.5 w-3.5 text-emerald-600" /> : <Copy className="h-3.5 w-3.5" />}
                </button>
              </div>
              <p className="text-xs text-zinc-500">
                下单时间 {formatOrderTime(order.createdAt)}
                {order.paymentMethod ? ` · ${formatPaymentMethod(order.paymentMethod)}` : ''}
              </p>
            </div>
            <button
              type="button"
              onClick={() => onQueryChange?.({ status: statusFilter, order: null })}
              className="flex items-center gap-1 text-xs font-semibold text-zinc-500 transition hover:text-zinc-900 lg:hidden"
            >
              <ChevronLeft className="h-4 w-4" />
              返回订单列表
            </button>
          </div>

          <div className="border-b border-zinc-200/80 p-4 sm:px-5 sm:py-3">
            {renderOrderActions(order)}
          </div>

          <div className="space-y-3 p-4 sm:p-5">
            {order.items.length > 0 ? (
              order.items.map((item, index) => (
                <div key={index} className="flex items-center justify-between gap-3">
                  <div className="flex min-w-0 items-center gap-3">
                    <div className="relative h-14 w-14 shrink-0 overflow-hidden rounded-xl border border-zinc-200 bg-zinc-100">
                      {item.image && (
                        <img
                          src={item.image}
                          alt={item.title}
                          className="h-full w-full object-cover"
                          onError={(event) => {
                            event.currentTarget.style.display = 'none';
                            event.currentTarget.nextElementSibling?.classList.remove('hidden');
                          }}
                        />
                      )}
                      <div className={`absolute inset-0 items-center justify-center text-zinc-300 ${item.image ? 'hidden' : 'flex'}`}>
                        <Package className="h-5 w-5" />
                      </div>
                    </div>
                    <div className="min-w-0">
                      <p className="truncate text-sm font-semibold text-zinc-900">{item.title}</p>
                      <p className="mt-0.5 text-xs text-zinc-400">
                        {item.variantsSummary} × {item.quantity}
                      </p>
                    </div>
                  </div>
                  <div className="flex shrink-0 items-center gap-3">
                    <span className="text-sm font-bold text-zinc-900">¥{(item.price * item.quantity).toFixed(2)}</span>
                    {canReviewOrder && onReviewOrderItem && (
                      reviewedProductIds.includes(item.productId) ? (
                        <span className="flex shrink-0 items-center gap-1.5">
                          <span className="rounded-md border border-zinc-200 px-2 py-1 text-[11px] font-semibold text-zinc-400">已评价</span>
                          {onViewReview && (
                            <button
                              type="button"
                              onClick={() => onViewReview(item)}
                              className="rounded-md border border-sky-200 bg-sky-50 px-2 py-1 text-[11px] font-semibold text-sky-700 transition hover:bg-sky-100"
                            >
                              查看
                            </button>
                          )}
                        </span>
                      ) : (
                        <button
                          type="button"
                          onClick={() => void onReviewOrderItem(item)}
                          className="rounded-md border border-zinc-900 px-2.5 py-1 text-[11px] font-semibold text-zinc-900 transition hover:bg-zinc-900 hover:text-white"
                        >
                          评价
                        </button>
                      )
                    )}
                  </div>
                </div>
              ))
            ) : (
              <p className="py-2 text-xs text-zinc-400">订单明细加载中或暂无明细</p>
            )}
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div className="rounded-2xl border border-zinc-200/90 bg-white p-4 shadow-xs sm:p-5">
            <h3 className="mb-3 flex items-center gap-1.5 text-xs font-bold text-zinc-800">
              <MapPin className="h-3.5 w-3.5 text-rose-500" />
              收货信息
            </h3>
            {address ? (
              <div className="space-y-1 text-xs text-zinc-600">
                <p className="font-bold text-zinc-900">
                  {address.receiverName}
                  <span className="ml-2 font-mono font-normal text-zinc-500">{maskPhone(address.phone)}</span>
                </p>
                <p className="leading-relaxed">
                  {address.province} {address.city} {address.district} {maskDetailAddress(address.detail)}
                </p>
              </div>
            ) : (
              <p className="text-xs text-zinc-400">暂无收货信息</p>
            )}
          </div>

          <div className="rounded-2xl border border-zinc-200/90 bg-white p-4 shadow-xs sm:p-5">
            <h3 className="mb-3 text-xs font-bold text-zinc-800">金额明细</h3>
            <dl className="space-y-1.5 text-xs text-zinc-600">
              <div className="flex justify-between">
                <dt>商品小计</dt>
                <dd className="font-mono">¥{order.subtotal.toFixed(2)}</dd>
              </div>
              <div className="flex justify-between">
                <dt>优惠</dt>
                <dd className="font-mono">-¥{order.discount.toFixed(2)}</dd>
              </div>
              <div className="flex justify-between">
                <dt>运费</dt>
                <dd className="font-mono">{order.shippingFee === 0 ? '免运费' : `¥${order.shippingFee.toFixed(2)}`}</dd>
              </div>
              <div className="flex items-baseline justify-between border-t border-zinc-200/80 pt-2 text-sm">
                <dt className="font-semibold text-zinc-900">实付款</dt>
                <dd className="font-bold text-orange-600">¥{order.totalPaid.toFixed(2)}</dd>
              </div>
            </dl>
          </div>
        </div>

        {(afterSalesLoading || afterSalesError || orderAfterSales.length > 0) && (
          <div className="space-y-2 rounded-2xl border border-zinc-200/90 bg-white p-4 shadow-xs sm:p-5">
            <h3 className="flex items-center gap-1.5 text-xs font-bold text-zinc-800">
              <RotateCcw className="h-3.5 w-3.5 text-amber-600" />
              售后进度
            </h3>
            {afterSalesLoading && <p className="text-[11px] text-zinc-400">售后记录加载中…</p>}
            {afterSalesError && (
              <div className="flex items-center justify-between gap-3 rounded-lg bg-rose-50 px-2.5 py-2">
                <p className="text-[11px] text-rose-600">{afterSalesError}</p>
                {onRetryAfterSales && (
                  <button
                    type="button"
                    onClick={() => void onRetryAfterSales()}
                    disabled={afterSalesLoading}
                    className="shrink-0 rounded-md border border-rose-200 bg-white px-2 py-1 text-[10px] font-semibold text-rose-700 hover:bg-rose-100 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {afterSalesLoading ? '加载中…' : '重新加载'}
                  </button>
                )}
              </div>
            )}
            {orderAfterSales.map((afterSale) => (
              <div key={afterSale.id} className="space-y-1.5 rounded-xl border border-amber-200/80 bg-amber-50/60 p-3 text-[11px] text-zinc-600">
                <div className="flex items-center justify-between gap-2">
                  <span className="font-semibold text-zinc-900">
                    {AFTER_SALE_TYPE_LABELS[afterSale.afterSaleType] || '售后申请'} · {AFTER_SALE_STATUS_LABELS[afterSale.status] || '处理中'}
                  </span>
                  <span className="font-mono text-zinc-400">{afterSale.afterSaleNo}</span>
                </div>
                <p>原因：{afterSale.reason}</p>
                {afterSale.refundAmount > 0 && <p>申请退款：¥{Number(afterSale.refundAmount).toFixed(2)}</p>}
                {parseEvidenceUrls(afterSale.evidenceUrls).length > 0 && (
                  <div className="flex flex-wrap gap-1.5 pt-1">
                    {parseEvidenceUrls(afterSale.evidenceUrls).map((url) => (
                      <a key={url} href={url} target="_blank" rel="noreferrer" title="查看售后凭证">
                        <img src={url} alt="售后凭证" className="h-12 w-12 rounded-md border border-amber-200 object-cover" />
                      </a>
                    ))}
                  </div>
                )}
                {afterSale.status === 10 && onCancelAfterSale && (
                  <button
                    type="button"
                    disabled={actioningAfterSaleId === afterSale.id}
                    onClick={() => void runAfterSaleAction(afterSale, onCancelAfterSale)}
                    className="font-semibold text-rose-600 hover:text-rose-700 disabled:opacity-50"
                  >
                    {actioningAfterSaleId === afterSale.id ? '取消中…' : '取消售后申请'}
                  </button>
                )}
              </div>
            ))}
          </div>
        )}

        <OrderTracking
          order={logisticsOverrides[order.id] ? { ...order, trackingSteps: logisticsOverrides[order.id] } : order}
          interactiveSimulator={false}
          onRetryLogistics={onRetryLogistics ? () => retryOrderLogistics(order) : undefined}
          logisticsRetrying={logisticsRetryingOrderId === order.id}
        />
      </div>
    );
  };

  return (
    <div className="space-y-5">
      <nav aria-label="面包屑" className="flex items-center gap-1.5 text-xs text-zinc-500">
        <button type="button" onClick={onBackToHome} className="transition hover:text-zinc-900">
          商城首页
        </button>
        <ChevronRight className="h-3.5 w-3.5 text-zinc-300" />
        <span className="font-semibold text-zinc-900">我的订单</span>
      </nav>

      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-zinc-900">我的订单</h1>
          <p className="mt-1 text-xs text-zinc-500">共 {orders.length} 笔订单记录</p>
        </div>
        <button
          type="button"
          onClick={onBackToHome}
          className="flex items-center gap-1 rounded-lg border border-zinc-200 bg-white px-3 py-2 text-xs font-semibold text-zinc-600 transition hover:border-zinc-300 hover:text-zinc-900"
        >
          <ChevronLeft className="h-4 w-4" />
          返回首页继续逛
        </button>
      </div>

      <div role="tablist" aria-label="按订单状态筛选" className="flex flex-wrap gap-1.5 rounded-2xl border border-zinc-200/90 bg-white p-2 shadow-xs">
        {ORDER_STATUS_FILTERS.map(({ key, label }) => {
          const isActive = statusFilter === key;
          return (
            <button
              key={key}
              type="button"
              role="tab"
              aria-selected={isActive}
              onClick={() => {
                // 换页签就丢掉选中项：新列表里那一单未必还在，留着会在窄屏上直接落到详情。
                onQueryChange?.({ status: key, order: null });
              }}
              className={`rounded-xl px-3 py-2 text-xs font-semibold transition ${
                isActive ? 'bg-zinc-900 text-white' : 'text-zinc-600 hover:bg-zinc-100 hover:text-zinc-900'
              }`}
            >
              {label}
              <span className={`ml-1.5 font-mono text-[11px] ${isActive ? 'text-zinc-300' : 'text-zinc-400'}`}>
                {filterCounts[key]}
              </span>
            </button>
          );
        })}
      </div>

      {orders.length === 0 ? (
        <div className="rounded-2xl border border-zinc-200/90 bg-white py-20 text-center text-zinc-400 shadow-xs">
          <Package className="mx-auto h-12 w-12 stroke-1 text-zinc-300" />
          <p className="mt-3 text-sm font-semibold text-zinc-700">暂无订单记录</p>
          <p className="mt-1 text-xs text-zinc-400">完成支付后即可在此查看订单与实时物流信息</p>
        </div>
      ) : (
        <div className="grid gap-6 lg:grid-cols-[360px_minmax(0,1fr)] lg:items-start">
          <aside
            aria-label="订单列表"
            className={`min-w-0 overflow-hidden rounded-2xl border border-zinc-200/90 bg-white shadow-xs lg:sticky lg:top-28 lg:max-h-[calc(100vh-9rem)] lg:overflow-y-auto ${
              activePane === 'detail' ? 'hidden lg:block' : 'block'
            }`}
          >
            {visibleOrders.length === 0 ? (
              <div className="px-4 py-14 text-center">
                <Package className="mx-auto h-9 w-9 stroke-1 text-zinc-300" />
                <p className="mt-2 text-xs font-semibold text-zinc-600">该状态下暂无订单</p>
              </div>
            ) : (
              visibleOrders.map((order) => {
                const isSelected = selectedOrder?.id === order.id;
                const firstItem = order.items[0];
                return (
                  <button
                    key={order.id}
                    type="button"
                    aria-current={isSelected ? 'true' : undefined}
                    onClick={() => onQueryChange?.({ status: statusFilter, order: order.id })}
                    className={`block w-full border-b border-zinc-100 px-4 py-3 text-left transition last:border-b-0 ${
                      isSelected ? 'bg-amber-50/70 shadow-[inset_3px_0_0_0_#BA7517]' : 'hover:bg-zinc-50'
                    }`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="truncate font-mono text-[11px] text-zinc-500">{order.orderNumber}</span>
                      <span className={`shrink-0 rounded-md border px-1.5 py-0.5 text-[11px] font-semibold ${statusBadgeClass(order.status)}`}>
                        {order.statusLabel}
                      </span>
                    </div>
                    <div className="mt-2 flex items-center gap-3">
                      <div className="relative h-11 w-11 shrink-0 overflow-hidden rounded-lg border border-zinc-200 bg-zinc-100">
                        {firstItem?.image ? (
                          <img src={firstItem.image} alt={firstItem.title} className="h-full w-full object-cover" />
                        ) : (
                          <div className="flex h-full items-center justify-center text-zinc-300">
                            <Package className="h-4 w-4" />
                          </div>
                        )}
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-xs font-semibold text-zinc-900">
                          {firstItem?.title || '订单明细加载中'}
                        </p>
                        <p className="mt-0.5 text-[11px] text-zinc-400">
                          {order.items.length > 0 ? `${order.items.length} 件商品 · ` : ''}
                          {formatOrderTime(order.createdAt)}
                        </p>
                      </div>
                      <span className="shrink-0 text-sm font-bold text-zinc-900">¥{order.totalPaid.toFixed(2)}</span>
                    </div>
                  </button>
                );
              })
            )}
          </aside>

          <section
            aria-label="订单详情"
            className={`min-w-0 ${activePane === 'list' ? 'hidden lg:block' : 'block'}`}
          >
            {selectedOrder ? (
              renderDetail(selectedOrder)
            ) : (
              <div className="rounded-2xl border border-zinc-200/90 bg-white py-20 text-center shadow-xs">
                <Package className="mx-auto h-10 w-10 stroke-1 text-zinc-300" />
                <p className="mt-2 text-xs font-semibold text-zinc-600">该状态下暂无订单</p>
              </div>
            )}
          </section>
        </div>
      )}

      {afterSaleOrder && (
        <div
          className="fixed inset-0 z-[60] flex items-center justify-center bg-black/50 p-4"
          onClick={() => !afterSaleSubmitting && setAfterSaleOrder(null)}
        >
          <div className="w-full max-w-lg rounded-2xl bg-white p-5 shadow-2xl" onClick={(event) => event.stopPropagation()}>
            <div className="flex items-center justify-between border-b border-zinc-200 pb-3">
              <div>
                <h3 className="text-base font-bold text-zinc-900">申请售后</h3>
                <p className="text-xs text-zinc-500">订单：{afterSaleOrder.orderNumber}</p>
              </div>
              <button
                type="button"
                className="rounded-lg p-1.5 text-zinc-400 hover:bg-zinc-100"
                onClick={() => setAfterSaleOrder(null)}
                disabled={afterSaleSubmitting}
                aria-label="关闭售后申请"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
            <div className="space-y-3 pt-4">
              <label className="block text-xs font-semibold text-zinc-700">
                售后类型
                <select
                  value={afterSaleType}
                  onChange={(event) => {
                    const next = Number(event.target.value) as 1 | 2 | 3;
                    setAfterSaleType(next);
                    if (next === 3) setAfterSaleAmount('0');
                    else if (Number(afterSaleAmount) === 0) setAfterSaleAmount(String(afterSaleOrder.totalPaid));
                  }}
                  className="mt-1 w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm"
                >
                  <option value={1}>仅退款</option>
                  <option value={2}>退货退款</option>
                  <option value={3}>换货</option>
                </select>
              </label>
              <label className="block text-xs font-semibold text-zinc-700">
                退款金额（元）
                <input
                  type="number"
                  min="0"
                  max={afterSaleOrder.totalPaid}
                  step="0.01"
                  value={afterSaleAmount}
                  disabled={afterSaleType === 3}
                  onChange={(event) => setAfterSaleAmount(event.target.value)}
                  className="mt-1 w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm disabled:bg-zinc-100"
                />
              </label>
              <label className="block text-xs font-semibold text-zinc-700">
                售后原因
                <textarea
                  rows={3}
                  maxLength={500}
                  value={afterSaleReason}
                  onChange={(event) => setAfterSaleReason(event.target.value)}
                  placeholder="请描述商品问题或售后诉求"
                  className="mt-1 w-full resize-none rounded-lg border border-zinc-200 px-3 py-2 text-sm"
                />
              </label>
              <div>
                <div className="mb-1 flex items-center justify-between text-xs font-semibold text-zinc-700">
                  <span>售后凭证（最多9张，可选）</span>
                  <span className="font-normal text-zinc-400">{afterSaleEvidenceUrls.length}/9</span>
                </div>
                <div className="flex flex-wrap gap-2">
                  {afterSaleEvidenceUrls.map((url) => (
                    <div key={url} className="group relative">
                      <img src={url} alt="已上传售后凭证" className="h-16 w-16 rounded-lg border border-zinc-200 object-cover" />
                      <button
                        type="button"
                        aria-label="删除凭证"
                        onClick={() => setAfterSaleEvidenceUrls((current) => current.filter((item) => item !== url))}
                        className="absolute -right-1.5 -top-1.5 rounded-full bg-zinc-900 p-0.5 text-white opacity-0 transition group-hover:opacity-100"
                      >
                        <Trash2 className="h-3 w-3" />
                      </button>
                    </div>
                  ))}
                  {afterSaleEvidenceUrls.length < 9 && (
                    <label
                      className={`flex h-16 w-16 cursor-pointer flex-col items-center justify-center rounded-lg border border-dashed border-zinc-300 text-zinc-400 hover:border-amber-400 hover:text-amber-600 ${
                        afterSaleUploading ? 'pointer-events-none opacity-50' : ''
                      }`}
                    >
                      <Upload className="h-4 w-4" />
                      <span className="mt-1 text-[10px]">{afterSaleUploading ? '上传中' : '上传图片'}</span>
                      <input
                        type="file"
                        accept="image/jpeg,image/png,image/webp,image/gif"
                        multiple
                        className="hidden"
                        disabled={afterSaleUploading || afterSaleSubmitting}
                        onChange={(event) => void handleAfterSaleEvidenceChange(event)}
                      />
                    </label>
                  )}
                </div>
              </div>
              {afterSaleError && <p className="text-xs text-rose-600">{afterSaleError}</p>}
              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setAfterSaleOrder(null)}
                  disabled={afterSaleSubmitting}
                  className="rounded-lg border border-zinc-200 px-4 py-2 text-xs font-semibold text-zinc-600 hover:bg-zinc-50"
                >
                  取消
                </button>
                <button
                  type="button"
                  onClick={() => void submitAfterSaleForm()}
                  disabled={afterSaleSubmitting || afterSaleUploading}
                  className="rounded-lg bg-amber-600 px-4 py-2 text-xs font-semibold text-white hover:bg-amber-700 disabled:opacity-50"
                >
                  {afterSaleSubmitting ? '提交中…' : '提交售后申请'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
