import React, { useEffect, useState } from 'react';
import { Order } from '../types/ecommerce';
import { PortalAfterSaleRecord } from '../api/portalApi';
import { Package, X, ChevronDown, ChevronUp, RotateCcw, Receipt } from 'lucide-react';
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
  onApplyAfterSale?: (order: Order) => Promise<void> | void;
  onCancelAfterSale?: (afterSale: PortalAfterSaleRecord) => Promise<void> | void;
  onApplyInvoice?: (order: Order) => Promise<void> | void;
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
}) => {
  const [expandedOrderId, setExpandedOrderId] = useState<string | null>(
    orders[0]?.id || null
  );
  const [actioningOrderId, setActioningOrderId] = useState<string | null>(null);
  const [actioningAfterSaleId, setActioningAfterSaleId] = useState<number | null>(null);

  useEffect(() => {
    setExpandedOrderId((current) => {
      if (current && orders.some((order) => order.id === current)) return current;
      return orders[0]?.id || null;
    });
  }, [orders]);

  if (!isOpen) return null;

  const runOrderAction = async (order: Order, action?: (order: Order) => Promise<void> | void) => {
    if (!action) return;
    setActioningOrderId(order.id);
    try {
      await action(order);
    } finally {
      setActioningOrderId(null);
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

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-black/60 backdrop-blur-xs flex items-center justify-center p-3 sm:p-6 animate-in fade-in duration-200">
      <div 
        className="bg-white rounded-3xl border border-zinc-200 shadow-2xl max-w-3xl w-full overflow-hidden relative flex flex-col max-h-[90vh]"
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
        <div className="overflow-y-auto p-5 sm:p-6 space-y-4 flex-1">
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
                              <img
                                src={item.image}
                                alt={item.title}
                                className="w-10 h-10 rounded-lg object-cover bg-zinc-100 border border-zinc-200 shrink-0"
                              />
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
                            disabled={actioningOrderId === ord.id}
                            onClick={() => void runOrderAction(ord, onRetryPayment)}
                            className="px-3 py-1.5 rounded-lg bg-orange-600 text-white text-xs font-semibold hover:bg-orange-700 disabled:opacity-50"
                          >
                            {actioningOrderId === ord.id ? '处理中…' : '重新支付'}
                          </button>
                        )}
                        {canApplyAfterSale && onApplyAfterSale && (
                          <button
                            type="button"
                            disabled={actioningOrderId === ord.id}
                            onClick={() => void runOrderAction(ord, onApplyAfterSale)}
                            className="px-3 py-1.5 rounded-lg border border-amber-200 text-amber-700 text-xs font-semibold hover:bg-amber-50 disabled:opacity-50"
                          >
                            {actioningOrderId === ord.id ? '提交中…' : '申请售后'}
                          </button>
                        )}
                        {canApplyAfterSale && onApplyInvoice && (
                          <button
                            type="button"
                            disabled={actioningOrderId === ord.id}
                            onClick={() => void runOrderAction(ord, onApplyInvoice)}
                            className="px-3 py-1.5 rounded-lg border border-sky-200 text-sky-700 text-xs font-semibold hover:bg-sky-50 disabled:opacity-50"
                          >
                            {actioningOrderId === ord.id ? '处理中…' : <><Receipt className="w-3.5 h-3.5 inline mr-1" />申请发票</>}
                          </button>
                        )}
                        {(ord.status === 'placed' || ord.status === 'paid' || ord.status === 'processing') && (
                          <button
                            type="button"
                            disabled={actioningOrderId === ord.id}
                            onClick={() => void runOrderAction(ord, onCancelOrder)}
                            className="px-3 py-1.5 rounded-lg border border-rose-200 text-rose-600 text-xs font-semibold hover:bg-rose-50 disabled:opacity-50"
                          >
                            {actioningOrderId === ord.id ? '处理中…' : '取消订单'}
                          </button>
                        )}
                        {ord.status === 'shipped' && (
                          <button
                            type="button"
                            disabled={actioningOrderId === ord.id}
                            onClick={() => void runOrderAction(ord, onConfirmOrder)}
                            className="px-3 py-1.5 rounded-lg bg-emerald-600 text-white text-xs font-semibold hover:bg-emerald-700 disabled:opacity-50"
                          >
                            {actioningOrderId === ord.id ? '处理中…' : '确认收货'}
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
                        <OrderTracking order={ord} interactiveSimulator={false} />
                      </div>
                    </div>
                  )}
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
};
