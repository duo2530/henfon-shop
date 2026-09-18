import React, { FormEvent, useCallback, useEffect, useState } from 'react';
import { useAdmin } from '../../context/AdminContext';
import { useExportCenter } from '../../context/ExportCenterContext';
import {
  BackendInventoryWarehouse,
  BackendInventoryStock,
  BackendInventoryStockLock,
  adjustInventoryStock,
  deleteInventoryWarehouse,
  listInventoryStocks,
  listInventoryStockLocks,
  listInventoryWarehouses,
  saveInventoryWarehouse,
  updateInventoryWarehouseStatus
} from '../../api/adminApi';
import { formatDateTime } from '../../utils/datetime';
import { 
  Boxes, 
  ArrowDownToLine, 
  ArrowUpFromLine, 
  Repeat, 
  Search, 
  Plus, 
  CheckCircle2, 
  Clock, 
  AlertTriangle, 
  Download, 
  Warehouse,
  Edit3,
  Trash2,
  X,
  Loader2,
  Save,
  RefreshCw
} from 'lucide-react';
import { useBodyScrollLock } from '../../hooks/useBodyScrollLock';

export interface StockOrder {
  id: string;
  stockId: number;
  orderNo: string;
  type: 'inbound_purchase' | 'outbound_sale' | 'transfer' | 'loss_audit' | 'stock';
  warehouseName: string;
  targetWarehouse?: string;
  productName: string;
  sku: string;
  quantity: number;
  operator: string;
  status: 'completed' | 'processing' | 'pending';
  createdAt: string;
  remarks?: string;
}

// 进销存单据历史待接入库存流水接口，当前仅展示服务端库存锁定流水，避免伪造业务记录。

interface WarehouseForm {
  id?: number;
  warehouseCode: string;
  warehouseName: string;
  status: number;
  isDefault: number;
  remark: string;
  version?: number;
}

const emptyWarehouseForm = (): WarehouseForm => ({
  warehouseCode: '',
  warehouseName: '',
  status: 1,
  isDefault: 0,
  remark: ''
});

/**
 * 库存流水的业务类型。
 *
 * 取值与后端 StockLogExportDataset 的 BIZ_TYPE_LABELS 一一对应，改动需两边同步。
 */
const STOCK_LOG_BIZ_TYPES: { value: string; label: string }[] = [
  { value: 'RESERVE', label: '下单预占' },
  { value: 'RELEASE', label: '取消释放' },
  { value: 'EXPIRE_RELEASE', label: '超时释放' },
  { value: 'DEDUCT', label: '支付扣减' }
];

