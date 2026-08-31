import React, { useEffect, useState } from 'react';
import { useAdmin } from '../../context/AdminContext';
import { listInventorySuppliers } from '../../api/adminApi';
import { 
  Truck, 
  Search, 
  Plus, 
  Phone, 
  Mail, 
  MapPin, 
  CheckCircle, 
  Clock, 
  Edit, 
  Trash2 
} from 'lucide-react';

export interface SupplierItem {
  id: string;
  name: string;
  code: string;
  category: string;
  contactPerson: string;
  phone: string;
  email: string;
  address: string;
  settlementCycle: 'T+30' | 'T+15' | 'prepay';
  status: 'active' | 'reviewing' | 'disabled';
  purchaseOrderCount: number;
}

const mockSuppliers: SupplierItem[] = [
  {
    id: 'sup-001',
    name: '深圳市深蓝声学智能电子有限公司',
    code: 'SUP-SZ-001',
    category: '电子数码',
    contactPerson: '陈总监',
    phone: '13800138000',
    email: 'contact@deepblueaudio.com',
    address: '广东省深圳市南山区高新科技园北区 5 栋',
    settlementCycle: 'T+30',
    status: 'active',
    purchaseOrderCount: 28
  },
  {
    id: 'sup-002',
    name: '东莞市倍思极速充电科技有限公司',
    code: 'SUP-DG-002',
    category: '数码配件',
    contactPerson: '黄经理',
    phone: '13911223344',
    email: 'sales@basecharge.cn',
    address: '广东省东莞市塘厦镇电子智能产业基地',
    settlementCycle: 'T+15',
    status: 'active',
    purchaseOrderCount: 16
  },
  {
    id: 'sup-003',
    name: '杭州西湖丝绸与家纺制品制造厂',
    code: 'SUP-HZ-003',
    category: '居家生活',
    contactPerson: '孙厂长',
    phone: '13788990011',
    email: 'hz_textile@163.com',
    address: '浙江省杭州市萧山区瓜沥镇工业园',
    settlementCycle: 'T+30',
    status: 'active',
    purchaseOrderCount: 12
  }
];

export const SupplierManagementView: React.FC = () => {
  const { showToast } = useAdmin();
  const [suppliers, setSuppliers] = useState<SupplierItem[]>(mockSuppliers);
  const [searchTerm, setSearchTerm] = useState('');

  useEffect(() => {
    let active = true;
    listInventorySuppliers({ size: 200 })
      .then((page) => {
        if (!active || !page.records.length) return;
        setSuppliers(page.records.map((supplier) => ({
          id: String(supplier.id),
          name: supplier.supplierName,
          code: supplier.supplierCode,
          category: '未分类',
          contactPerson: supplier.contactName || '未填写',
          phone: supplier.contactPhone || '未填写',
          email: '',
          address: supplier.address || '未填写',
          settlementCycle: 'T+30',
          status: supplier.status === 1 ? 'active' : 'disabled',
          purchaseOrderCount: 0,
        })));
      })
      .catch((error) => {
        if (active) showToast(error instanceof Error ? error.message : '供应商数据加载失败，当前显示演示数据', 'error');
      });
    return () => {
      active = false;
    };
  }, [showToast]);

  const filtered = suppliers.filter(
    (s) =>
      s.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      s.contactPerson.toLowerCase().includes(searchTerm.toLowerCase()) ||
      s.category.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <div className="space-y-6 animate-in fade-in-50 duration-200">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-xl md:text-2xl font-bold text-[#191C1E] tracking-tight">
              供应商档案与采购管理 (Suppliers & Sourcing)
            </h2>
            <span className="text-xs bg-slate-100 text-slate-700 font-semibold px-2 py-0.5 rounded-full border border-slate-200">
              供应链 SRM
            </span>
          </div>
          <p className="text-xs md:text-sm text-[#434655] mt-0.5">
            供应商准入资质、供货品类、账期结算约定及采购订单跟踪。
          </p>
        </div>

        <button
          onClick={() => showToast('已打开供应商入驻与建档向导', 'info')}
          className="h-[36px] px-4 rounded-lg bg-[#2563EB] text-white hover:bg-blue-700 flex items-center justify-center gap-2 text-xs font-semibold shadow-xs"
        >
          <Plus className="w-4 h-4" />
          <span>新建供应商档案</span>
        </button>
      </div>

      {/* Grid Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
        {filtered.map((sup) => (
          <div
            key={sup.id}
            className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs p-5 hover:border-blue-300 transition-all flex flex-col justify-between"
          >
            <div>
              <div className="flex items-start justify-between gap-2 mb-2">
                <span className="text-xs font-semibold text-blue-600 bg-blue-50 px-2 py-0.5 rounded">
                  {sup.category}
                </span>
                <span className="text-xs font-semibold text-emerald-800 bg-emerald-100 px-2 py-0.5 rounded-full">
                  合格供方
                </span>
              </div>

              <h3 className="font-bold text-gray-900 text-sm mb-1">{sup.name}</h3>
              <div className="text-xs font-mono text-gray-400 mb-3">{sup.code}</div>

              <div className="space-y-1.5 text-xs text-gray-600">
                <div className="flex items-center gap-1.5">
                  <Phone className="w-3.5 h-3.5 text-gray-400" />
                  <span>{sup.contactPerson} ({sup.phone})</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <Mail className="w-3.5 h-3.5 text-gray-400" />
                  <span>{sup.email}</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <MapPin className="w-3.5 h-3.5 text-gray-400 shrink-0" />
                  <span className="truncate">{sup.address}</span>
                </div>
              </div>
            </div>

            <div className="mt-4 pt-3 border-t border-gray-100 flex items-center justify-between text-xs">
              <span className="text-gray-500">结算约定: <strong className="text-gray-800">{sup.settlementCycle}</strong></span>
              <button
                onClick={() => showToast(`已加载【${sup.name}】采购订单列表与未结应付款`, 'info')}
                className="text-blue-600 hover:text-blue-700 font-semibold"
              >
                采购订货 (共{sup.purchaseOrderCount}单)
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
