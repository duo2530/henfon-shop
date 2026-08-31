import React, { FormEvent, useCallback, useEffect, useState } from 'react';
import { useAdmin } from '../../context/AdminContext';
import {
  BackendInventoryWarehouse,
  deleteInventoryWarehouse,
  listInventoryWarehouses,
  saveInventoryWarehouse,
  updateInventoryWarehouseStatus
} from '../../api/adminApi';
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

export interface StockOrder {
  id: string;
  orderNo: string;
  type: 'inbound_purchase' | 'outbound_sale' | 'transfer' | 'loss_audit';
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

const mockStockOrders: StockOrder[] = [
  {
    id: 'stk-001',
    orderNo: 'IN20260829001',
    type: 'inbound_purchase',
    warehouseName: '华东一号中心仓 (上海)',
    productName: '极客降噪无线蓝牙耳机 Pro Max',
    sku: 'SKU-GEEK-001',
    quantity: 500,
    operator: '王仓管',
    status: 'completed',
    createdAt: '2026-08-29 09:30:00',
    remarks: '供应商深蓝数码批次到货验收入库'
  },
  {
    id: 'stk-002',
    orderNo: 'TR20260829002',
    type: 'transfer',
    warehouseName: '华东一号中心仓 (上海)',
    targetWarehouse: '静安前置自提微仓',
    productName: '智能磁吸无线充电底座',
    sku: 'SKU-CHG-002',
    quantity: 120,
    operator: '赵主管',
    status: 'processing',
    createdAt: '2026-08-29 11:20:00',
    remarks: '前置仓补货同城冷链调拨'
  },
  {
    id: 'stk-003',
    orderNo: 'OUT20260829003',
    type: 'outbound_sale',
    warehouseName: '华南二号中心仓 (广州)',
    productName: '太空慢回弹记忆棉护颈深睡枕',
    sku: 'SKU-PILLOW-003',
    quantity: 65,
    operator: '系统自动打单',
    status: 'completed',
    createdAt: '2026-08-29 13:00:15',
    remarks: '顺丰大促批量波次发货出库'
  },
  {
    id: 'stk-004',
    orderNo: 'AUD20260829004',
    type: 'loss_audit',
    warehouseName: '华东一号中心仓 (上海)',
    productName: '天然有机大马士革玫瑰纯露',
    sku: 'SKU-ROSE-004',
    quantity: -2,
    operator: '周盘点员',
    status: 'completed',
    createdAt: '2026-08-29 14:15:00',
    remarks: '月末例行抽检外包装微损报损'
  }
];

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

export const WarehouseStockView: React.FC = () => {
  const { showToast } = useAdmin();
  const [stockOrders, setStockOrders] = useState<StockOrder[]>(mockStockOrders);
  const [searchTerm, setSearchTerm] = useState('');
  const [typeFilter, setTypeFilter] = useState('all');
  const [warehouses, setWarehouses] = useState<BackendInventoryWarehouse[]>([]);
  const [warehouseLoading, setWarehouseLoading] = useState(true);
  const [warehouseError, setWarehouseError] = useState<string | null>(null);
  const [editingWarehouse, setEditingWarehouse] = useState<WarehouseForm | null>(null);
  const [warehouseSaving, setWarehouseSaving] = useState(false);
  const [warehouseActionId, setWarehouseActionId] = useState<number | null>(null);

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
    if (!window.confirm(`确认删除仓库「${warehouse.warehouseName}」吗？有库存台账的仓库无法删除。`)) return;
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

  const filtered = stockOrders.filter((o) => {
    const matchSearch =
      searchTerm === '' ||
      o.orderNo.toLowerCase().includes(searchTerm.toLowerCase()) ||
      o.productName.toLowerCase().includes(searchTerm.toLowerCase()) ||
      o.sku.toLowerCase().includes(searchTerm.toLowerCase());
    const matchType = typeFilter === 'all' || o.type === typeFilter;
    return matchSearch && matchType;
  });

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
              {filtered.map((item) => (
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
                    <div>{item.operator}</div>
                    <div className="text-gray-400">{item.createdAt}</div>
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