export const WarehouseStockView: React.FC = () => {
  const { showToast, confirm, requirePermission } = useAdmin();
  const { submit: submitExportTask } = useExportCenter();
  const [searchTerm, setSearchTerm] = useState('');
  const [typeFilter, setTypeFilter] = useState('all');
  const [warehouses, setWarehouses] = useState<BackendInventoryWarehouse[]>([]);
  const [warehouseLoading, setWarehouseLoading] = useState(true);
  const [warehouseError, setWarehouseError] = useState<string | null>(null);
  const [editingWarehouse, setEditingWarehouse] = useState<WarehouseForm | null>(null);
  const [warehouseSaving, setWarehouseSaving] = useState(false);
  const [warehouseActionId, setWarehouseActionId] = useState<number | null>(null);
  const [stockLocks, setStockLocks] = useState<BackendInventoryStockLock[]>([]);
  const [stockLocksLoading, setStockLocksLoading] = useState(true);
  const [stockLocksError, setStockLocksError] = useState<string | null>(null);
  const [inventoryStocks, setInventoryStocks] = useState<BackendInventoryStock[]>([]);
  const [inventoryStocksLoading, setInventoryStocksLoading] = useState(true);
  const [inventoryStocksError, setInventoryStocksError] = useState<string | null>(null);
  const [stockActionId, setStockActionId] = useState<number | null>(null);
  const [exportPanelOpen, setExportPanelOpen] = useState(false);
  const [exportBizType, setExportBizType] = useState('all');
  const [exportSkuId, setExportSkuId] = useState('');

  const loadInventoryStocks = useCallback(async () => {
    setInventoryStocksLoading(true);
    setInventoryStocksError(null);
    try {
      const page = await listInventoryStocks({ current: 1, size: 200 });
      setInventoryStocks(page.records || []);
    } catch (error) {
      const message = error instanceof Error ? error.message : '库存台账加载失败';
      setInventoryStocksError(message);
      showToast(`${message}，请稍后重试`, 'error');
    } finally {
      setInventoryStocksLoading(false);
    }
  }, [showToast]);

  useEffect(() => {
    void loadInventoryStocks();
  }, [loadInventoryStocks]);

  const loadWarehouses = useCallback(async () => {
    setWarehouseLoading(true);
    setWarehouseError(null);
    try {
      const page = await listInventoryWarehouses({ current: 1, size: 200 });
      setWarehouses(page.records || []);
    } catch (error) {
      const message = error instanceof Error ? error.message : '仓库数据加载失败';
      setWarehouseError(message);
      showToast(`${message}，请稍后重试`, 'error');
    } finally {
      setWarehouseLoading(false);
    }
  }, [showToast]);

  useEffect(() => {
    void loadWarehouses();
  }, [loadWarehouses]);

  const loadStockLocks = useCallback(async () => {
    setStockLocksLoading(true);
    setStockLocksError(null);
    try {
      const page = await listInventoryStockLocks({ current: 1, size: 100 });
      setStockLocks(page.records || []);
    } catch (error) {
      const message = error instanceof Error ? error.message : '库存锁定流水加载失败';
      setStockLocksError(message);
      showToast(`${message}，请稍后重试`, 'error');
    } finally {
      setStockLocksLoading(false);
    }
  }, [showToast]);

  useEffect(() => {
    void loadStockLocks();
  }, [loadStockLocks]);

  const openWarehouseForm = (warehouse?: BackendInventoryWarehouse) => {
    setEditingWarehouse(warehouse ? {
      id: warehouse.id,
      warehouseCode: warehouse.warehouseCode,
      warehouseName: warehouse.warehouseName,
      status: warehouse.status,
      isDefault: warehouse.isDefault,
      remark: warehouse.remark || '',
      version: warehouse.version
    } : emptyWarehouseForm());
  };

  const updateWarehouseForm = <K extends keyof WarehouseForm>(key: K, value: WarehouseForm[K]) => {
    setEditingWarehouse((current) => current ? { ...current, [key]: value } : current);
  };

  const submitWarehouse = async (event: FormEvent) => {
    event.preventDefault();
    if (!editingWarehouse) return;
    const payload = {
      ...editingWarehouse,
      warehouseCode: editingWarehouse.warehouseCode.trim(),
      warehouseName: editingWarehouse.warehouseName.trim(),
      remark: editingWarehouse.remark.trim() || undefined
    };
    if (!payload.warehouseCode || !payload.warehouseName) {
      showToast('请填写仓库编码和仓库名称', 'warning');
      return;
    }
    setWarehouseSaving(true);
    try {
      await saveInventoryWarehouse(payload);
      setEditingWarehouse(null);
      await loadWarehouses();
      showToast('仓库保存成功', 'success');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '仓库保存失败', 'error');
    } finally {
      setWarehouseSaving(false);
    }
  };

  const toggleWarehouseStatus = async (warehouse: BackendInventoryWarehouse) => {
    const nextStatus = warehouse.status === 1 ? 0 : 1;
    const previous = warehouses;
    setWarehouseActionId(warehouse.id);
    setWarehouses((current) => current.map((item) => item.id === warehouse.id ? { ...item, status: nextStatus } : item));
    try {
      await updateInventoryWarehouseStatus(warehouse.id, nextStatus);
      showToast(nextStatus === 1 ? '仓库已启用' : '仓库已停用', 'success');
    } catch (error) {
      setWarehouses(previous);
      showToast(error instanceof Error ? error.message : '仓库状态更新失败', 'error');
    } finally {
      setWarehouseActionId(null);
    }
  };

  const removeWarehouse = async (warehouse: BackendInventoryWarehouse) => {
    if (!await confirm(`确认删除仓库「${warehouse.warehouseName}」吗？有库存台账的仓库无法删除。`, '删除仓库')) return;
    const previous = warehouses;
    setWarehouseActionId(warehouse.id);
    setWarehouses((current) => current.filter((item) => item.id !== warehouse.id));
    try {
      await deleteInventoryWarehouse(warehouse.id);
      showToast('仓库已删除', 'success');
    } catch (error) {
      setWarehouses(previous);
      showToast(error instanceof Error ? error.message : '仓库删除失败', 'error');
    } finally {
      setWarehouseActionId(null);
    }
  };

  const adjustStock = async (stock: BackendInventoryStock, changeQuantity: number) => {
    setStockActionId(stock.id);
    try {
      await adjustInventoryStock(stock.id, changeQuantity, '管理端库存快速调整');
      await loadInventoryStocks();
      showToast(`库存已${changeQuantity > 0 ? '增加' : '减少'} ${Math.abs(changeQuantity)} 件`, 'success');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '库存调整失败', 'error');
    } finally {
      setStockActionId(null);
    }
  };

  const stockOrders: StockOrder[] = inventoryStocks.map((stock) => {
    const warehouse = warehouses.find((item) => item.id === stock.warehouseId);
    return {
      id: `stock-${stock.id}`,
      stockId: stock.id,
      orderNo: `STOCK-${stock.id}`,
      type: 'stock',
      warehouseName: warehouse?.warehouseName || `仓库 #${stock.warehouseId}`,
      productName: stock.productId ? `商品 #${stock.productId}` : '未关联商品',
      sku: `SKU #${stock.skuId}`,
      quantity: stock.availableStock,
      operator: '—',
      status: 'completed',
      createdAt: formatDateTime(stock.updatedAt, '—'),
      remarks: stock.remark
    };
  });

  const filtered = stockOrders.filter((o) => {
    const matchSearch =
      searchTerm === '' ||
      o.orderNo.toLowerCase().includes(searchTerm.toLowerCase()) ||
      o.productName.toLowerCase().includes(searchTerm.toLowerCase()) ||
      o.sku.toLowerCase().includes(searchTerm.toLowerCase());
    const matchType = typeFilter === 'all' || o.type === typeFilter;
    return matchSearch && matchType;
  });

  const handleExportStockLogs = () => {
    if (!requirePermission('inventory:stock:export', '导出库存流水')) return;
    const trimmedSkuId = exportSkuId.trim();
    if (trimmedSkuId && !/^\d+$/.test(trimmedSkuId)) {
      showToast('SKU ID 只能填数字', 'warning');
      return;
    }
    // 导出的库存变动流水与页面上的库存台账不是同一份数据，字段与取值域都对不上，
    // 因此这里不套用页面顶部作用于台账的关键字与单据类型，改用导出自己的两个条件。
    void submitExportTask({
      exportType: 'STOCK_LOG',
      bizType: exportBizType === 'all' ? undefined : exportBizType,
      skuId: trimmedSkuId ? Number(trimmedSkuId) : undefined
    });
    setExportPanelOpen(false);
  };

  // 弹层打开期间锁住底层文档滚动，避免出现滚动穿透。
  useBodyScrollLock(Boolean(editingWarehouse));
  return (
    <div className="space-y-6 animate-in fade-in-50 duration-200">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-xl md:text-2xl font-bold text-[#191C1E] tracking-tight">
              仓库进销存与调拨 (Warehouse & Stock In/Out)
            </h2>
            <span className="text-xs bg-cyan-50 text-cyan-700 font-semibold px-2 py-0.5 rounded-full border border-cyan-200">
              WMS 智能仓配
            </span>
          </div>
          <p className="text-xs md:text-sm text-[#434655] mt-0.5">
            采购验收入库单、销售发货出库、跨仓库存调拨单及盘点报损报溢记录。
          </p>
        </div>

        <div className="flex items-center gap-3">
          <div className="relative">
            <button
              type="button"
              onClick={() => setExportPanelOpen((previous) => !previous)}
              aria-expanded={exportPanelOpen}
              aria-haspopup="dialog"
              className="h-[36px] px-3.5 rounded-lg border border-[#E2E8F0] bg-white text-gray-700 hover:bg-gray-50 flex items-center gap-2 text-xs font-semibold shadow-2xs transition-colors cursor-pointer"
            >
              <Download className="w-4 h-4 text-gray-500" />
              <span>导出库存流水</span>
            </button>

            {exportPanelOpen && (
              <>
                <div className="fixed inset-0 z-40" onClick={() => setExportPanelOpen(false)} />
                <div className="absolute right-0 mt-2 w-64 bg-white rounded-xl shadow-lg border border-[#E2E8F0] z-50 p-4 space-y-3 text-left">
                  <p className="text-xs text-gray-500 leading-relaxed">
                    导出库存变动明细，可按下面两个条件收窄；都不填则导出全部。
                  </p>
                  <label className="block text-xs text-gray-600">
                    业务类型
                    <select
                      value={exportBizType}
                      onChange={(event) => setExportBizType(event.target.value)}
                      className="mt-1 w-full h-9 px-2 rounded-lg border border-[#E2E8F0] text-sm bg-white"
                    >
                      <option value="all">全部</option>
                      {STOCK_LOG_BIZ_TYPES.map((item) => (
                        <option key={item.value} value={item.value}>{item.label}</option>
                      ))}
                    </select>
                  </label>
                  <label className="block text-xs text-gray-600">
                    SKU ID
                    <input
                      inputMode="numeric"
                      value={exportSkuId}
                      onChange={(event) => setExportSkuId(event.target.value)}
                      placeholder="留空则不按 SKU 筛选"
                      className="mt-1 w-full h-9 px-3 rounded-lg border border-[#E2E8F0] text-sm"
                    />
                  </label>
                  <button
                    type="button"
                    onClick={handleExportStockLogs}
                    className="w-full h-9 rounded-lg bg-[#2563EB] text-white hover:bg-blue-700 text-xs font-semibold cursor-pointer"
                  >
                    提交导出任务
                  </button>
                </div>
              </>
            )}
          </div>

          <button
            onClick={() => showToast('已创建新入库调拨单向导', 'info')}
            className="h-[36px] px-4 rounded-lg bg-[#2563EB] text-white hover:bg-blue-700 flex items-center justify-center gap-2 text-xs font-semibold shadow-xs"
          >
            <Plus className="w-4 h-4" />
            <span>新建入库/调拨单</span>
          </button>
        </div>
      </div>

      {/* Warehouse Management */}
      <section className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs overflow-hidden">
        <div className="p-4 border-b border-[#E2E8F0] bg-[#F8FAFC]/50 flex flex-wrap gap-3 items-center justify-between">
          <div>
            <h3 className="text-sm font-bold text-gray-900">仓库基础管理</h3>
            <p className="text-xs text-gray-500 mt-1">维护仓库编码、启停状态和默认仓库，库存台账会关联这里的仓库。</p>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => void loadWarehouses()}
              disabled={warehouseLoading}
              className="h-9 px-3 rounded-lg border border-[#E2E8F0] text-xs font-semibold text-gray-600 hover:text-blue-600 disabled:opacity-50 flex items-center gap-1.5"
              title="刷新仓库"
            >
              <RefreshCw className={`w-4 h-4 ${warehouseLoading ? 'animate-spin' : ''}`} />刷新
            </button>
            <button
              type="button"
              onClick={() => openWarehouseForm()}
              className="h-9 px-3 rounded-lg bg-[#2563EB] text-white hover:bg-blue-700 flex items-center gap-1.5 text-xs font-semibold"
            >
              <Plus className="w-4 h-4" />新增仓库
            </button>
          </div>
        </div>
        {warehouseLoading ? (
          <div className="flex items-center justify-center py-12 text-sm text-gray-500"><Loader2 className="w-4 h-4 mr-2 animate-spin" />正在加载仓库…</div>
        ) : warehouseError ? (
          <div className="py-12 text-center text-sm text-red-600">
            <p>{warehouseError}</p>
            <button type="button" onClick={() => void loadWarehouses()} className="mt-3 text-blue-600 hover:underline">重新加载</button>
          </div>
        ) : warehouses.length === 0 ? (
          <div className="py-12 text-center text-sm text-gray-500">暂无仓库，请先新增仓库。</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead className="bg-white border-b border-[#E2E8F0] text-xs font-semibold text-gray-500">
                <tr>
                  <th className="py-3 px-4">仓库</th>
                  <th className="py-3 px-4">编码</th>
                  <th className="py-3 px-4 text-center">状态</th>
                  <th className="py-3 px-4">备注</th>
                  <th className="py-3 px-4 text-right">操作</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 text-sm">
                {warehouses.map((warehouse) => {
                  const actionLoading = warehouseActionId === warehouse.id;
                  return (
                    <tr key={warehouse.id} className="hover:bg-[#F8FAFC]">
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-2 text-xs font-semibold text-gray-800">
                          <Warehouse className="w-4 h-4 text-blue-600" />{warehouse.warehouseName}
                          {warehouse.isDefault === 1 && <span className="text-[10px] font-bold text-blue-700 bg-blue-50 px-1.5 py-0.5 rounded">默认</span>}
                        </div>
                      </td>
                      <td className="py-3 px-4 text-xs font-mono text-gray-500">{warehouse.warehouseCode}</td>
                      <td className="py-3 px-4 text-center">
                        <button
                          type="button"
                          disabled={actionLoading || warehouse.isDefault === 1}
                          onClick={() => void toggleWarehouseStatus(warehouse)}
                          className={`text-xs font-semibold px-2 py-1 rounded-full disabled:cursor-not-allowed disabled:opacity-60 ${warehouse.status === 1 ? 'text-emerald-800 bg-emerald-100' : 'text-gray-600 bg-gray-100'}`}
                          title={warehouse.isDefault === 1 ? '默认仓库不能停用' : '切换启停状态'}
                        >
                          {actionLoading ? '处理中…' : warehouse.status === 1 ? '已启用' : '已停用'}
                        </button>
                      </td>
                      <td className="py-3 px-4 text-xs text-gray-500 max-w-xs truncate">{warehouse.remark || '—'}</td>
                      <td className="py-3 px-4 text-right">
                        <div className="inline-flex items-center gap-1">
                          <button type="button" onClick={() => openWarehouseForm(warehouse)} className="p-2 text-gray-400 hover:text-blue-600 rounded" title="编辑仓库" aria-label="编辑仓库"><Edit3 className="w-4 h-4" /></button>
                          <button type="button" disabled={actionLoading || warehouse.isDefault === 1} onClick={() => void removeWarehouse(warehouse)} className="p-2 text-gray-400 hover:text-red-600 rounded disabled:opacity-40 disabled:cursor-not-allowed" title={warehouse.isDefault === 1 ? '默认仓库不能删除' : '删除仓库'} aria-label="删除仓库"><Trash2 className="w-4 h-4" /></button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Warehouse Summary Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {warehouses.slice(0, 3).map((warehouse, index) => (
          <div key={warehouse.id} className="bg-white p-4.5 rounded-xl border border-[#E2E8F0] shadow-xs">
            <div className="flex items-center gap-2 text-xs font-semibold text-gray-500 mb-1">
              <Warehouse className={`w-4 h-4 ${index === 0 ? 'text-blue-600' : index === 1 ? 'text-purple-600' : 'text-emerald-600'}`} />{warehouse.warehouseName}
            </div>
            <div className="text-2xl font-bold text-gray-900 mt-1">{warehouse.warehouseCode}</div>
            <p className={`text-xs mt-1 ${warehouse.status === 1 ? 'text-emerald-600' : 'text-gray-400'}`}>{warehouse.status === 1 ? '启用 · 库存台账可用' : '停用 · 暂不可用于库存业务'}</p>
          </div>
        ))}
        {warehouses.length === 0 && !warehouseLoading && !warehouseError && <div className="sm:col-span-3 text-center text-xs text-gray-400 py-4">暂无仓库摘要</div>}
      </div>

      <section className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs overflow-hidden">
        <div className="p-4 border-b border-[#E2E8F0] bg-[#F8FAFC]/50 flex items-center justify-between">
          <div>
            <h3 className="text-sm font-bold text-gray-900">库存锁定流水</h3>
            <p className="text-xs text-gray-500 mt-1">订单预占、释放和发货扣减均由服务端记录，可用于库存对账。</p>
          </div>
          <button type="button" onClick={() => void loadStockLocks()} disabled={stockLocksLoading} className="h-9 px-3 rounded-lg border border-[#E2E8F0] text-xs font-semibold text-gray-600 hover:text-blue-600 disabled:opacity-50 flex items-center gap-1.5">
            <RefreshCw className={`w-4 h-4 ${stockLocksLoading ? 'animate-spin' : ''}`} />刷新
          </button>
        </div>
        {stockLocksLoading ? (
          <div className="flex items-center justify-center py-10 text-sm text-gray-500"><Loader2 className="w-4 h-4 mr-2 animate-spin" />正在加载锁定流水…</div>
        ) : stockLocksError ? (
          <div className="py-10 text-center text-sm text-red-600"><p>{stockLocksError}</p><button type="button" onClick={() => void loadStockLocks()} className="mt-3 text-blue-600 hover:underline">重新加载</button></div>
        ) : stockLocks.length === 0 ? (
          <div className="py-10 text-center text-sm text-gray-400">暂无库存锁定流水</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead className="bg-white border-b border-[#E2E8F0] text-xs font-semibold text-gray-500"><tr><th className="py-3 px-4">锁定单号</th><th className="py-3 px-4">订单号</th><th className="py-3 px-4">SKU</th><th className="py-3 px-4 text-right">数量</th><th className="py-3 px-4 text-center">状态</th><th className="py-3 px-4 text-right">创建时间</th></tr></thead>
              <tbody className="divide-y divide-gray-100 text-sm">
                {stockLocks.map((lock) => <tr key={lock.id} className="hover:bg-[#F8FAFC]"><td className="py-3 px-4 text-xs font-mono font-semibold text-gray-800">{lock.lockNo}</td><td className="py-3 px-4 text-xs font-mono text-gray-600">{lock.orderNo || `订单 #${lock.orderId}`}</td><td className="py-3 px-4 text-xs font-mono text-gray-600">{lock.skuId}</td><td className="py-3 px-4 text-right text-xs font-mono font-bold text-gray-800">{lock.quantity}</td><td className="py-3 px-4 text-center"><span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${lock.status === 0 ? 'bg-amber-50 text-amber-700' : lock.status === 1 ? 'bg-emerald-50 text-emerald-700' : lock.status === 2 ? 'bg-blue-50 text-blue-700' : 'bg-gray-100 text-gray-600'}`}>{lock.status === 0 ? '已预占' : lock.status === 1 ? '已释放' : lock.status === 2 ? '已扣减' : `状态 ${lock.status}`}</span></td><td className="py-3 px-4 text-right text-xs font-mono text-gray-500">{formatDateTime(lock.createdAt, '—')}</td></tr>)}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Table */}
      <div className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs overflow-hidden">
        <div className="p-4 border-b border-[#E2E8F0] bg-[#F8FAFC]/50 flex flex-wrap gap-3 items-center justify-between">
          <div className="relative max-w-xs flex-1">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="搜索单号、SKU或商品..."
              className="w-full h-[36px] pl-9 pr-3 text-sm rounded-lg border border-[#E2E8F0] bg-white outline-none"
            />
          </div>

          <select
            value={typeFilter}
            onChange={(e) => setTypeFilter(e.target.value)}
            className="h-[36px] px-3 text-sm rounded-lg border border-[#E2E8F0] bg-white"
          >
            <option value="all">所有单据类型</option>
            <option value="inbound_purchase">采购入库 (Inbound)</option>
            <option value="outbound_sale">销售出库 (Outbound)</option>
            <option value="transfer">跨仓调拨 (Transfer)</option>
            <option value="loss_audit">盘点报损 (Audit)</option>
          </select>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead className="bg-[#F8FAFC] border-b border-[#E2E8F0] text-xs font-semibold text-gray-600 uppercase">
              <tr>
                <th className="py-3 px-4">单据编号</th>
                <th className="py-3 px-4">业务类型</th>
                <th className="py-3 px-4">关联仓库 / 目的仓</th>
                <th className="py-3 px-4">商品名称 / SKU</th>
                <th className="py-3 px-4 text-right">出入库数量</th>
                <th className="py-3 px-4 text-center">状态</th>
                <th className="py-3 px-4 text-right">操作人 / 时间</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 text-sm">
              {inventoryStocksLoading && <tr><td colSpan={7} className="py-12 text-center text-sm text-gray-500"><Loader2 className="w-4 h-4 mr-2 inline animate-spin" />正在加载库存台账…</td></tr>}
              {!inventoryStocksLoading && inventoryStocksError && <tr><td colSpan={7} className="py-12 text-center text-sm text-red-600"><p>{inventoryStocksError}</p><button type="button" onClick={() => void loadInventoryStocks()} className="mt-3 text-blue-600 hover:underline">重新加载</button></td></tr>}
              {!inventoryStocksLoading && !inventoryStocksError && filtered.length === 0 && <tr><td colSpan={7} className="py-12 text-center text-sm text-gray-400">暂无库存台账记录，请先维护 SKU 库存。</td></tr>}
              {!inventoryStocksLoading && !inventoryStocksError && filtered.map((item) => (
                <tr key={item.id} className="hover:bg-[#F8FAFC]">
                  <td className="py-3 px-4 font-mono font-bold text-gray-900 text-xs">
                    {item.orderNo}
                  </td>

                  <td className="py-3 px-4">
                    {item.type === 'inbound_purchase' && (
                      <span className="inline-flex items-center gap-1 text-xs font-semibold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded">
                        <ArrowDownToLine className="w-3 h-3" /> 采购入库
                      </span>
                    )}
                    {item.type === 'outbound_sale' && (
                      <span className="inline-flex items-center gap-1 text-xs font-semibold text-blue-700 bg-blue-50 px-2 py-0.5 rounded">
                        <ArrowUpFromLine className="w-3 h-3" /> 订单出库
                      </span>
                    )}
                    {item.type === 'transfer' && (
                      <span className="inline-flex items-center gap-1 text-xs font-semibold text-purple-700 bg-purple-50 px-2 py-0.5 rounded">
                        <Repeat className="w-3 h-3" /> 跨仓调拨
                      </span>
                    )}
                    {item.type === 'loss_audit' && (
                      <span className="inline-flex items-center gap-1 text-xs font-semibold text-amber-700 bg-amber-50 px-2 py-0.5 rounded">
                        <AlertTriangle className="w-3 h-3" /> 盘点报损
                      </span>
                    )}
                    {item.type === 'stock' && (
                      <span className="inline-flex items-center gap-1 text-xs font-semibold text-cyan-700 bg-cyan-50 px-2 py-0.5 rounded">
                        <Boxes className="w-3 h-3" /> 库存台账
                      </span>
                    )}
                  </td>

                  <td className="py-3 px-4">
                    <div className="text-xs font-semibold text-gray-800">{item.warehouseName}</div>
                    {item.targetWarehouse && (
                      <div className="text-[11px] text-purple-600">➔ {item.targetWarehouse}</div>
                    )}
                  </td>

                  <td className="py-3 px-4">
                    <div className="text-xs font-bold text-gray-800">{item.productName}</div>
                    <div className="font-mono text-[11px] text-gray-400">{item.sku}</div>
                  </td>

                  <td className="py-3 px-4 text-right font-mono font-bold text-sm">
                    {item.quantity > 0 ? `+${item.quantity}` : item.quantity} 件
                  </td>

                  <td className="py-3 px-4 text-center">
                    {item.status === 'completed' && (
                      <span className="text-xs font-semibold text-emerald-800 bg-emerald-100 px-2 py-0.5 rounded-full inline-flex items-center gap-1">
                        <CheckCircle2 className="w-3 h-3" /> 已入库
                      </span>
                    )}
                    {item.status === 'processing' && (
                      <span className="text-xs font-semibold text-blue-800 bg-blue-100 px-2 py-0.5 rounded-full inline-flex items-center gap-1">
                        <Clock className="w-3 h-3" /> 运输中
                      </span>
                    )}
                  </td>

                  <td className="py-3 px-4 text-right text-xs font-mono text-gray-500">
                    {item.type === 'stock' ? (
                      <div className="inline-flex items-center gap-1">
                        <button
                          type="button"
                          disabled={stockActionId === item.stockId}
                          onClick={() => {
                            const stock = inventoryStocks.find((entry) => entry.id === item.stockId);
                            if (stock) void adjustStock(stock, -1);
                          }}
                          className="w-7 h-7 rounded border border-red-200 text-red-600 hover:bg-red-50 disabled:opacity-50"
                          aria-label="减少库存"
                        >−</button>
                        <button
                          type="button"
                          disabled={stockActionId === item.stockId}
                          onClick={() => {
                            const stock = inventoryStocks.find((entry) => entry.id === item.stockId);
                            if (stock) void adjustStock(stock, 1);
                          }}
                          className="w-7 h-7 rounded border border-emerald-200 text-emerald-600 hover:bg-emerald-50 disabled:opacity-50"
                          aria-label="增加库存"
                        >+</button>
                      </div>
                    ) : (
                      <><div>{item.operator}</div><div className="text-gray-400">{item.createdAt}</div></>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {editingWarehouse && (
        <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-label="仓库编辑">
          <form onSubmit={submitWarehouse} className="bg-white rounded-xl shadow-xl w-full max-w-lg p-6 space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-bold text-gray-900">{editingWarehouse.id ? '编辑仓库' : '新增仓库'}</h3>
              <button type="button" onClick={() => setEditingWarehouse(null)} className="p-2 text-gray-400 hover:text-gray-700 rounded" aria-label="关闭"><X className="w-5 h-5" /></button>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
              <label className="text-xs text-gray-600">仓库编码
                <input required maxLength={64} value={editingWarehouse.warehouseCode} onChange={(event) => updateWarehouseForm('warehouseCode', event.target.value)} className="mt-1 w-full h-10 border border-[#E2E8F0] rounded-lg px-3 text-sm uppercase" placeholder="例如：SH-DEFAULT" />
              </label>
              <label className="text-xs text-gray-600">仓库名称
                <input required maxLength={128} value={editingWarehouse.warehouseName} onChange={(event) => updateWarehouseForm('warehouseName', event.target.value)} className="mt-1 w-full h-10 border border-[#E2E8F0] rounded-lg px-3 text-sm" placeholder="例如：华东中心仓" />
              </label>
              <label className="text-xs text-gray-600">状态
                <select value={editingWarehouse.status} onChange={(event) => updateWarehouseForm('status', Number(event.target.value))} className="mt-1 w-full h-10 border border-[#E2E8F0] rounded-lg px-3 text-sm">
                  <option value={1}>启用</option><option value={0}>停用</option>
                </select>
              </label>
              <label className="text-xs text-gray-600">默认仓库
                <select value={editingWarehouse.isDefault} onChange={(event) => updateWarehouseForm('isDefault', Number(event.target.value))} className="mt-1 w-full h-10 border border-[#E2E8F0] rounded-lg px-3 text-sm">
                  <option value={0}>否</option><option value={1}>是</option>
                </select>
              </label>
              <label className="text-xs text-gray-600 md:col-span-2">备注
                <textarea maxLength={500} rows={3} value={editingWarehouse.remark} onChange={(event) => updateWarehouseForm('remark', event.target.value)} className="mt-1 w-full border border-[#E2E8F0] rounded-lg px-3 py-2 text-sm" placeholder="选填，记录仓库用途或地址" />
              </label>
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <button type="button" onClick={() => setEditingWarehouse(null)} className="px-4 py-2 rounded-lg border border-[#E2E8F0] text-sm">取消</button>
              <button disabled={warehouseSaving} type="submit" className="px-4 py-2 rounded-lg bg-blue-600 text-white text-sm flex items-center gap-2 disabled:opacity-60"><Save className="w-4 h-4" />{warehouseSaving ? '保存中…' : '保存'}</button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
