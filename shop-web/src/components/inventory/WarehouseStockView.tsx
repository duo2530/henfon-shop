import React, { useState } from 'react';
import { useAdmin } from '../../context/AdminContext';
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
  Warehouse 
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

export const WarehouseStockView: React.FC = () => {
  const { showToast } = useAdmin();
  const [stockOrders, setStockOrders] = useState<StockOrder[]>(mockStockOrders);
  const [searchTerm, setSearchTerm] = useState('');
  const [typeFilter, setTypeFilter] = useState('all');

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

      {/* Warehouse Summary Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-white p-4.5 rounded-xl border border-[#E2E8F0] shadow-xs">
          <div className="flex items-center gap-2 text-xs font-semibold text-gray-500 mb-1">
            <Warehouse className="w-4 h-4 text-blue-600" /> 华东一号总仓 (上海)
          </div>
          <div className="text-2xl font-bold text-gray-900 mt-1">45,820 件</div>
          <p className="text-xs text-gray-400 mt-1">库位利用率 78% · 正常</p>
        </div>

        <div className="bg-white p-4.5 rounded-xl border border-[#E2E8F0] shadow-xs">
          <div className="flex items-center gap-2 text-xs font-semibold text-gray-500 mb-1">
            <Warehouse className="w-4 h-4 text-purple-600" /> 华南二号总仓 (广州)
          </div>
          <div className="text-2xl font-bold text-gray-900 mt-1">28,400 件</div>
          <p className="text-xs text-gray-400 mt-1">库位利用率 62% · 正常</p>
        </div>

        <div className="bg-white p-4.5 rounded-xl border border-[#E2E8F0] shadow-xs">
          <div className="flex items-center gap-2 text-xs font-semibold text-gray-500 mb-1">
            <Warehouse className="w-4 h-4 text-emerald-600" /> 静安自提微仓 (前置)
          </div>
          <div className="text-2xl font-bold text-gray-900 mt-1">3,120 件</div>
          <p className="text-xs text-emerald-600 mt-1">支持同城 30 分钟极速达</p>
        </div>
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
    </div>
  );
};
