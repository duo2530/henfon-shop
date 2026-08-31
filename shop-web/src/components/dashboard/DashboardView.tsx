import React, { useEffect, useState } from 'react';
import { useAdmin } from '../../context/AdminContext';
import { Order } from '../../types';
import { BackendReportingDashboardMetrics, getReportingDashboardMetrics } from '../../api/adminApi';
import { SalesTrendChart } from './SalesTrendChart';
import { 
  Package, 
  ShoppingBag, 
  Users, 
  Eye,
  Undo2,
  Mail,
  AlertTriangle,
  ChevronRight,
  CheckCircle2,
  X
} from 'lucide-react';

export const DashboardView: React.FC = () => {
  const { orders, todos, resolveTodo, setCurrentTab } = useAdmin();
  const [selectedOrder, setSelectedOrder] = useState<Order | null>(null);
  const [metrics, setMetrics] = useState<BackendReportingDashboardMetrics | null>(null);
  const [metricsLoading, setMetricsLoading] = useState(true);
  const [metricsError, setMetricsError] = useState<string | null>(null);
  const [metricsReloadKey, setMetricsReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setMetricsLoading(true);
    setMetricsError(null);
    getReportingDashboardMetrics()
      .then((result) => {
        if (!cancelled) {
          setMetrics(result);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setMetrics(null);
          setMetricsError(error instanceof Error ? error.message : '经营指标加载失败');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setMetricsLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [metricsReloadKey]);

  const formatCount = (value?: number) => (metricsLoading || metricsError || value === undefined ? '—' : value.toLocaleString('zh-CN'));
  const formatAmount = (value?: number) => (
    metricsLoading || metricsError || value === undefined
      ? '—'
      : `¥${value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
  );

  // Recent 5 orders
  const recentOrders = orders.slice(0, 5);

  return (
    <div className="space-y-6 animate-in fade-in-50 duration-200">
      {/* Page Title & Intro */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
        <div>
          <h2 className="text-xl md:text-2xl font-bold text-[#191C1E] tracking-tight">
            工作台 (Dashboard)
          </h2>
          <p className="text-sm text-[#434655] mt-0.5">
            实时汇总今日电商平台核心运营数据与待处理业务事项。
          </p>
        </div>
      </div>

      {/* 4 Metric Cards Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Metric 1: 今日销售额 */}
        <div className="bg-white rounded-xl p-5 border border-[#E2E8F0] shadow-xs hover:shadow-md transition-shadow">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-[#434655]">
              今日销售额
            </span>
            <div className="w-8 h-8 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center">
              <ShoppingBag className="w-4 h-4" />
            </div>
          </div>
          <div className="flex items-baseline justify-between mt-1">
            <span className="text-2xl md:text-3xl font-bold text-[#191C1E]">
              {formatAmount(metrics?.todaySalesAmount)}
            </span>
            <span className="text-xs font-medium text-gray-500">接口实时数据</span>
          </div>
        </div>

        {/* Metric 2: 今日订单数 */}
        <div className="bg-white rounded-xl p-5 border border-[#E2E8F0] shadow-xs hover:shadow-md transition-shadow">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-[#434655]">
              今日订单数
            </span>
            <div className="w-8 h-8 rounded-lg bg-orange-50 text-orange-600 flex items-center justify-center">
              <Package className="w-4 h-4" />
            </div>
          </div>
          <div className="flex items-baseline justify-between mt-1">
            <span className="text-2xl md:text-3xl font-bold text-[#191C1E]">
              {formatCount(metrics?.todayOrderCount)}
            </span>
            <span className="text-xs font-medium text-gray-500">按创建日期统计</span>
          </div>
        </div>

        {/* Metric 3: 总用户数 */}
        <div className="bg-white rounded-xl p-5 border border-[#E2E8F0] shadow-xs hover:shadow-md transition-shadow">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-[#434655]">
              总用户数
            </span>
            <div className="w-8 h-8 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center">
              <Users className="w-4 h-4" />
            </div>
          </div>
          <div className="flex items-baseline justify-between mt-1">
            <span className="text-2xl md:text-3xl font-bold text-[#191C1E]">
              {formatCount(metrics?.totalMemberCount)}
            </span>
            <span className="text-xs font-medium text-gray-500">累计有效会员</span>
          </div>
        </div>

        {/* Metric 4: 商品总数 */}
        <div className="bg-white rounded-xl p-5 border border-[#E2E8F0] shadow-xs hover:shadow-md transition-shadow">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-[#434655]">
              商品总数
            </span>
            <div className="w-8 h-8 rounded-lg bg-red-50 text-red-600 flex items-center justify-center">
              <Package className="w-4 h-4" />
            </div>
          </div>
          <div className="flex items-baseline justify-between mt-1">
            <span className="text-2xl md:text-3xl font-bold text-[#DC2626]">
              {formatCount(metrics?.totalProductCount)}
            </span>
            <span className="text-xs font-medium text-gray-500">累计有效商品</span>
          </div>
        </div>
      </div>

      {metricsError && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 flex items-center justify-between gap-3" role="alert">
          <span>经营指标加载失败：{metricsError}</span>
          <button
            type="button"
            onClick={() => setMetricsReloadKey((value) => value + 1)}
            className="shrink-0 rounded-md border border-red-300 bg-white px-3 py-1.5 text-xs font-semibold text-red-700 hover:bg-red-100"
          >
            重试
          </button>
        </div>
      )}
      {!metricsLoading && !metricsError && !metrics && (
        <div className="rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-500" role="status">
          暂无经营指标数据
        </div>
      )}

      {/* Middle Section: Chart & To-do List */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Sales Trends Chart based on Recharts (2 cols) */}
        <SalesTrendChart className="lg:col-span-2" />

        {/* To-Do List (1 col) */}
        <div className="bg-white rounded-xl border border-[#E2E8F0] p-5 shadow-xs flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-base font-semibold text-[#191C1E]">
                待办事项 (To-do List)
              </h3>
              <span className="text-xs text-gray-400">
                {todos.length} 项待处理
              </span>
            </div>

            <div className="space-y-3">
              {todos.map((todo) => {
                let borderStyle = 'border-[#FFB4AB] bg-[#FFDAD6]/30';
                let icon = <Undo2 className="w-5 h-5 text-[#93000A] flex-shrink-0" />;
                let titleColor = 'text-[#93000A]';

                if (todo.type === 'stock_alert') {
                  borderStyle = 'border-[#FFE082] bg-[#FFF8E1]';
                  icon = <AlertTriangle className="w-5 h-5 text-[#F57F17] flex-shrink-0" />;
                  titleColor = 'text-[#B45309]';
                } else if (todo.type === 'message') {
                  borderStyle = 'border-gray-200 bg-gray-50';
                  icon = <Mail className="w-5 h-5 text-gray-600 flex-shrink-0" />;
                  titleColor = 'text-gray-800';
                }

                return (
                  <div
                    key={todo.id}
                    className={`p-3.5 rounded-lg border ${borderStyle} flex items-start justify-between gap-3 transition-all hover:scale-[1.01]`}
                  >
                    <div className="flex items-start gap-3 min-w-0">
                      {icon}
                      <div className="min-w-0">
                        <p className={`text-sm font-semibold ${titleColor} truncate`}>
                          {todo.title}
                        </p>
                        <p className="text-xs text-gray-500 mt-0.5 leading-snug">
                          {todo.subtitle}
                        </p>
                      </div>
                    </div>
                    <div className="flex items-center gap-1">
                      {todo.linkTab && (
                        <button
                          onClick={() => setCurrentTab(todo.linkTab!)}
                          className="text-xs font-semibold text-blue-600 hover:text-blue-800 p-1 rounded hover:bg-blue-100/50 transition-colors"
                          title="前往处理"
                        >
                          处理
                        </button>
                      )}
                      <button
                        onClick={() => resolveTodo(todo.id)}
                        className="p-1 text-gray-400 hover:text-emerald-600 rounded transition-colors"
                        title="标记完成"
                      >
                        <CheckCircle2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                );
              })}

              {todos.length === 0 && (
                <div className="text-center py-10 text-gray-400 text-xs">
                  🎉 所有待办事项已全部处理完毕！
                </div>
              )}
            </div>
          </div>

          <div className="mt-4 pt-3 border-t border-gray-100 text-right">
            <button
              onClick={() => setCurrentTab('orders')}
              className="text-xs font-medium text-blue-600 hover:text-blue-800 inline-flex items-center gap-1"
            >
              查看待发货订单中心 <ChevronRight className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </div>

      {/* Bottom Section: Recent Orders Table */}
      <div className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs overflow-hidden">
        <div className="p-4 md:p-5 border-b border-gray-200 flex items-center justify-between bg-white">
          <div>
            <h3 className="text-base font-semibold text-[#191C1E]">
              最近订单 (Recent Orders)
            </h3>
            <p className="text-xs text-gray-500 mt-0.5">查看平台最新生成的交易明细</p>
          </div>
          <button
            onClick={() => setCurrentTab('orders')}
            className="text-sm font-semibold text-blue-600 hover:text-blue-800 hover:bg-blue-50 px-3 py-1.5 rounded-lg transition-colors"
          >
            查看全部 (View All)
          </button>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-[#F8FAFC] text-gray-600 text-xs font-semibold uppercase tracking-wider border-b border-gray-200">
                <th className="py-3 px-4">订单号 (Order ID)</th>
                <th className="py-3 px-4">客户姓名 (Customer)</th>
                <th className="py-3 px-4">商品摘要 (Product)</th>
                <th className="py-3 px-4 text-right">金额 (Amount)</th>
                <th className="py-3 px-4 text-center">状态 (Status)</th>
                <th className="py-3 px-4 text-right">操作 (Action)</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 text-sm text-gray-800 font-normal">
              {recentOrders.map((order, idx) => {
                let statusBadge = (
                  <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800">
                    已完成
                  </span>
                );

                if (order.status === 'pending_shipment') {
                  statusBadge = (
                    <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-100 text-amber-800">
                      待发货
                    </span>
                  );
                } else if (order.status === 'pending_payment') {
                  statusBadge = (
                    <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-yellow-100 text-yellow-800">
                      待付款
                    </span>
                  );
                } else if (order.status === 'shipped') {
                  statusBadge = (
                    <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-blue-100 text-blue-800">
                      已发货
                    </span>
                  );
                } else if (order.status === 'cancelled') {
                  statusBadge = (
                    <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-red-100 text-red-800">
                      已取消
                    </span>
                  );
                }

                const firstItem = order.items[0];

                return (
                  <tr
                    key={order.id}
                    className={`hover:bg-[#F1F5F9] transition-colors group ${
                      idx % 2 === 1 ? 'bg-[#F8FAFC]' : 'bg-white'
                    }`}
                  >
                    <td className="py-3.5 px-4 font-mono font-medium text-gray-900">
                      #{order.orderNumber}
                    </td>
                    <td className="py-3.5 px-4 text-gray-700">
                      {order.customerName}
                    </td>
                    <td className="py-3.5 px-4 text-gray-600 max-w-[240px] truncate">
                      {firstItem ? firstItem.productName : '多件商品'}
                    </td>
                    <td className="py-3.5 px-4 text-right font-medium text-gray-900">
                      ¥{order.amount.toFixed(2)}
                    </td>
                    <td className="py-3.5 px-4 text-center">
                      {statusBadge}
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      <button
                        onClick={() => setSelectedOrder(order)}
                        className="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-md transition-colors"
                        title="查看订单详情"
                      >
                        <Eye className="w-4 h-4" />
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* Quick Order Inspection Modal */}
      {selectedOrder && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white rounded-xl max-w-lg w-full p-6 border border-gray-200 shadow-2xl animate-in zoom-in-95 duration-150">
            <div className="flex items-center justify-between pb-4 border-b border-gray-200">
              <div>
                <h3 className="text-base font-bold text-gray-900">
                  订单详情 #{selectedOrder.orderNumber}
                </h3>
                <p className="text-xs text-gray-500 mt-0.5">
                  下单时间: {selectedOrder.createdAt}
                </p>
              </div>
              <button
                onClick={() => setSelectedOrder(null)}
                className="text-gray-400 hover:text-gray-600 p-1.5 rounded-lg"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="py-4 space-y-4 text-sm text-gray-700 max-h-[60vh] overflow-y-auto">
              <div className="grid grid-cols-2 gap-3 p-3 bg-gray-50 rounded-lg">
                <div>
                  <span className="text-xs text-gray-400 block">收货客户</span>
                  <span className="font-semibold text-gray-800">{selectedOrder.customerName}</span>
                </div>
                <div>
                  <span className="text-xs text-gray-400 block">联系电话</span>
                  <span className="font-semibold text-gray-800">{selectedOrder.customerPhone}</span>
                </div>
                <div className="col-span-2">
                  <span className="text-xs text-gray-400 block">收货地址</span>
                  <span className="text-xs text-gray-700">{selectedOrder.shippingAddress}</span>
                </div>
              </div>

              <div>
                <h4 className="text-xs font-bold uppercase text-gray-500 mb-2">
                  购买商品清单
                </h4>
                <div className="space-y-2">
                  {selectedOrder.items.map((item, idx) => (
                    <div
                      key={idx}
                      className="flex items-center justify-between p-2.5 border border-gray-100 rounded-lg bg-gray-50/50"
                    >
                      <div className="flex items-center gap-3">
                        <img
                          src={item.imageUrl}
                          alt={item.productName}
                          className="w-10 h-10 rounded-md object-cover border border-gray-200"
                        />
                        <div>
                          <p className="font-medium text-xs text-gray-900 line-clamp-1">
                            {item.productName}
                          </p>
                          <p className="text-xs text-gray-500">
                            数量: x{item.quantity}
                          </p>
                        </div>
                      </div>
                      <div className="text-right font-medium text-gray-900">
                        ¥{(item.price * item.quantity).toFixed(2)}
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              <div className="flex justify-between items-center pt-2 border-t border-gray-100">
                <span className="font-semibold text-gray-700">总计金额:</span>
                <span className="text-xl font-bold text-blue-600">
                  ¥{selectedOrder.amount.toFixed(2)}
                </span>
              </div>
            </div>

            <div className="pt-3 border-t border-gray-200 flex justify-end gap-2">
              <button
                onClick={() => {
                  setSelectedOrder(null);
                  setCurrentTab('orders');
                }}
                className="px-4 py-2 bg-blue-600 text-white text-xs font-semibold rounded-lg hover:bg-blue-700 transition-colors"
              >
                前往订单中心处理
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
