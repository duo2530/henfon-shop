import React, { useEffect, useState } from 'react';
import { Order } from '../types/ecommerce';
import { PortalAfterSaleCreatePayload, PortalAfterSaleRecord, uploadPortalMedia } from '../api/portalApi';
import { Package, X, ChevronDown, ChevronUp, RotateCcw, Receipt, Upload, Trash2 } from 'lucide-react';
import { OrderTracking } from './OrderTracking';

interface OrdersModalProps {
  isOpen: boolean;
  orders: Order[];
  onClose: () => void;
  onCancelOrder?: (order: Order) => Promise<void> | void;
  onConfirmOrder?: (order: Order) => Promise<void> | void;
  onRetryPayment?: (order: Order) => Promise<void> | void;
  afterSales?: PortalAfterSaleRecord[];
  afterSalesLoading?: boolean;
  afterSalesError?: string | null;
  onApplyAfterSale?: (order: Order, payload: PortalAfterSaleCreatePayload) => Promise<void> | void;
  onCancelAfterSale?: (afterSale: PortalAfterSaleRecord) => Promise<void> | void;
  onApplyInvoice?: (order: Order) => Promise<void> | void;
  onRetryLogistics?: (order: Order) => Promise<Order['trackingSteps']>;
}

export const OrdersModal: React.FC<OrdersModalProps> = ({
  isOpen,
  orders,
  onClose,
  onCancelOrder,
  onConfirmOrder,
  onRetryPayment,
  afterSales = [],
  afterSalesLoading = false,
  afterSalesError,
  onApplyAfterSale,
  onCancelAfterSale,
  onApplyInvoice,
  onRetryLogistics,
}) => {
  const [expandedOrderId, setExpandedOrderId] = useState<string | null>(
    orders[0]?.id || null
  );
  const [actioningOrderAction, setActioningOrderAction] = useState<{ orderId: string; action: string } | null>(null);
  const [actioningAfterSaleId, setActioningAfterSaleId] = useState<number | null>(null);
  const [afterSaleOrder, setAfterSaleOrder] = useState<Order | null>(null);
  const [afterSaleType, setAfterSaleType] = useState<1 | 2 | 3>(1);
  const [afterSaleAmount, setAfterSaleAmount] = useState('0');
  const [afterSaleReason, setAfterSaleReason] = useState('');
  const [afterSaleEvidenceUrls, setAfterSaleEvidenceUrls] = useState<string[]>([]);
  const [afterSaleUploading, setAfterSaleUploading] = useState(false);
  const [afterSaleSubmitting, setAfterSaleSubmitting] = useState(false);
  const [afterSaleError, setAfterSaleError] = useState<string | null>(null);
  const [logisticsRetryingOrderId, setLogisticsRetryingOrderId] = useState<string | null>(null);
  const [logisticsOverrides, setLogisticsOverrides] = useState<Record<string, Order['trackingSteps']>>({});

  useEffect(() => {
    setExpandedOrderId((current) => {
      if (current && orders.some((order) => order.id === current)) return current;
      return orders[0]?.id || null;
    });
  }, [orders]);

  if (!isOpen) return null;

  const runOrderAction = async (order: Order, action: ((order: Order) => Promise<void> | void) | undefined, actionName: string) => {
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
    action?: (afterSale: PortalAfterSaleRecord) => Promise<void> | void,
  ) => {
    if (!action) return;
    setActioningAfterSaleId(afterSale.id);
    try {
      await action(afterSale);
    } finally {
      setActioningAfterSaleId(null);
    }
  };

  /** 重试加载指定订单物流轨迹，并仅更新当前弹窗展示，避免刷新页面丢失操作上下文。 */
  const retryOrderLogistics = async (order: Order) => {
    if (!onRetryLogistics) return;
    setLogisticsRetryingOrderId(order.id);
    try {
      const steps = await onRetryLogistics(order);
      setLogisticsOverrides((current) => ({ ...current, [order.id]: steps }));
    } catch (error) {
      // 父组件负责提示具体失败原因，弹窗只结束加载状态，避免产生未处理 Promise。
      console.warn('刷新订单物流失败', error);
    } finally {
      setLogisticsRetryingOrderId(null);
    }
  };

  const afterSaleStatusLabels: Record<number, string> = {
    10: '待审核',
    20: '处理中',
    30: '已完成',
    40: '已驳回',
    50: '已取消',
  };

  const afterSaleTypeLabels: Record<number, string> = {
    1: '仅退款',
    2: '退货退款',
    3: '换货',
  };

  /**
   * 打开售后申请表单并初始化订单金额。
   *
   * @param order 当前订单
   * @author Henfon
   * @date 2026-09-01
   */
  const openAfterSaleForm = (order: Order) => {
    setAfterSaleOrder(order);
    setAfterSaleType(1);
    setAfterSaleAmount(String(order.totalPaid));
    setAfterSaleReason('');
    setAfterSaleEvidenceUrls([]);
    setAfterSaleError(null);
  };

  /**
   * 上传售后凭证图片并加入预览列表。
   *
   * @param event 文件选择事件
   * @author Henfon
   * @date 2026-09-01
   */
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

  /**
   * 提交售后申请表单。
   *
   * @author Henfon
   * @date 2026-09-01
   */
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

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto overscroll-contain bg-black/60 backdrop-blur-xs flex items-start sm:items-center justify-center p-3 sm:p-6 animate-in fade-in duration-200">
      <div 
        className="bg-white rounded-3xl border border-zinc-200 shadow-2xl max-w-3xl w-full overflow-hidden relative flex flex-col min-h-0 max-h-[calc(100vh-1.5rem)] sm:max-h-[90vh]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="p-5 sm:p-6 border-b border-zinc-200 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-xl bg-zinc-900 text-white flex items-center justify-center">
              <Package className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-zinc-900">我的历史订单</h2>
              <p className="text-xs text-zinc-500">共 {orders.length} 笔订单记录</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg hover:bg-zinc-100 text-zinc-500 hover:text-zinc-900 transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* List of Orders */}
        <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain p-5 sm:p-6 space-y-4">
          {orders.length === 0 ? (
            <div className="py-16 text-center text-zinc-400 space-y-3">
              <Package className="w-12 h-12 mx-auto stroke-1 text-zinc-300" />
              <p className="text-sm font-semibold text-zinc-700">暂无订单记录</p>
              <p className="text-xs text-zinc-400">完成支付后即可在此查看订单与实时物流信息</p>
            </div>
          ) : (
            orders.map((ord) => {
              const isExpanded = expandedOrderId === ord.id;
              const orderAfterSales = afterSales.filter((item) => String(item.orderId) === ord.id);
              const canApplyAfterSale = ['paid', 'processing', 'shipped', 'delivered'].includes(ord.status);
              return (
                <div
                  key={ord.id}
                  className="rounded-2xl border border-zinc-200 overflow-hidden bg-white shadow-xs"
                >
                  {/* Order header row */}
                  <div
                    onClick={() => setExpandedOrderId(isExpanded ? null : ord.id)}
                    className="p-4 bg-zinc-50/80 hover:bg-zinc-50 flex items-center justify-between cursor-pointer transition select-none"
                  >
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-bold text-zinc-900">
                          单号：{ord.orderNumber}
                        </span>
                        <span className="text-[10px] px-2 py-0.5 rounded-md bg-emerald-50 text-emerald-700 border border-emerald-200/60 font-semibold">
                          {ord.statusLabel}
                        </span>
                      </div>
                      <div className="text-[11px] text-zinc-400">
                        下单时间：{ord.createdAt} {ord.trackingNumber ? `| ${ord.trackingNumber}` : '| 暂无运单号'}
                      </div>
                    </div>

                    <div className="flex items-center gap-4">
                      <div className="text-right">
                        <span className="text-xs text-zinc-400 block">实付金额</span>
                        <span className="text-sm font-bold text-zinc-900">
                          ¥{ord.totalPaid.toFixed(2)}
                        </span>
                      </div>
                      {isExpanded ? (
                        <ChevronUp className="w-4 h-4 text-zinc-400" />
                      ) : (
                        <ChevronDown className="w-4 h-4 text-zinc-400" />
                      )}
                    </div>
                  </div>

                  {/* Expanded Items and Tracking */}
                  {isExpanded && (
                    <div className="p-4 space-y-4 border-t border-zinc-200/80 animate-in fade-in">
                      {/* Products */}
                      <div className="space-y-2">
                        {ord.items.length > 0 ? ord.items.map((item, i) => (
                          <div key={i} className="flex items-center justify-between gap-3 text-xs">
                            <div className="flex items-center gap-2.5 min-w-0">
                              <div className="relative h-10 w-10 shrink-0 overflow-hidden rounded-lg border border-zinc-200 bg-zinc-100">
                                {item.image && <img
                                  src={item.image}
                                  alt={item.title}
                                  className="h-full w-full object-cover"
                                  onError={(event) => {
                                    event.currentTarget.style.display = 'none';
                                    event.currentTarget.nextElementSibling?.classList.remove('hidden');
                                  }}
                                />}
                                <div className={`absolute inset-0 items-center justify-center text-zinc-300 ${item.image ? 'hidden' : 'flex'}`}>
                                  <Package className="h-4 w-4" />
                                </div>
                              </div>
                              <div className="min-w-0">
                                <span className="font-semibold text-zinc-900 block truncate">
                                  {item.title}
                                </span>
                                <span className="text-[11px] text-zinc-400">
                                  {item.variantsSummary} × {item.quantity}
                                </span>
                              </div>
                            </div>
                            <span className="font-bold text-zinc-900 shrink-0">
                              ¥{(item.price * item.quantity).toFixed(2)}
                            </span>
                          </div>
                        )) : (
                          <p className="text-xs text-zinc-400 py-2">订单明细加载中或暂无明细</p>
                        )}
                      </div>

                      <div className="flex justify-end gap-2 border-t border-zinc-200/80 pt-3">
                        {(ord.paymentState === 'failed' || ord.paymentState === 'expired') && onRetryPayment && (
                          <button
                            type="button"
                            disabled={actioningOrderAction?.orderId === ord.id}
                            onClick={() => void runOrderAction(ord, onRetryPayment, 'retry-payment')}
                            className="px-3 py-1.5 rounded-lg bg-orange-600 text-white text-xs font-semibold hover:bg-orange-700 disabled:opacity-50"
                          >
                            {actioningOrderAction?.orderId === ord.id && actioningOrderAction.action === 'retry-payment' ? '处理中…' : '重新支付'}
                          </button>
                        )}
                        {canApplyAfterSale && onApplyAfterSale && (
                          <button
                            type="button"
                            disabled={actioningOrderAction?.orderId === ord.id}
                            onClick={() => openAfterSaleForm(ord)}
                            className="px-3 py-1.5 rounded-lg border border-amber-200 text-amber-700 text-xs font-semibold hover:bg-amber-50 disabled:opacity-50"
                          >
                            {actioningOrderAction?.orderId === ord.id && actioningOrderAction.action === 'after-sale' ? '提交中…' : '申请售后'}
                          </button>
                        )}
                        {canApplyAfterSale && onApplyInvoice && (
                          <button
                            type="button"
                            disabled={actioningOrderAction?.orderId === ord.id}
                            onClick={() => void runOrderAction(ord, onApplyInvoice, 'invoice')}
                            className="px-3 py-1.5 rounded-lg border border-sky-200 text-sky-700 text-xs font-semibold hover:bg-sky-50 disabled:opacity-50"
                          >
                            {actioningOrderAction?.orderId === ord.id && actioningOrderAction.action === 'invoice' ? '处理中…' : <><Receipt className="w-3.5 h-3.5 inline mr-1" />申请发票</>}
                          </button>
                        )}
                        {(ord.status === 'placed' || ord.status === 'paid' || ord.status === 'processing') && (
                          <button
                            type="button"
                            disabled={actioningOrderAction?.orderId === ord.id}
                            onClick={() => void runOrderAction(ord, onCancelOrder, 'cancel')}
                            className="px-3 py-1.5 rounded-lg border border-rose-200 text-rose-600 text-xs font-semibold hover:bg-rose-50 disabled:opacity-50"
                          >
                            {actioningOrderAction?.orderId === ord.id && actioningOrderAction.action === 'cancel' ? '处理中…' : '取消订单'}
                          </button>
                        )}
                        {ord.status === 'shipped' && (
                          <button
                            type="button"
                            disabled={actioningOrderAction?.orderId === ord.id}
                            onClick={() => void runOrderAction(ord, onConfirmOrder, 'confirm')}
                            className="px-3 py-1.5 rounded-lg bg-emerald-600 text-white text-xs font-semibold hover:bg-emerald-700 disabled:opacity-50"
                          >
                            {actioningOrderAction?.orderId === ord.id && actioningOrderAction.action === 'confirm' ? '处理中…' : '确认收货'}
                          </button>
                        )}
                      </div>

                      {(afterSalesLoading || afterSalesError || orderAfterSales.length > 0) && (
                        <div className="pt-3 border-t border-zinc-200/80 space-y-2">
                          <div className="flex items-center gap-1.5 text-xs font-bold text-zinc-800">
                            <RotateCcw className="w-3.5 h-3.5 text-amber-600" />
                            售后进度
                          </div>
                          {afterSalesLoading && (
                            <p className="text-[11px] text-zinc-400">售后记录加载中…</p>
                          )}
                          {afterSalesError && (
                            <p className="text-[11px] text-rose-600">{afterSalesError}</p>
                          )}
                          {orderAfterSales.map((afterSale) => (
                            <div key={afterSale.id} className="rounded-xl bg-amber-50/60 border border-amber-200/80 p-2.5 text-[11px] text-zinc-600 space-y-1.5">
                              <div className="flex items-center justify-between gap-2">
                                <span className="font-semibold text-zinc-900">
                                  {afterSaleTypeLabels[afterSale.afterSaleType] || '售后申请'} · {afterSaleStatusLabels[afterSale.status] || '处理中'}
                                </span>
                                <span className="font-mono text-zinc-400">{afterSale.afterSaleNo}</span>
                              </div>
                              <p>原因：{afterSale.reason}</p>
                              {afterSale.refundAmount > 0 && <p>申请退款：¥{Number(afterSale.refundAmount).toFixed(2)}</p>}
                              {parseEvidenceUrls(afterSale.evidenceUrls).length > 0 && (
                                <div className="flex flex-wrap gap-1.5 pt-1">
                                  {parseEvidenceUrls(afterSale.evidenceUrls).map((url) => (
                                    <a key={url} href={url} target="_blank" rel="noreferrer" title="查看售后凭证">
                                      <img src={url} alt="售后凭证" className="w-12 h-12 rounded-md object-cover border border-amber-200" />
                                    </a>
                                  ))}
                                </div>
                              )}
                              {afterSale.status === 10 && onCancelAfterSale && (
                                <button
                                  type="button"
                                  disabled={actioningAfterSaleId === afterSale.id}
                                  onClick={() => void runAfterSaleAction(afterSale, onCancelAfterSale)}
                                  className="text-rose-600 font-semibold hover:text-rose-700 disabled:opacity-50"
                                >
                                  {actioningAfterSaleId === afterSale.id ? '取消中…' : '取消售后申请'}
                                </button>
                              )}
                            </div>
                          ))}
                        </div>
                      )}

                      {/* Visual Shipment Timeline and Tracking */}
                      <div className="pt-2 border-t border-zinc-200/80">
                        <OrderTracking
                          order={logisticsOverrides[ord.id] ? { ...ord, trackingSteps: logisticsOverrides[ord.id] } : ord}
                          interactiveSimulator={false}
                          onRetryLogistics={onRetryLogistics ? () => retryOrderLogistics(ord) : undefined}
                          logisticsRetrying={logisticsRetryingOrderId === ord.id}
                        />
                      </div>
                    </div>
                  )}
                </div>
              );
            })
          )}
        </div>
      </div>
      {afterSaleOrder && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/50 p-4" onClick={() => !afterSaleSubmitting && setAfterSaleOrder(null)}>
          <div className="w-full max-w-lg rounded-2xl bg-white p-5 shadow-2xl" onClick={(event) => event.stopPropagation()}>
            <div className="flex items-center justify-between border-b border-zinc-200 pb-3">
              <div>
                <h3 className="text-base font-bold text-zinc-900">申请售后</h3>
                <p className="text-xs text-zinc-500">订单：{afterSaleOrder.orderNumber}</p>
              </div>
              <button type="button" className="rounded-lg p-1.5 text-zinc-400 hover:bg-zinc-100" onClick={() => setAfterSaleOrder(null)} disabled={afterSaleSubmitting}>
                <X className="h-4 w-4" />
              </button>
            </div>
            <div className="space-y-3 pt-4">
              <label className="block text-xs font-semibold text-zinc-700">售后类型
                <select value={afterSaleType} onChange={(event) => {
                  const next = Number(event.target.value) as 1 | 2 | 3;
                  setAfterSaleType(next);
                  if (next === 3) setAfterSaleAmount('0');
                  else if (Number(afterSaleAmount) === 0) setAfterSaleAmount(String(afterSaleOrder.totalPaid));
                }} className="mt-1 w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm">
                  <option value={1}>仅退款</option>
                  <option value={2}>退货退款</option>
                  <option value={3}>换货</option>
                </select>
              </label>
              <label className="block text-xs font-semibold text-zinc-700">退款金额（元）
                <input type="number" min="0" max={afterSaleOrder.totalPaid} step="0.01" value={afterSaleAmount} disabled={afterSaleType === 3} onChange={(event) => setAfterSaleAmount(event.target.value)} className="mt-1 w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm disabled:bg-zinc-100" />
              </label>
              <label className="block text-xs font-semibold text-zinc-700">售后原因
                <textarea rows={3} maxLength={500} value={afterSaleReason} onChange={(event) => setAfterSaleReason(event.target.value)} placeholder="请描述商品问题或售后诉求" className="mt-1 w-full resize-none rounded-lg border border-zinc-200 px-3 py-2 text-sm" />
              </label>
              <div>
                <div className="mb-1 flex items-center justify-between text-xs font-semibold text-zinc-700">
                  <span>售后凭证（最多9张，可选）</span><span className="font-normal text-zinc-400">{afterSaleEvidenceUrls.length}/9</span>
                </div>
                <div className="flex flex-wrap gap-2">
                  {afterSaleEvidenceUrls.map((url) => (
                    <div key={url} className="group relative">
                      <img src={url} alt="已上传售后凭证" className="h-16 w-16 rounded-lg border border-zinc-200 object-cover" />
                      <button type="button" aria-label="删除凭证" onClick={() => setAfterSaleEvidenceUrls((current) => current.filter((item) => item !== url))} className="absolute -right-1.5 -top-1.5 rounded-full bg-zinc-900 p-0.5 text-white opacity-0 transition group-hover:opacity-100">
                        <Trash2 className="h-3 w-3" />
                      </button>
                    </div>
                  ))}
                  {afterSaleEvidenceUrls.length < 9 && (
                    <label className={`flex h-16 w-16 cursor-pointer flex-col items-center justify-center rounded-lg border border-dashed border-zinc-300 text-zinc-400 hover:border-amber-400 hover:text-amber-600 ${afterSaleUploading ? 'pointer-events-none opacity-50' : ''}`}>
                      <Upload className="h-4 w-4" />
                      <span className="mt-1 text-[10px]">{afterSaleUploading ? '上传中' : '上传图片'}</span>
                      <input type="file" accept="image/jpeg,image/png,image/webp,image/gif" multiple className="hidden" disabled={afterSaleUploading || afterSaleSubmitting} onChange={(event) => void handleAfterSaleEvidenceChange(event)} />
                    </label>
                  )}
                </div>
              </div>
              {afterSaleError && <p className="text-xs text-rose-600">{afterSaleError}</p>}
              <div className="flex justify-end gap-2 pt-2">
                <button type="button" onClick={() => setAfterSaleOrder(null)} disabled={afterSaleSubmitting} className="rounded-lg border border-zinc-200 px-4 py-2 text-xs font-semibold text-zinc-600 hover:bg-zinc-50">取消</button>
                <button type="button" onClick={() => void submitAfterSaleForm()} disabled={afterSaleSubmitting || afterSaleUploading} className="rounded-lg bg-amber-600 px-4 py-2 text-xs font-semibold text-white hover:bg-amber-700 disabled:opacity-50">{afterSaleSubmitting ? '提交中…' : '提交售后申请'}</button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
