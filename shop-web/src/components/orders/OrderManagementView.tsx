import React, { useState, useMemo } from 'react';
import { useAdmin } from '../../context/AdminContext';
import { Order, OrderStatus } from '../../types';
import { 
  Plus, 
  Search, 
  Download, 
  ShoppingBag, 
  Truck, 
  CreditCard, 
  RotateCcw, 
  Eye, 
  Send, 
  X, 
  CheckCircle2, 
  AlertCircle,
  Filter,
  Ban,
  Flag,
  Receipt,
  Clock,
  MapPin,
  FileText,
  DollarSign,
  PackageCheck,
  Tag
} from 'lucide-react';
import { PermissionGate } from '../common/PermissionGate';

export const OrderManagementView: React.FC = () => {
  const { 
    orders, 
    addOrder, 
    updateOrderStatus, 
    cancelOrder, 
    batchShipOrders,
    batchCancelOrders,
    updateOrderRemark,
    processOrderRefund,
    showToast, 
    products,
    requirePermission
  } = useAdmin();

  // Tab filter
  const [activeTab, setActiveTab] = useState<'all' | 'pending_payment' | 'pending_shipment' | 'shipped' | 'completed' | 'cancelled' | 'refunded'>('all');
  const [searchTerm, setSearchTerm] = useState('');
  const [dateRange, setDateRange] = useState<'7days' | '30days' | 'month' | 'all'>('7days');
  const [selectedOrderIds, setSelectedOrderIds] = useState<string[]>([]);
  const [flagFilter, setFlagFilter] = useState<'all' | 'red' | 'yellow' | 'blue' | 'green'>('all');

  // Pagination
  const [currentPage, setCurrentPage] = useState(1);
  const pageSize = 5;

  // Modals & Drawers
  const [inspectOrder, setInspectOrder] = useState<Order | null>(null);
  const [shippingOrder, setShippingOrder] = useState<Order | null>(null);
  const [carrier, setCarrier] = useState('顺丰速运');
  const [trackingNumber, setTrackingNumber] = useState('');
  const [isNewOrderModalOpen, setIsNewOrderModalOpen] = useState(false);

  // Batch Ship Modal
  const [isBatchShipModalOpen, setIsBatchShipModalOpen] = useState(false);
  const [batchCarrier, setBatchCarrier] = useState('顺丰速运');

  // Remark & Flag Modal
  const [remarkOrder, setRemarkOrder] = useState<Order | null>(null);
  const [remarkText, setRemarkText] = useState('');
  const [remarkFlag, setRemarkFlag] = useState<Order['flagColor']>('blue');

  // Refund Modal
  const [refundOrder, setRefundOrder] = useState<Order | null>(null);
  const [refundAmount, setRefundAmount] = useState(0);
  const [refundReason, setRefundReason] = useState('协商一致售后退款');

  // New Order Form state
  const [newOrderCustomer, setNewOrderCustomer] = useState('');
  const [newOrderPhone, setNewOrderPhone] = useState('');
  const [newOrderAddress, setNewOrderAddress] = useState('');
  const [selectedProductId, setSelectedProductId] = useState(products[0]?.id || '');
  const [itemQuantity, setItemQuantity] = useState(1);

  // Filtered Orders
  const filteredOrders = useMemo(() => {
    return orders.filter((order) => {
      let matchesTab = true;
      if (activeTab === 'pending_payment') matchesTab = order.status === 'pending_payment';
      else if (activeTab === 'pending_shipment') matchesTab = order.status === 'pending_shipment';
      else if (activeTab === 'shipped') matchesTab = order.status === 'shipped';
      else if (activeTab === 'completed') matchesTab = order.status === 'completed';
      else if (activeTab === 'cancelled') matchesTab = order.status === 'cancelled';
      else if (activeTab === 'refunded') matchesTab = order.status === 'refunded' || Boolean(order.refundStatus);

      const matchesSearch =
        searchTerm === '' ||
        order.orderNumber.toLowerCase().includes(searchTerm.toLowerCase()) ||
        order.customerName.toLowerCase().includes(searchTerm.toLowerCase()) ||
        order.customerPhone.includes(searchTerm);

      const matchesFlag = flagFilter === 'all' || order.flagColor === flagFilter;

      return matchesTab && matchesSearch && matchesFlag;
    });
  }, [orders, activeTab, searchTerm, flagFilter]);

  // Tab dynamic counts
  const pendingPaymentCount = orders.filter((o) => o.status === 'pending_payment').length;
  const pendingShipmentCount = orders.filter((o) => o.status === 'pending_shipment').length;
  const shippedCount = orders.filter((o) => o.status === 'shipped').length;
  const refundedCount = orders.filter((o) => o.status === 'refunded' || o.refundStatus).length;

  // Paginated Orders
  const totalEntries = filteredOrders.length;
  const totalPages = Math.ceil(totalEntries / pageSize) || 1;
  const paginatedOrders = filteredOrders.slice(
    (currentPage - 1) * pageSize,
    currentPage * pageSize
  );

  const handleSelectAll = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.checked) {
      setSelectedOrderIds(paginatedOrders.map((o) => o.id));
    } else {
      setSelectedOrderIds([]);
    }
  };

  const handleToggleSelectOrder = (id: string) => {
    setSelectedOrderIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]
    );
  };

  const handleConfirmShipment = (e: React.FormEvent) => {
    e.preventDefault();
    if (!requirePermission('order:ship', '订单发货')) return;
    if (!shippingOrder) return;
    if (!trackingNumber.trim()) {
      showToast('请填写物流运单号', 'error');
      return;
    }
    updateOrderStatus(shippingOrder.id, 'shipped', trackingNumber, carrier);
    setShippingOrder(null);
    setTrackingNumber('');
  };

  const handleBatchShip = () => {
    if (!requirePermission('order:ship', '批量发货')) return;
    const pendingOrdersToShip = orders.filter((o) => selectedOrderIds.includes(o.id) && o.status === 'pending_shipment');
    if (pendingOrdersToShip.length === 0) {
      showToast('选中的订单中没有待发货订单', 'warning');
      return;
    }
    const shipments = pendingOrdersToShip.map((o, idx) => ({
      orderId: o.id,
      carrier: batchCarrier,
      trackingNumber: `SF${Date.now().toString().slice(-8)}${idx + 10}`
    }));
    batchShipOrders(shipments);
    setIsBatchShipModalOpen(false);
    setSelectedOrderIds([]);
  };

  const handleCreateOrder = (e: React.FormEvent) => {
    e.preventDefault();
    if (!requirePermission('order:add', '代客录单')) return;
    const product = products.find((p) => p.id === selectedProductId) || products[0];
    if (!product) return;

    const newOrderNumber = `ORD-${new Date().toISOString().slice(0, 10).replace(/-/g, '')}-${Math.floor(100 + Math.random() * 900)}`;

    addOrder({
      orderNumber: newOrderNumber,
      customerName: newOrderCustomer || '新客户',
      customerPhone: newOrderPhone || '13800138000',
      amount: product.price * itemQuantity,
      paymentMethod: 'wechat',
      status: 'pending_shipment',
      flagColor: 'blue',
      discountAmount: 0,
      sellerNote: '人工录单创建',
      items: [
        {
          productId: product.id,
          productName: product.name,
          price: product.price,
          quantity: itemQuantity,
          imageUrl: product.imageUrl
        }
      ],
      shippingAddress: newOrderAddress || '北京市朝阳区建国路88号国贸大厦1201',
      logisticsSteps: [
        {
          time: new Date().toISOString().slice(0, 16).replace('T', ' '),
          title: '订单创建成功',
          desc: '买家已提交订单，客服人工代客录单完成',
          status: 'current'
        }
      ]
    });

    setIsNewOrderModalOpen(false);
    setNewOrderCustomer('');
    setNewOrderPhone('');
    setNewOrderAddress('');
  };

  const handleOpenRemark = (order: Order) => {
    setRemarkOrder(order);
    setRemarkText(order.sellerNote || '');
    setRemarkFlag(order.flagColor || 'blue');
  };

  const handleSaveRemark = () => {
    if (!remarkOrder) return;
    if (!requirePermission('order:remark', '订单备注')) return;
    updateOrderRemark(remarkOrder.id, remarkText, remarkFlag);
    setRemarkOrder(null);
  };

  const handleOpenRefund = (order: Order) => {
    setRefundOrder(order);
    setRefundAmount(order.amount);
    setRefundReason(order.refundReason || '协商一致售后退款');
  };

  const handleConfirmRefund = () => {
    if (!refundOrder) return;
    if (!requirePermission('order:refund', '订单退款')) return;
    processOrderRefund(refundOrder.id, refundAmount, refundReason);
    setRefundOrder(null);
  };

  const handleExportOrders = () => {
    if (!requirePermission('order:export', '导出订单报表')) return;
    const csvContent =
      'data:text/csv;charset=utf-8,\uFEFF' +
      '订单号,下单时间,客户姓名,联系电话,支付方式,实付金额,优惠减免,标旗,状态,承运商,运单号,卖家备注,收货地址\n' +
      filteredOrders
        .map(
          (o) =>
            `"${o.orderNumber}","${o.createdAt}","${o.customerName}","${o.customerPhone}","${o.paymentMethod || '微信支付'}",${o.amount},${o.discountAmount || 0},"${o.flagColor || '无'}","${o.status}","${o.shippingCarrier || ''}","${o.trackingNumber || ''}","${o.sellerNote || ''}","${o.shippingAddress}"`
        )
        .join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `全渠道订单明细_${new Date().toISOString().split('T')[0]}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showToast('订单报表明细已成功导出为 CSV', 'success');
  };

  const getFlagBadge = (flag?: Order['flagColor']) => {
    switch (flag) {
      case 'red':
        return <Flag className="w-3.5 h-3.5 text-red-500 fill-red-500" title="红旗: 催发货/加急" />;
      case 'yellow':
        return <Flag className="w-3.5 h-3.5 text-amber-500 fill-amber-500" title="黄旗: 需核实改地址" />;
      case 'green':
        return <Flag className="w-3.5 h-3.5 text-emerald-500 fill-emerald-500" title="绿旗: VIP客户/赠品" />;
      case 'blue':
      default:
        return <Flag className="w-3.5 h-3.5 text-blue-500 fill-blue-500" title="蓝旗: 普通备注" />;
    }
  };

  return (
    <div className="space-y-6 animate-in fade-in-50 duration-200">
      {/* Header Section */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-xl md:text-2xl font-bold text-[#191C1E] tracking-tight">
              订单履约与售后管理 (Order Center)
            </h2>
            <span className="text-xs bg-emerald-50 text-emerald-700 font-semibold px-2 py-0.5 rounded-full border border-emerald-200">
              实时接入
            </span>
          </div>
          <p className="text-xs md:text-sm text-[#434655] mt-0.5">
            全渠道订单实时监控、标旗优先流转、批量打单发货、售后退款与物流链路跟踪。
          </p>
        </div>

        <div className="flex items-center gap-3">
          <PermissionGate permission="order:export">
            <button
              onClick={handleExportOrders}
              className="h-[36px] px-3.5 rounded-lg border border-[#E2E8F0] bg-white text-gray-700 hover:bg-gray-50 flex items-center gap-2 text-xs font-semibold shadow-2xs transition-colors cursor-pointer"
            >
              <Download className="w-4 h-4 text-gray-500" />
              <span>导出订单表</span>
            </button>
          </PermissionGate>

          <PermissionGate permission="order:add">
            <button
              id="btn-create-order"
              onClick={() => setIsNewOrderModalOpen(true)}
              className="h-[36px] px-4 rounded-lg bg-[#2563EB] text-white hover:bg-blue-700 flex items-center justify-center gap-2 text-xs font-semibold shadow-xs transition-colors cursor-pointer"
            >
              <Plus className="w-4 h-4" />
              <span>代客录单 (New Order)</span>
            </button>
          </PermissionGate>
        </div>
      </div>

      {/* Metrics Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-white p-4.5 rounded-xl border border-[#E2E8F0] shadow-xs">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">
              今日订单总数
            </span>
            <div className="p-2 bg-blue-50 text-blue-600 rounded-lg">
              <ShoppingBag className="w-4 h-4" />
            </div>
          </div>
          <div className="flex items-baseline gap-2 mt-1">
            <span className="text-2xl md:text-3xl font-bold text-gray-900">{orders.length + 1200}</span>
            <span className="text-xs font-medium text-emerald-600 bg-emerald-50 px-1.5 py-0.5 rounded">
              +14.8%
            </span>
          </div>
        </div>

        <div className="bg-white p-4.5 rounded-xl border border-[#E2E8F0] shadow-xs">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">
              待发货订单
            </span>
            <div className="p-2 bg-orange-50 text-orange-600 rounded-lg">
              <Truck className="w-4 h-4" />
            </div>
          </div>
          <div className="flex items-baseline gap-2 mt-1">
            <span className="text-2xl md:text-3xl font-bold text-gray-900">{pendingShipmentCount}</span>
            <span className="text-xs font-semibold text-orange-700 bg-orange-50 px-2 py-0.5 rounded flex items-center gap-1">
              <AlertCircle className="w-3 h-3" /> 急需出库
            </span>
          </div>
        </div>

        <div className="bg-white p-4.5 rounded-xl border border-[#E2E8F0] shadow-xs">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">
              在途配送订单
            </span>
            <div className="p-2 bg-purple-50 text-purple-600 rounded-lg">
              <PackageCheck className="w-4 h-4" />
            </div>
          </div>
          <div className="flex items-baseline gap-2 mt-1">
            <span className="text-2xl md:text-3xl font-bold text-gray-900">{shippedCount}</span>
            <span className="text-xs font-medium text-purple-600 bg-purple-50 px-1.5 py-0.5 rounded">
              顺丰/京东承运
            </span>
          </div>
        </div>

        <div className="bg-white p-4.5 rounded-xl border border-[#E2E8F0] shadow-xs">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">
              售后 / 退款争议
            </span>
            <div className="p-2 bg-red-50 text-red-600 rounded-lg">
              <RotateCcw className="w-4 h-4" />
            </div>
          </div>
          <div className="flex items-baseline gap-2 mt-1">
            <span className="text-2xl md:text-3xl font-bold text-gray-900">{refundedCount}</span>
            <span className="text-xs text-gray-400">争议率 0.4%</span>
          </div>
        </div>
      </div>

      {/* Main Workspace: Tabs, Filters, Table */}
      <div className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs overflow-hidden">
        {/* Status Tabs */}
        <div className="border-b border-gray-200 px-4 md:px-6 pt-3 flex gap-6 overflow-x-auto">
          <button
            onClick={() => {
              setActiveTab('all');
              setCurrentPage(1);
            }}
            className={`pb-3 text-sm font-semibold whitespace-nowrap transition-all border-b-2 ${
              activeTab === 'all'
                ? 'text-[#2563EB] border-[#2563EB]'
                : 'text-gray-500 border-transparent hover:text-gray-800'
            }`}
          >
            全部订单 ({orders.length})
          </button>

          <button
            onClick={() => {
              setActiveTab('pending_shipment');
              setCurrentPage(1);
            }}
            className={`pb-3 text-sm font-semibold whitespace-nowrap transition-all border-b-2 flex items-center gap-1.5 ${
              activeTab === 'pending_shipment'
                ? 'text-[#2563EB] border-[#2563EB]'
                : 'text-gray-500 border-transparent hover:text-gray-800'
            }`}
          >
            <span>待发货</span>
            <span className="bg-red-100 text-red-700 px-1.5 py-0.2 rounded-full text-[11px] font-bold">
              {pendingShipmentCount}
            </span>
          </button>

          <button
            onClick={() => {
              setActiveTab('shipped');
              setCurrentPage(1);
            }}
            className={`pb-3 text-sm font-semibold whitespace-nowrap transition-all border-b-2 flex items-center gap-1.5 ${
              activeTab === 'shipped'
                ? 'text-[#2563EB] border-[#2563EB]'
                : 'text-gray-500 border-transparent hover:text-gray-800'
            }`}
          >
            <span>已发货/运输中</span>
            <span className="bg-blue-100 text-blue-700 px-1.5 py-0.2 rounded-full text-[11px]">
              {shippedCount}
            </span>
          </button>

          <button
            onClick={() => {
              setActiveTab('pending_payment');
              setCurrentPage(1);
            }}
            className={`pb-3 text-sm font-semibold whitespace-nowrap transition-all border-b-2 flex items-center gap-1.5 ${
              activeTab === 'pending_payment'
                ? 'text-[#2563EB] border-[#2563EB]'
                : 'text-gray-500 border-transparent hover:text-gray-800'
            }`}
          >
            <span>待付款</span>
            <span className="bg-gray-100 text-gray-700 px-1.5 py-0.2 rounded-full text-[11px]">
              {pendingPaymentCount}
            </span>
          </button>

          <button
            onClick={() => {
              setActiveTab('completed');
              setCurrentPage(1);
            }}
            className={`pb-3 text-sm font-semibold whitespace-nowrap transition-all border-b-2 ${
              activeTab === 'completed'
                ? 'text-[#2563EB] border-[#2563EB]'
                : 'text-gray-500 border-transparent hover:text-gray-800'
            }`}
          >
            已签收完成
          </button>

          <button
            onClick={() => {
              setActiveTab('refunded');
              setCurrentPage(1);
            }}
            className={`pb-3 text-sm font-semibold whitespace-nowrap transition-all border-b-2 flex items-center gap-1.5 ${
              activeTab === 'refunded'
                ? 'text-[#2563EB] border-[#2563EB]'
                : 'text-gray-500 border-transparent hover:text-gray-800'
            }`}
          >
            <span>退款/售后</span>
            {refundedCount > 0 && (
              <span className="bg-amber-100 text-amber-800 px-1.5 py-0.2 rounded-full text-[11px] font-bold">
                {refundedCount}
              </span>
            )}
          </button>

          <button
            onClick={() => {
              setActiveTab('cancelled');
              setCurrentPage(1);
            }}
            className={`pb-3 text-sm font-semibold whitespace-nowrap transition-all border-b-2 ${
              activeTab === 'cancelled'
                ? 'text-[#2563EB] border-[#2563EB]'
                : 'text-gray-500 border-transparent hover:text-gray-800'
            }`}
          >
            已取消关闭
          </button>
        </div>

        {/* Filters & Search Toolbar */}
        <div className="p-4 bg-[#F8FAFC]/60 border-b border-[#E2E8F0] flex flex-wrap gap-3 items-center justify-between">
          <div className="flex flex-wrap gap-3 flex-1 min-w-[280px]">
            <div className="relative flex-1 max-w-sm">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => {
                  setSearchTerm(e.target.value);
                  setCurrentPage(1);
                }}
                placeholder="搜索订单编号、买家姓名、手机号..."
                className="w-full h-[36px] pl-9 pr-3 text-sm rounded-lg border border-[#E2E8F0] bg-white focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 outline-none"
              />
            </div>

            {/* Flag filter */}
            <div className="flex items-center gap-1.5 bg-white border border-[#E2E8F0] px-2.5 py-1 rounded-lg text-xs">
              <span className="text-gray-400">标旗:</span>
              <button
                onClick={() => setFlagFilter('all')}
                className={`px-1.5 py-0.5 rounded text-xs ${flagFilter === 'all' ? 'bg-gray-200 font-bold text-gray-800' : 'text-gray-500'}`}
              >
                全部
              </button>
              <button onClick={() => setFlagFilter('red')} className="p-1 hover:bg-gray-100 rounded">
                <Flag className="w-3.5 h-3.5 text-red-500 fill-red-500" />
              </button>
              <button onClick={() => setFlagFilter('yellow')} className="p-1 hover:bg-gray-100 rounded">
                <Flag className="w-3.5 h-3.5 text-amber-500 fill-amber-500" />
              </button>
              <button onClick={() => setFlagFilter('blue')} className="p-1 hover:bg-gray-100 rounded">
                <Flag className="w-3.5 h-3.5 text-blue-500 fill-blue-500" />
              </button>
              <button onClick={() => setFlagFilter('green')} className="p-1 hover:bg-gray-100 rounded">
                <Flag className="w-3.5 h-3.5 text-emerald-500 fill-emerald-500" />
              </button>
            </div>
          </div>

          {/* Batch operations toolbar */}
          {selectedOrderIds.length > 0 && (
            <div className="flex items-center gap-2 animate-in fade-in-50">
              <span className="text-xs font-semibold text-blue-700 bg-blue-50 px-2 py-1 rounded-md">
                已选中 {selectedOrderIds.length} 笔订单
              </span>
              <PermissionGate permission="order:ship">
                <button
                  onClick={() => setIsBatchShipModalOpen(true)}
                  className="h-[34px] px-3 rounded-lg bg-orange-600 text-white hover:bg-orange-700 text-xs font-semibold flex items-center gap-1.5 shadow-xs transition-colors"
                >
                  <Truck className="w-3.5 h-3.5" />
                  <span>批量发货出库</span>
                </button>
              </PermissionGate>
              <PermissionGate permission="order:cancel">
                <button
                  onClick={() => {
                    if (window.confirm(`确认批量取消选中的 ${selectedOrderIds.length} 笔订单吗？`)) {
                      if (requirePermission('order:cancel', '批量取消订单')) batchCancelOrders(selectedOrderIds);
                      setSelectedOrderIds([]);
                    }
                  }}
                  className="h-[34px] px-3 rounded-lg border border-red-200 text-red-600 hover:bg-red-50 text-xs font-semibold flex items-center gap-1 transition-colors"
                >
                  <Ban className="w-3.5 h-3.5" />
                  <span>批量取消</span>
                </button>
              </PermissionGate>
            </div>
          )}
        </div>

        {/* Data Table */}
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead className="bg-[#F8FAFC] border-b border-[#E2E8F0] text-xs font-semibold text-gray-600 uppercase tracking-wider">
              <tr>
                <th className="py-3 px-4 w-12 text-center">
                  <input
                    type="checkbox"
                    onChange={handleSelectAll}
                    checked={
                      paginatedOrders.length > 0 &&
                      paginatedOrders.every((o) => selectedOrderIds.includes(o.id))
                    }
                    className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                  />
                </th>
                <th className="py-3 px-3 w-8 text-center">标旗</th>
                <th className="py-3 px-4 whitespace-nowrap">订单编号 / 时间</th>
                <th className="py-3 px-4 min-w-[200px]">买家档案 / 配送信息</th>
                <th className="py-3 px-4">购买商品清单</th>
                <th className="py-3 px-4 text-right">实付 / 优惠</th>
                <th className="py-3 px-4 text-center">订单状态</th>
                <th className="py-3 px-4 text-right w-[160px]">操作指令</th>
              </tr>
            </thead>

            <tbody className="divide-y divide-gray-100 text-sm text-gray-800">
              {paginatedOrders.length === 0 ? (
                <tr>
                  <td colSpan={8} className="text-center py-16 text-gray-400">
                    <ShoppingBag className="w-12 h-12 mx-auto mb-2 opacity-40" />
                    <p className="text-sm font-medium">未查询到符合条件的订单记录</p>
                  </td>
                </tr>
              ) : (
                paginatedOrders.map((order, idx) => {
                  const isSelected = selectedOrderIds.includes(order.id);

                  let statusBadges = null;
                  if (order.status === 'pending_shipment') {
                    statusBadges = (
                      <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-orange-100 text-orange-800">
                        <span className="w-1.5 h-1.5 rounded-full bg-orange-500 mr-1.5 animate-pulse"></span>
                        待发货 (待出库)
                      </span>
                    );
                  } else if (order.status === 'shipped') {
                    statusBadges = (
                      <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-blue-100 text-blue-800">
                        <Truck className="w-3 h-3 mr-1" />
                        已发货 / 运输中
                      </span>
                    );
                  } else if (order.status === 'pending_payment') {
                    statusBadges = (
                      <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-amber-100 text-amber-800">
                        <Clock className="w-3 h-3 mr-1" />
                        待支付款项
                      </span>
                    );
                  } else if (order.status === 'refunding') {
                    statusBadges = (
                      <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-purple-100 text-purple-800">
                        <RotateCcw className="w-3 h-3 mr-1 animate-spin" />
                        退款中 (等待渠道确认)
                      </span>
                    );
                  } else if (order.status === 'refunded' || order.refundStatus) {
                    statusBadges = (
                      <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-purple-100 text-purple-800">
                        <RotateCcw className="w-3 h-3 mr-1" />
                        已退款 (售后处理)
                      </span>
                    );
                  } else if (order.status === 'cancelled') {
                    statusBadges = (
                      <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-red-100 text-red-700">
                        已取消
                      </span>
                    );
                  } else {
                    statusBadges = (
                      <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800">
                        <CheckCircle2 className="w-3 h-3 mr-1" />
                        已签收完成
                      </span>
                    );
                  }

                  return (
                    <tr
                      key={order.id}
                      className={`hover:bg-[#F8FAFC] transition-colors group ${
                        isSelected ? 'bg-blue-50/50' : idx % 2 === 1 ? 'bg-[#FCFDFF]' : 'bg-white'
                      }`}
                    >
                      {/* Checkbox */}
                      <td className="py-3 px-4 text-center">
                        <input
                          type="checkbox"
                          checked={isSelected}
                          onChange={() => handleToggleSelectOrder(order.id)}
                          className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                        />
                      </td>

                      {/* Flag color indicator */}
                      <td className="py-3 px-3 text-center">
                        <button
                          onClick={() => handleOpenRemark(order)}
                          className="p-1 hover:bg-gray-100 rounded transition-colors"
                          title="点击修改标旗与卖家备注"
                        >
                          {getFlagBadge(order.flagColor)}
                        </button>
                      </td>

                      {/* Order Number & Date */}
                      <td className="py-3 px-4">
                        <div
                          className="font-mono font-bold text-gray-900 hover:text-blue-600 cursor-pointer text-xs"
                          onClick={() => setInspectOrder(order)}
                        >
                          {order.orderNumber}
                        </div>
                        <div className="text-[11px] text-gray-400 mt-0.5">
                          {order.createdAt}
                        </div>
                        {order.sellerNote && (
                          <div className="text-[11px] text-amber-700 bg-amber-50 px-1.5 py-0.5 rounded mt-1 line-clamp-1 max-w-[150px]">
                            备注: {order.sellerNote}
                          </div>
                        )}
                      </td>

                      {/* Customer Info */}
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-2">
                          <div className="w-7 h-7 rounded-full bg-blue-100 text-blue-700 font-bold text-xs flex items-center justify-center">
                            {order.customerName.charAt(0)}
                          </div>
                          <div>
                            <div className="font-semibold text-gray-900 text-xs">
                              {order.customerName}
                            </div>
                            <PermissionGate permission="order:pii:view" fallback={<div className="text-[11px] text-gray-400 font-mono">手机号已脱敏</div>}>
                              <div className="text-[11px] text-gray-500 font-mono">{order.customerPhone}</div>
                            </PermissionGate>
                          </div>
                        </div>
                        <PermissionGate permission="order:pii:view" fallback={<div className="text-[11px] text-gray-400 mt-1">收货地址已脱敏</div>}>
                          <div className="text-[11px] text-gray-400 line-clamp-1 mt-1 max-w-[180px]">{order.shippingAddress}</div>
                        </PermissionGate>
                      </td>

                      {/* Items Preview */}
                      <td className="py-3 px-4 max-w-xs">
                        <div className="space-y-1">
                          {order.items.slice(0, 2).map((it, i) => (
                            <div key={i} className="flex items-center gap-2 text-xs text-gray-700">
                              <img
                                src={it.imageUrl}
                                alt={it.productName}
                                className="w-6 h-6 rounded object-cover border border-gray-200"
                              />
                              <span className="truncate max-w-[140px] font-medium">{it.productName}</span>
                              <span className="text-gray-400">×{it.quantity}</span>
                            </div>
                          ))}
                          {order.items.length > 2 && (
                            <div className="text-[10px] text-gray-400 font-medium">
                              共 {order.items.length} 种商品
                            </div>
                          )}
                        </div>
                      </td>

                      {/* Financial Amount */}
                      <td className="py-3 px-4 text-right">
                        <div className="font-bold text-gray-900 text-sm">
                          ¥{order.amount.toFixed(2)}
                        </div>
                        <div className="text-[11px] text-gray-400 mt-0.5">
                          {order.discountAmount ? `已减 ¥${order.discountAmount}` : '微信/支付宝'}
                        </div>
                      </td>

                      {/* Status */}
                      <td className="py-3 px-4 text-center">
                        {statusBadges}
                      </td>

                      {/* Actions */}
                      <td className="py-3 px-4 text-right">
                        <div className="flex items-center justify-end gap-1">
                          <button
                            onClick={() => setInspectOrder(order)}
                            className="p-1.5 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors"
                            title="查看完整履约与物流追踪"
                          >
                            <Eye className="w-4 h-4" />
                          </button>

                          {order.status === 'pending_shipment' && (
                            <PermissionGate permission="order:ship">
                              <button
                                onClick={() => {
                                  setShippingOrder(order);
                                  setTrackingNumber(`SF${Math.floor(10000000000 + Math.random() * 90000000000)}`);
                                }}
                                className="px-2 py-1 text-xs font-semibold bg-orange-50 text-orange-700 hover:bg-orange-100 rounded border border-orange-200 transition-colors"
                                title="单笔发货"
                              >
                                发货
                              </button>
                            </PermissionGate>
                          )}

                          {order.status === 'shipped' && !order.refundStatus && (
                            <PermissionGate permission="order:refund">
                              <button
                                onClick={() => handleOpenRefund(order)}
                                className="p-1.5 text-gray-500 hover:text-purple-600 hover:bg-purple-50 rounded transition-colors"
                                title="售后退款处理"
                              >
                                <RotateCcw className="w-4 h-4" />
                              </button>
                            </PermissionGate>
                          )}

                          <PermissionGate permission="order:remark">
                            <button
                              onClick={() => handleOpenRemark(order)}
                              className="p-1.5 text-gray-500 hover:text-amber-600 hover:bg-amber-50 rounded transition-colors"
                              title="修改卖家备注"
                            >
                              <FileText className="w-4 h-4" />
                            </button>
                          </PermissionGate>

                          {order.status !== 'cancelled' && order.status !== 'completed' && order.status !== 'refunded' && order.status !== 'refunding' && (
                            <PermissionGate permission="order:cancel">
                              <button
                                onClick={() => {
                                  if (window.confirm(`确认取消订单 ${order.orderNumber} 吗？`)) {
                                    if (requirePermission('order:cancel', '取消订单')) cancelOrder(order.id);
                                  }
                                }}
                                className="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded transition-colors"
                                title="取消订单"
                              >
                                <Ban className="w-4 h-4" />
                              </button>
                            </PermissionGate>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        <div className="bg-[#F8FAFC] border-t border-[#E2E8F0] px-4 py-3 flex items-center justify-between text-xs text-gray-500">
          <div>
            显示第 {(currentPage - 1) * pageSize + 1} -{' '}
            {Math.min(currentPage * pageSize, totalEntries)} 笔，共 {totalEntries} 笔订单
          </div>

          <div className="flex items-center gap-1.5">
            <button
              onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
              disabled={currentPage === 1}
              className="px-2.5 py-1 rounded border border-[#E2E8F0] hover:bg-white disabled:opacity-40 disabled:pointer-events-none transition-colors"
            >
              上一页
            </button>

            {Array.from({ length: totalPages }, (_, i) => i + 1).map((page) => (
              <button
                key={page}
                onClick={() => setCurrentPage(page)}
                className={`w-7 h-7 rounded text-xs font-medium transition-all ${
                  currentPage === page
                    ? 'bg-[#2563EB] text-white font-bold'
                    : 'border border-[#E2E8F0] hover:bg-white text-gray-700'
                }`}
              >
                {page}
              </button>
            ))}

            <button
              onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
              disabled={currentPage === totalPages}
              className="px-2.5 py-1 rounded border border-[#E2E8F0] hover:bg-white disabled:opacity-40 disabled:pointer-events-none transition-colors"
            >
              下一页
            </button>
          </div>
        </div>
      </div>

      {/* Inspect Order Details Modal with Timeline */}
      {inspectOrder && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white rounded-xl max-w-2xl w-full p-6 border border-gray-200 shadow-2xl animate-in zoom-in-95 max-h-[90vh] flex flex-col">
            <div className="flex items-center justify-between pb-3 border-b border-gray-200">
              <div className="flex items-center gap-3">
                <h3 className="text-base font-bold text-gray-900">
                  订单全生命周期档案与履约详情
                </h3>
                <div className="flex items-center gap-1">
                  {getFlagBadge(inspectOrder.flagColor)}
                </div>
              </div>
              <button
                onClick={() => setInspectOrder(null)}
                className="text-gray-400 hover:text-gray-600 p-1.5 rounded-lg"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="py-4 space-y-4 text-sm text-gray-700 overflow-y-auto flex-1 pr-1">
              {/* Order Basic Card */}
              <div className="p-3.5 bg-gray-50 rounded-xl border border-gray-200 grid grid-cols-2 sm:grid-cols-3 gap-3 text-xs">
                <div>
                  <span className="text-gray-400 block mb-0.5">订单编号</span>
                  <span className="font-mono font-bold text-gray-900">{inspectOrder.orderNumber}</span>
                </div>
                <div>
                  <span className="text-gray-400 block mb-0.5">下单成交时间</span>
                  <span className="text-gray-800 font-semibold">{inspectOrder.createdAt}</span>
                </div>
                <div>
                  <span className="text-gray-400 block mb-0.5">支付渠道</span>
                  <span className="text-gray-800 font-semibold">{inspectOrder.paymentMethod || '微信在线支付'}</span>
                </div>
                <div>
                  <span className="text-gray-400 block mb-0.5">收货人姓名</span>
                  <span className="text-gray-900 font-bold">{inspectOrder.customerName}</span>
                </div>
                <div>
                  <span className="text-gray-400 block mb-0.5">联系电话</span>
                  <span className="text-gray-900 font-mono">{inspectOrder.customerPhone}</span>
                </div>
                <div>
                  <span className="text-gray-400 block mb-0.5">发票税号</span>
                  <span className="text-gray-600 font-mono">{inspectOrder.taxId || '个人普通发票'}</span>
                </div>
                <div className="col-span-2 sm:col-span-3 pt-2 border-t border-gray-200">
                  <span className="text-gray-400 block mb-0.5">收货地址</span>
                  <span className="text-gray-800 flex items-center gap-1">
                    <MapPin className="w-3.5 h-3.5 text-blue-600 shrink-0" />
                    {inspectOrder.shippingAddress}
                  </span>
                </div>
              </div>

              {/* Items List */}
              <div>
                <h4 className="text-xs font-bold text-gray-700 uppercase tracking-wider mb-2 flex items-center gap-1">
                  <ShoppingBag className="w-3.5 h-3.5 text-blue-600" />
                  <span>购买商品明细清单</span>
                </h4>
                <div className="space-y-2">
                  {inspectOrder.items.map((item, idx) => (
                    <div
                      key={idx}
                      className="flex items-center justify-between p-3 bg-white border border-gray-200 rounded-lg shadow-2xs"
                    >
                      <div className="flex items-center gap-3">
                        <img
                          src={item.imageUrl}
                          alt={item.productName}
                          className="w-12 h-12 rounded-lg object-cover border border-gray-200"
                        />
                        <div>
                          <p className="font-semibold text-xs text-gray-900">
                            {item.productName}
                          </p>
                          <p className="text-xs text-gray-500 mt-0.5">
                            商品编号 #{item.productId}
                          </p>
                        </div>
                      </div>
                      <div className="text-right">
                        <div className="font-bold text-gray-900 text-sm">
                          ¥{(item.price * item.quantity).toFixed(2)}
                        </div>
                        <div className="text-xs text-gray-400">
                          ¥{item.price.toFixed(2)} × {item.quantity} 件
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Financial Calculation Bar */}
              <div className="p-3 bg-blue-50/50 rounded-xl border border-blue-100 space-y-1.5 text-xs">
                <div className="flex justify-between text-gray-600">
                  <span>商品小计金额:</span>
                  <span className="font-mono">¥{(inspectOrder.amount + (inspectOrder.discountAmount || 0)).toFixed(2)}</span>
                </div>
                {inspectOrder.discountAmount ? (
                  <div className="flex justify-between text-emerald-700">
                    <span>优惠券抵扣减免:</span>
                    <span className="font-mono">-¥{inspectOrder.discountAmount.toFixed(2)}</span>
                  </div>
                ) : null}
                <div className="flex justify-between text-gray-600">
                  <span>运费:</span>
                  <span className="font-mono">¥0.00 (包邮)</span>
                </div>
                <div className="flex justify-between text-sm font-bold text-gray-900 pt-1.5 border-t border-blue-200/60">
                  <span>买家实付总金额:</span>
                  <span className="text-blue-700 text-base">¥{inspectOrder.amount.toFixed(2)}</span>
                </div>
              </div>

              {/* Logistics Timeline */}
              <div>
                <h4 className="text-xs font-bold text-gray-700 uppercase tracking-wider mb-2 flex items-center gap-1">
                  <Truck className="w-3.5 h-3.5 text-blue-600" />
                  <span>物流轨迹与履约流转节点 (Logistics Steps)</span>
                </h4>

                {inspectOrder.logisticsSteps && inspectOrder.logisticsSteps.length > 0 ? (
                  <div className="p-3.5 bg-gray-50 rounded-xl border border-gray-200 space-y-3">
                    {inspectOrder.logisticsSteps.map((step, sIdx) => (
                      <div key={sIdx} className="flex gap-3 relative">
                        <div className="flex flex-col items-center">
                          <div className={`w-3 h-3 rounded-full ${sIdx === 0 ? 'bg-blue-600 ring-4 ring-blue-100' : 'bg-gray-300'}`} />
                          {sIdx < inspectOrder.logisticsSteps!.length - 1 && (
                            <div className="w-0.5 flex-1 bg-gray-200 my-1" />
                          )}
                        </div>
                        <div className="flex-1 pb-2">
                          <div className="flex items-center justify-between text-xs">
                            <span className="font-bold text-gray-900">{step.title}</span>
                            <span className="text-gray-400 font-mono">{step.time}</span>
                          </div>
                          <p className="text-xs text-gray-600 mt-0.5">{step.desc}</p>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="p-4 bg-gray-50 rounded-xl border border-gray-200 text-center text-xs text-gray-400">
                    该订单尚在备货中，暂无物流节点数据
                  </div>
                )}
              </div>
            </div>

            <div className="pt-3 border-t border-gray-200 flex justify-end gap-2">
              <button
                onClick={() => setInspectOrder(null)}
                className="px-4 py-2 bg-gray-100 text-gray-700 text-xs font-semibold rounded-lg hover:bg-gray-200 transition-colors"
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Single Ship Order Modal */}
      {shippingOrder && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white rounded-xl max-w-md w-full p-6 border border-gray-200 shadow-2xl animate-in zoom-in-95">
            <div className="flex items-center justify-between pb-3 border-b border-gray-200">
              <div className="flex items-center gap-2 text-orange-600">
                <Truck className="w-5 h-5" />
                <h3 className="text-base font-bold text-gray-900">订单发货出库录入</h3>
              </div>
              <button
                onClick={() => setShippingOrder(null)}
                className="text-gray-400 hover:text-gray-600 p-1.5 rounded-lg"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleConfirmShipment} className="py-4 space-y-3.5 text-sm">
              <div className="p-3 bg-gray-50 rounded-lg text-xs space-y-1">
                <p className="font-semibold text-gray-800">
                  订单号: #{shippingOrder.orderNumber}
                </p>
                <p className="text-gray-600">收件人: {shippingOrder.customerName} ({shippingOrder.customerPhone})</p>
                <p className="text-gray-500 truncate">地址: {shippingOrder.shippingAddress}</p>
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  物流承运商
                </label>
                <select
                  value={carrier}
                  onChange={(e) => setCarrier(e.target.value)}
                  className="w-full h-[36px] px-3 rounded-lg border border-gray-300 focus:border-blue-500 outline-none text-sm bg-white"
                >
                  <option value="顺丰速运">顺丰速运 (SF Express)</option>
                  <option value="中通快递">中通快递 (ZTO Express)</option>
                  <option value="圆通速递">圆通速递 (YTO Express)</option>
                  <option value="京东快递">京东快递 (JD Logistics)</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  物流运单号 *
                </label>
                <input
                  type="text"
                  required
                  value={trackingNumber}
                  onChange={(e) => setTrackingNumber(e.target.value)}
                  placeholder="请输入或扫描运单号..."
                  className="w-full h-[36px] px-3 rounded-lg border border-gray-300 focus:border-blue-500 outline-none text-sm font-mono"
                />
              </div>

              <div className="pt-3 border-t border-gray-200 flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setShippingOrder(null)}
                  className="px-4 py-2 rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-50 text-xs font-medium"
                >
                  取消
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 rounded-lg bg-orange-600 text-white hover:bg-orange-700 text-xs font-semibold shadow-xs flex items-center gap-1.5"
                >
                  <CheckCircle2 className="w-4 h-4" />
                  <span>确认发货并出库</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Batch Ship Modal */}
      {isBatchShipModalOpen && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white rounded-xl max-w-md w-full p-6 border border-gray-200 shadow-2xl animate-in zoom-in-95">
            <div className="flex items-center justify-between pb-3 border-b border-gray-200">
              <h3 className="text-base font-bold text-gray-900">
                批量打单发货 (Batch Ship)
              </h3>
              <button
                onClick={() => setIsBatchShipModalOpen(false)}
                className="text-gray-400 hover:text-gray-600"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="py-4 space-y-3.5 text-sm">
              <p className="text-xs text-gray-600 leading-relaxed">
                系统将为选中的待发货订单自动批量生成运单号并推进至「已发货」履约节点。
              </p>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  选择承运快递服务商
                </label>
                <select
                  value={batchCarrier}
                  onChange={(e) => setBatchCarrier(e.target.value)}
                  className="w-full h-[36px] px-3 rounded-lg border border-gray-300 bg-white text-sm"
                >
                  <option value="顺丰速运">顺丰速运 (SF Express)</option>
                  <option value="京东快递">京东快递 (JD Express)</option>
                  <option value="中通快递">中通快递 (ZTO Express)</option>
                  <option value="圆通速递">圆通速递 (YTO Express)</option>
                </select>
              </div>
            </div>

            <div className="pt-3 border-t border-gray-200 flex justify-end gap-2">
              <button
                onClick={() => setIsBatchShipModalOpen(false)}
                className="px-4 py-2 rounded-lg border border-gray-300 text-gray-700 text-xs font-medium"
              >
                取消
              </button>
              <button
                onClick={handleBatchShip}
                className="px-5 py-2 rounded-lg bg-orange-600 hover:bg-orange-700 text-white text-xs font-semibold shadow-xs"
              >
                一键批量出库
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Remark & Flag Color Modal */}
      {remarkOrder && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white rounded-xl max-w-md w-full p-6 border border-gray-200 shadow-2xl animate-in zoom-in-95">
            <div className="flex items-center justify-between pb-3 border-b border-gray-200">
              <h3 className="text-base font-bold text-gray-900">
                订单标旗与卖家专属备注
              </h3>
              <button
                onClick={() => setRemarkOrder(null)}
                className="text-gray-400 hover:text-gray-600"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="py-4 space-y-4 text-sm">
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-2">
                  设定标旗等级 (Priority Flag)
                </label>
                <div className="flex items-center gap-3">
                  <label className="flex items-center gap-1.5 cursor-pointer text-xs">
                    <input
                      type="radio"
                      name="flag"
                      checked={remarkFlag === 'red'}
                      onChange={() => setRemarkFlag('red')}
                    />
                    <Flag className="w-4 h-4 text-red-500 fill-red-500" />
                    <span>红旗 (加急催发)</span>
                  </label>
                  <label className="flex items-center gap-1.5 cursor-pointer text-xs">
                    <input
                      type="radio"
                      name="flag"
                      checked={remarkFlag === 'yellow'}
                      onChange={() => setRemarkFlag('yellow')}
                    />
                    <Flag className="w-4 h-4 text-amber-500 fill-amber-500" />
                    <span>黄旗 (需改地址)</span>
                  </label>
                  <label className="flex items-center gap-1.5 cursor-pointer text-xs">
                    <input
                      type="radio"
                      name="flag"
                      checked={remarkFlag === 'green'}
                      onChange={() => setRemarkFlag('green')}
                    />
                    <Flag className="w-4 h-4 text-emerald-500 fill-emerald-500" />
                    <span>绿旗 (赠品/VIP)</span>
                  </label>
                  <label className="flex items-center gap-1.5 cursor-pointer text-xs">
                    <input
                      type="radio"
                      name="flag"
                      checked={remarkFlag === 'blue'}
                      onChange={() => setRemarkFlag('blue')}
                    />
                    <Flag className="w-4 h-4 text-blue-500 fill-blue-500" />
                    <span>蓝旗 (普通)</span>
                  </label>
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  卖家内部备注内容
                </label>
                <textarea
                  rows={3}
                  value={remarkText}
                  onChange={(e) => setRemarkText(e.target.value)}
                  placeholder="仅内部运营客服可见，例如：买家要求顺丰特快并随单赠送清洁喷雾..."
                  className="w-full p-2.5 rounded-lg border border-gray-300 focus:border-blue-500 outline-none text-xs"
                />
              </div>
            </div>

            <div className="pt-3 border-t border-gray-200 flex justify-end gap-2">
              <button
                onClick={() => setRemarkOrder(null)}
                className="px-4 py-2 rounded-lg border border-gray-300 text-gray-700 text-xs font-medium"
              >
                取消
              </button>
              <button
                onClick={handleSaveRemark}
                className="px-5 py-2 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold"
              >
                保存备注
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Refund Workflow Modal */}
      {refundOrder && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white rounded-xl max-w-md w-full p-6 border border-gray-200 shadow-2xl animate-in zoom-in-95">
            <div className="flex items-center justify-between pb-3 border-b border-gray-200 text-purple-700">
              <div className="flex items-center gap-2">
                <RotateCcw className="w-5 h-5" />
                <h3 className="text-base font-bold text-gray-900">
                  售后退款审核与原路退款
                </h3>
              </div>
              <button
                onClick={() => setRefundOrder(null)}
                className="text-gray-400 hover:text-gray-600"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="py-4 space-y-3.5 text-sm">
              <div className="p-3 bg-purple-50 rounded-lg border border-purple-100 text-xs space-y-1">
                <p className="font-semibold text-gray-900">订单号: #{refundOrder.orderNumber}</p>
                <p className="text-gray-600">客户: {refundOrder.customerName} | 实付: ¥{refundOrder.amount.toFixed(2)}</p>
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  退款金额 (¥) *
                </label>
                <input
                  type="number"
                  step="0.01"
                  max={refundOrder.amount}
                  value={refundAmount}
                  onChange={(e) => setRefundAmount(parseFloat(e.target.value) || 0)}
                  className="w-full h-[36px] px-3 rounded-lg border border-gray-300 font-semibold text-sm"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  退款原由
                </label>
                <input
                  type="text"
                  value={refundReason}
                  onChange={(e) => setRefundReason(e.target.value)}
                  className="w-full h-[36px] px-3 rounded-lg border border-gray-300 text-xs"
                />
              </div>
            </div>

            <div className="pt-3 border-t border-gray-200 flex justify-end gap-2">
              <button
                onClick={() => setRefundOrder(null)}
                className="px-4 py-2 rounded-lg border border-gray-300 text-gray-700 text-xs font-medium"
              >
                取消
              </button>
              <button
                onClick={handleConfirmRefund}
                className="px-5 py-2 rounded-lg bg-purple-600 hover:bg-purple-700 text-white text-xs font-semibold shadow-xs"
              >
                同意并原路退款
              </button>
            </div>
          </div>
        </div>
      )}

      {/* New Order Modal */}
      {isNewOrderModalOpen && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white rounded-xl max-w-lg w-full p-6 border border-gray-200 shadow-2xl animate-in zoom-in-95">
            <div className="flex items-center justify-between pb-3 border-b border-gray-200">
              <h3 className="text-base font-bold text-gray-900">
                代客录入新订单 (Manual Order Entry)
              </h3>
              <button
                onClick={() => setIsNewOrderModalOpen(false)}
                className="text-gray-400 hover:text-gray-600 p-1.5 rounded-lg"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleCreateOrder} className="py-4 space-y-3.5 text-sm">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">
                    客户姓名 *
                  </label>
                  <input
                    type="text"
                    required
                    value={newOrderCustomer}
                    onChange={(e) => setNewOrderCustomer(e.target.value)}
                    placeholder="如: 张先生"
                    className="w-full h-[36px] px-3 rounded-lg border border-gray-300 focus:border-blue-500 outline-none text-sm"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">
                    手机号码 *
                  </label>
                  <input
                    type="tel"
                    required
                    value={newOrderPhone}
                    onChange={(e) => setNewOrderPhone(e.target.value)}
                    placeholder="13800000000"
                    className="w-full h-[36px] px-3 rounded-lg border border-gray-300 focus:border-blue-500 outline-none text-sm"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  订购商品
                </label>
                <select
                  value={selectedProductId}
                  onChange={(e) => setSelectedProductId(e.target.value)}
                  className="w-full h-[36px] px-3 rounded-lg border border-gray-300 focus:border-blue-500 outline-none text-sm bg-white"
                >
                  {products.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name} (¥{p.price.toFixed(2)})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  购买数量
                </label>
                <input
                  type="number"
                  min="1"
                  value={itemQuantity}
                  onChange={(e) => setItemQuantity(parseInt(e.target.value, 10) || 1)}
                  className="w-full h-[36px] px-3 rounded-lg border border-gray-300 focus:border-blue-500 outline-none text-sm"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  详细收货地址 *
                </label>
                <input
                  type="text"
                  required
                  value={newOrderAddress}
                  onChange={(e) => setNewOrderAddress(e.target.value)}
                  placeholder="省/市/区/详细街道与门牌号"
                  className="w-full h-[36px] px-3 rounded-lg border border-gray-300 focus:border-blue-500 outline-none text-sm"
                />
              </div>

              <div className="pt-3 border-t border-gray-200 flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setIsNewOrderModalOpen(false)}
                  className="px-4 py-2 rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-50 text-xs font-medium cursor-pointer"
                >
                  取消
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 rounded-lg bg-[#2563EB] text-white hover:bg-blue-700 text-xs font-semibold shadow-xs cursor-pointer"
                >
                  创建订单
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
