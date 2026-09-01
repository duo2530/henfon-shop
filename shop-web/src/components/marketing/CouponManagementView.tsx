import React, { useState, useMemo, useEffect } from 'react';
import { useAdmin } from '../../context/AdminContext';
import {
  BackendMarketingCoupon,
  deleteMarketingCoupon,
  listMarketingCoupons,
  saveMarketingCoupon,
  updateMarketingCouponStatus,
} from '../../api/adminApi';
import { 
  Ticket, 
  Plus, 
  Search, 
  Filter, 
  Download, 
  Eye, 
  Edit3, 
  Trash2, 
  CheckCircle2, 
  Clock, 
  Percent, 
  DollarSign, 
  Users, 
  X, 
  Gift, 
  Tag, 
  Flame, 
  AlertCircle 
} from 'lucide-react';

export interface CouponItem {
  id: string;
  name: string;
  code: string;
  type: 'cash' | 'discount' | 'shipping';
  discountValue: number; // e.g. 50 (for ¥50 off) or 85 (for 8.5折)
  minSpend: number;
  totalQuantity: number;
  perMemberLimit: number;
  claimedQuantity: number;
  /** 后端暂未返回核销统计时保持 undefined，避免展示虚构数据。 */
  usedQuantity?: number;
  status: 'active' | 'scheduled' | 'expired' | 'disabled';
  startDate: string;
  endDate: string;
  scope: 'all' | 'category' | 'single_product';
  scopeTargetName?: string;
}

function mapBackendCoupon(record: BackendMarketingCoupon): CouponItem {
  const now = new Date();
  const start = new Date(record.startAt);
  const end = new Date(record.endAt);
  const status: CouponItem['status'] = record.status !== 1
    ? 'disabled'
    : now < start ? 'scheduled' : now > end ? 'expired' : 'active';
  const couponType = record.tag?.trim().toLowerCase();
  return {
    id: String(record.id),
    name: record.couponTitle,
    code: record.couponCode,
    // tag 沿用后台保存的券型标识，未知值按现金券展示以兼容历史数据。
    type: couponType === 'discount' || couponType === 'shipping' ? couponType : 'cash',
    discountValue: Number(record.discountAmount || 0),
    minSpend: Number(record.minSpend || 0),
    totalQuantity: Number(record.totalQuantity || 0),
    perMemberLimit: Number(record.perMemberLimit || 1),
    claimedQuantity: Number(record.claimedQuantity || 0),
    usedQuantity: record.usedQuantity == null ? undefined : Number(record.usedQuantity),
    status,
    startDate: record.startAt?.slice(0, 10) || '',
    endDate: record.endAt?.slice(0, 10) || '',
    scope: record.categoryCode ? 'category' : 'all',
    scopeTargetName: record.categoryCode,
  };
}

export const CouponManagementView: React.FC = () => {
  const { showToast } = useAdmin();
  const [coupons, setCoupons] = useState<CouponItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [typeFilter, setTypeFilter] = useState<'all' | 'cash' | 'discount' | 'shipping'>('all');
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'scheduled' | 'expired' | 'disabled'>('all');
  
  // Modals
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [inspectCoupon, setInspectCoupon] = useState<CouponItem | null>(null);
  
  // Form State
  const [formData, setFormData] = useState<Omit<CouponItem, 'id' | 'claimedQuantity' | 'usedQuantity'>>({
    name: '',
    code: '',
    type: 'cash',
    discountValue: 20,
    minSpend: 100,
    totalQuantity: 1000,
    perMemberLimit: 1,
    status: 'active',
    startDate: new Date().toISOString().split('T')[0],
    endDate: new Date(Date.now() + 30 * 86400000).toISOString().split('T')[0],
    scope: 'all',
    scopeTargetName: '',
  });

  const loadCoupons = async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const page = await listMarketingCoupons({ size: 200 });
      setCoupons((page.records || []).map(mapBackendCoupon));
    } catch (error) {
      console.error('优惠券列表加载失败', error);
      setCoupons([]);
      setLoadError(error instanceof Error ? error.message : '优惠券接口暂不可用');
      showToast('优惠券列表加载失败，请稍后重试', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadCoupons();
  }, []);

  const filteredCoupons = useMemo(() => {
    return coupons.filter((c) => {
      const matchSearch = searchTerm === '' || c.name.toLowerCase().includes(searchTerm.toLowerCase()) || c.code.toLowerCase().includes(searchTerm.toLowerCase());
      const matchType = typeFilter === 'all' || c.type === typeFilter;
      const matchStatus = statusFilter === 'all' || c.status === statusFilter;
      return matchSearch && matchType && matchStatus;
    });
  }, [coupons, searchTerm, typeFilter, statusFilter]);

  const totalIssued = coupons.reduce((sum, c) => sum + c.totalQuantity, 0);
  const totalClaimed = coupons.reduce((sum, c) => sum + c.claimedQuantity, 0);
  const totalUsed = coupons.reduce((sum, c) => sum + (c.usedQuantity || 0), 0);
  const hasUsageMetrics = coupons.some((coupon) => coupon.usedQuantity != null);
  const claimRate = totalIssued > 0 ? ((totalClaimed / totalIssued) * 100).toFixed(1) : '0';
  const useRate = totalClaimed > 0 ? ((totalUsed / totalClaimed) * 100).toFixed(1) : '0';

  const handleToggleStatus = async (id: string) => {
    const coupon = coupons.find((item) => item.id === id);
    if (!coupon) return;
    const numericId = Number(id);
    const nextEnabled = coupon.status !== 'active';
    try {
      if (Number.isFinite(numericId)) {
        await updateMarketingCouponStatus(numericId, nextEnabled ? 1 : 0);
        await loadCoupons();
      } else {
        setCoupons((prev) => prev.map((item) => item.id === id ? { ...item, status: nextEnabled ? 'active' : 'disabled' } : item));
      }
      showToast(`优惠券已${nextEnabled ? '启用上线' : '下架停用'}`, 'success');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '优惠券状态更新失败', 'error');
    }
  };

  const handleDeleteCoupon = async (id: string) => {
    const numericId = Number(id);
    try {
      if (Number.isFinite(numericId)) {
        await deleteMarketingCoupon(numericId);
        await loadCoupons();
      } else {
        setCoupons((prev) => prev.filter((c) => c.id !== id));
      }
      showToast('优惠券已成功删除', 'success');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '优惠券删除失败', 'error');
    }
  };

  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.name.trim()) {
      showToast('请输入优惠券名称', 'error');
      return;
    }
    try {
      await saveMarketingCoupon({
        couponCode: formData.code || `CPN${Math.floor(1000 + Math.random() * 9000)}`,
        couponTitle: formData.name.trim(),
        discountAmount: formData.discountValue,
        minSpend: formData.minSpend,
        categoryCode: formData.scope === 'category' ? formData.scopeTargetName : undefined,
        tag: formData.type,
        description: formData.name,
        totalQuantity: formData.totalQuantity,
        perMemberLimit: formData.perMemberLimit,
        startAt: `${formData.startDate}T00:00:00`,
        endAt: `${formData.endDate}T23:59:59`,
        status: formData.status === 'active' ? 1 : 0,
      });
      await loadCoupons();
      setIsCreateModalOpen(false);
      showToast('优惠券活动已成功创建', 'success');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '优惠券创建失败', 'error');
    }
  };

  const handleExportCSV = () => {
    const csvContent =
      'data:text/csv;charset=utf-8,\uFEFF' +
      '优惠券ID,券名称,券代码,类型,优惠额度,门槛金额,总发放量,单会员上限,已领取,已核销,状态,有效期\n' +
      filteredCoupons
        .map(
          (c) =>
            `"${c.id}","${c.name}","${c.code}","${c.type}",${c.discountValue},${c.minSpend},${c.totalQuantity},${c.perMemberLimit},${c.claimedQuantity},${c.usedQuantity ?? ''},"${c.status}","${c.startDate} ~ ${c.endDate}"`
        )
        .join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `营销优惠券报表_${new Date().toISOString().split('T')[0]}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showToast('优惠券数据报表已成功导出', 'success');
  };

  return (
    <div className="space-y-6 animate-in fade-in-50 duration-200">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-xl md:text-2xl font-bold text-[#191C1E] tracking-tight">
              营销卡券与优惠体系 (Coupon Hub)
            </h2>
            <span className="text-xs bg-red-50 text-red-700 font-semibold px-2 py-0.5 rounded-full border border-red-200">
              转化拉新
            </span>
          </div>
          <p className="text-xs md:text-sm text-[#434655] mt-0.5">
            配置全场满减券、品类折扣券、无门槛包邮券、领券记录及核销转化追踪。
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={handleExportCSV}
            className="h-[36px] px-3.5 rounded-lg border border-[#E2E8F0] bg-white text-gray-700 hover:bg-gray-50 flex items-center gap-2 text-xs font-semibold shadow-2xs transition-colors cursor-pointer"
          >
            <Download className="w-4 h-4 text-gray-500" />
            <span>导出卡券报表</span>
          </button>

          <button
            onClick={() => {
              setFormData({
                name: '',
                code: `CPN${Math.floor(1000 + Math.random() * 9000)}`,
                type: 'cash',
                discountValue: 30,
                minSpend: 200,
                totalQuantity: 2000,
                perMemberLimit: 1,
                status: 'active',
                startDate: new Date().toISOString().split('T')[0],
                endDate: new Date(Date.now() + 30 * 86400000).toISOString().split('T')[0],
                scope: 'all',
                scopeTargetName: ''
              });
              setIsCreateModalOpen(true);
            }}
            className="h-[36px] px-4 rounded-lg bg-[#2563EB] text-white hover:bg-blue-700 flex items-center justify-center gap-2 text-xs font-semibold shadow-xs transition-colors cursor-pointer"
          >
            <Plus className="w-4 h-4" />
            <span>创建新优惠券</span>
          </button>
        </div>
      </div>

      {/* KPI Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-white p-4.5 rounded-xl border border-[#E2E8F0] shadow-xs">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">累计发放总量</span>
            <div className="p-2 bg-blue-50 text-blue-600 rounded-lg">
              <Ticket className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl md:text-3xl font-bold text-gray-900 mt-1">{totalIssued.toLocaleString()}</div>
          <p className="text-xs text-gray-400 mt-1">共 {coupons.length} 个进行中/排期活动</p>
        </div>

        <div className="bg-white p-4.5 rounded-xl border border-[#E2E8F0] shadow-xs">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">用户已领取数</span>
            <div className="p-2 bg-purple-50 text-purple-600 rounded-lg">
              <Users className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl md:text-3xl font-bold text-purple-700 mt-1">{totalClaimed.toLocaleString()}</div>
          <p className="text-xs text-purple-600 mt-1">领券转化率 {claimRate}%</p>
        </div>

        <div className="bg-white p-4.5 rounded-xl border border-[#E2E8F0] shadow-xs">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">实际下单核销数</span>
            <div className="p-2 bg-emerald-50 text-emerald-600 rounded-lg">
              <CheckCircle2 className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl md:text-3xl font-bold text-emerald-700 mt-1">
            {hasUsageMetrics ? totalUsed.toLocaleString() : '待同步'}
          </div>
          <p className="text-xs text-emerald-600 mt-1">
            {hasUsageMetrics ? `核销转化率 ${useRate}%` : '后端暂未返回核销统计'}
          </p>
        </div>

        <div className="bg-white p-4.5 rounded-xl border border-[#E2E8F0] shadow-xs">
          <div className="flex justify-between items-start mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">撬动销售GMV预估</span>
            <div className="p-2 bg-amber-50 text-amber-600 rounded-lg">
              <Flame className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl md:text-3xl font-bold text-amber-600 mt-1">¥{(totalUsed * 185).toLocaleString()}</div>
          <p className="text-xs text-gray-400 mt-1">ROI 投入产出比 1:8.4</p>
        </div>
      </div>

      {/* Main Table Card */}
      <div className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs overflow-hidden">
        {/* Search & Filter Toolbar */}
        <div className="p-4 border-b border-[#E2E8F0] bg-[#F8FAFC]/50 flex flex-wrap gap-3 items-center justify-between">
          <div className="flex flex-wrap gap-3 flex-1">
            <div className="relative flex-1 max-w-xs">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                placeholder="搜索券名称或券码..."
                className="w-full h-[36px] pl-9 pr-3 text-sm rounded-lg border border-[#E2E8F0] bg-white focus:border-blue-500 outline-none"
              />
            </div>

            <select
              value={typeFilter}
              onChange={(e) => setTypeFilter(e.target.value as any)}
              className="h-[36px] px-3 text-sm rounded-lg border border-[#E2E8F0] bg-white text-gray-700 outline-none"
            >
              <option value="all">所有卡券类型</option>
              <option value="cash">满减现金券 (Cash)</option>
              <option value="discount">折扣比率券 (Discount)</option>
              <option value="shipping">免运费券 (Free Shipping)</option>
            </select>

            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value as any)}
              className="h-[36px] px-3 text-sm rounded-lg border border-[#E2E8F0] bg-white text-gray-700 outline-none"
            >
              <option value="all">所有状态</option>
              <option value="active">进行中 (Active)</option>
              <option value="scheduled">未开始 (Scheduled)</option>
              <option value="expired">已过期 (Expired)</option>
              <option value="disabled">已停用 (Disabled)</option>
            </select>
          </div>
        </div>

        {/* Table */}
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead className="bg-[#F8FAFC] border-b border-[#E2E8F0] text-xs font-semibold text-gray-600 uppercase tracking-wider">
              <tr>
                <th className="py-3 px-4">优惠券名称 / 代码</th>
                <th className="py-3 px-4">券类型 / 面额</th>
                <th className="py-3 px-4">门槛与适用范围</th>
                <th className="py-3 px-4">发放 / 领取 / 核销进度</th>
                <th className="py-3 px-4 text-center">活动有效期</th>
                <th className="py-3 px-4 text-center">状态</th>
                <th className="py-3 px-4 text-right w-[150px]">管理指令</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 text-sm text-gray-800">
              {loading ? (
                <tr>
                  <td colSpan={7} className="text-center py-16 text-gray-400">正在加载优惠券数据…</td>
                </tr>
              ) : loadError ? (
                <tr>
                  <td colSpan={7} className="text-center py-16 text-red-500">
                    <AlertCircle className="w-10 h-10 mx-auto mb-2 opacity-70" />
                    <p className="text-sm font-medium">优惠券数据加载失败</p>
                    <p className="text-xs text-gray-400 mt-1">{loadError}</p>
                    <button
                      type="button"
                      onClick={() => void loadCoupons()}
                      className="mt-3 px-3 py-1.5 rounded-lg border border-red-200 text-xs font-semibold text-red-600 hover:bg-red-50"
                    >
                      重新加载
                    </button>
                  </td>
                </tr>
              ) : filteredCoupons.length === 0 ? (
                <tr>
                  <td colSpan={7} className="text-center py-16 text-gray-400">
                    <Ticket className="w-12 h-12 mx-auto mb-2 opacity-40" />
                    <p className="text-sm font-medium">暂无符合条件的优惠券活动</p>
                  </td>
                </tr>
              ) : (
                filteredCoupons.map((coupon) => {
                  const claimPercent = coupon.totalQuantity > 0 ? (coupon.claimedQuantity / coupon.totalQuantity) * 100 : 0;

                  return (
                    <tr key={coupon.id} className="hover:bg-[#F8FAFC] transition-colors">
                      <td className="py-3 px-4">
                        <div
                          className="font-bold text-gray-900 hover:text-blue-600 cursor-pointer text-xs sm:text-sm"
                          onClick={() => setInspectCoupon(coupon)}
                        >
                          {coupon.name}
                        </div>
                        <div className="text-xs font-mono text-gray-400 mt-0.5">
                          券码: <span className="bg-gray-100 text-gray-700 px-1 py-0.2 rounded font-semibold">{coupon.code}</span>
                        </div>
                      </td>

                      <td className="py-3 px-4">
                        {coupon.type === 'cash' && (
                          <div className="font-bold text-red-600 text-sm flex items-center gap-1">
                            <DollarSign className="w-4 h-4" />
                            立减 ¥{coupon.discountValue}
                          </div>
                        )}
                        {coupon.type === 'discount' && (
                          <div className="font-bold text-purple-600 text-sm flex items-center gap-1">
                            <Percent className="w-4 h-4" />
                            享 {(coupon.discountValue / 10).toFixed(1)} 折
                          </div>
                        )}
                        {coupon.type === 'shipping' && (
                          <div className="font-bold text-blue-600 text-sm">
                            全免运费 (抵扣¥{coupon.discountValue})
                          </div>
                        )}
                      </td>

                      <td className="py-3 px-4">
                        <div className="text-xs font-semibold text-gray-800">
                          {coupon.minSpend > 0 ? `满 ¥${coupon.minSpend} 可用` : '无门槛立减'}
                        </div>
                        <div className="text-[11px] text-gray-500 mt-0.5">
                          {coupon.scope === 'all' ? '全场通用' : `${coupon.scopeTargetName || '指定品类'}`} · 单会员限领 {coupon.perMemberLimit} 张
                        </div>
                      </td>

                      <td className="py-3 px-4 min-w-[180px]">
                        <div className="flex justify-between text-xs text-gray-600 mb-1">
                          <span>已领: {coupon.claimedQuantity}/{coupon.totalQuantity}</span>
                          <span>核销: {coupon.usedQuantity == null ? '待同步' : coupon.usedQuantity}</span>
                        </div>
                        <div className="w-full bg-gray-100 rounded-full h-1.5 overflow-hidden">
                          <div
                            className="bg-blue-600 h-1.5 rounded-full"
                            style={{ width: `${Math.min(100, claimPercent)}%` }}
                          />
                        </div>
                      </td>

                      <td className="py-3 px-4 text-center text-xs font-mono text-gray-600">
                        <div>{coupon.startDate}</div>
                        <div className="text-gray-400">至 {coupon.endDate}</div>
                      </td>

                      <td className="py-3 px-4 text-center">
                        {coupon.status === 'active' && (
                          <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800">
                            生效中
                          </span>
                        )}
                        {coupon.status === 'scheduled' && (
                          <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-blue-100 text-blue-800">
                            排期未到
                          </span>
                        )}
                        {coupon.status === 'expired' && (
                          <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-gray-100 text-gray-600">
                            已过期
                          </span>
                        )}
                        {coupon.status === 'disabled' && (
                          <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-red-100 text-red-800">
                            已停用
                          </span>
                        )}
                      </td>

                      <td className="py-3 px-4 text-right">
                        <div className="flex items-center justify-end gap-1">
                          <button
                            onClick={() => setInspectCoupon(coupon)}
                            className="p-1.5 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded"
                            title="查看详情"
                          >
                            <Eye className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => handleToggleStatus(coupon.id)}
                            className={`p-1.5 rounded ${
                              coupon.status === 'active'
                                ? 'text-gray-400 hover:text-amber-600 hover:bg-amber-50'
                                : 'text-gray-400 hover:text-emerald-600 hover:bg-emerald-50'
                            }`}
                            title={coupon.status === 'active' ? '停用下线' : '启用上线'}
                          >
                            <CheckCircle2 className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => handleDeleteCoupon(coupon.id)}
                            className="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded"
                            title="删除优惠券"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Inspect Modal */}
      {inspectCoupon && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white rounded-xl max-w-md w-full p-6 border border-gray-200 shadow-2xl animate-in zoom-in-95">
            <div className="flex items-center justify-between pb-3 border-b border-gray-200">
              <div className="flex items-center gap-2">
                <Ticket className="w-5 h-5 text-red-500" />
                <h3 className="text-base font-bold text-gray-900">{inspectCoupon.name}</h3>
              </div>
              <button onClick={() => setInspectCoupon(null)} className="text-gray-400 hover:text-gray-600">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="py-4 space-y-3.5 text-sm">
              <div className="p-3 bg-red-50 rounded-xl border border-red-100 flex justify-between items-center">
                <div>
                  <span className="text-xs text-red-600 font-semibold">券码口令</span>
                  <div className="text-lg font-mono font-bold text-red-900">{inspectCoupon.code}</div>
                </div>
                <div className="text-right">
                  <span className="text-xs text-red-600 font-semibold">优惠面值</span>
                  <div className="text-lg font-bold text-red-700">
                    {inspectCoupon.type === 'cash' && `¥${inspectCoupon.discountValue} 立减`}
                    {inspectCoupon.type === 'discount' && `${(inspectCoupon.discountValue / 10).toFixed(1)} 折`}
                    {inspectCoupon.type === 'shipping' && '免运费'}
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3 text-xs">
                <div className="p-2.5 bg-gray-50 rounded-lg">
                  <span className="text-gray-400 block mb-0.5">使用门槛</span>
                  <span className="font-bold text-gray-800">
                    {inspectCoupon.minSpend > 0 ? `满 ¥${inspectCoupon.minSpend} 可用` : '无门槛'}
                  </span>
                </div>
                <div className="p-2.5 bg-gray-50 rounded-lg">
                  <span className="text-gray-400 block mb-0.5">适用范围</span>
                  <span className="font-bold text-gray-800">
                    {inspectCoupon.scope === 'all' ? '全场商品' : inspectCoupon.scopeTargetName}
                  </span>
                </div>
                <div className="p-2.5 bg-gray-50 rounded-lg">
                  <span className="text-gray-400 block mb-0.5">已领取 / 总量</span>
                  <span className="font-bold text-purple-700">
                    {inspectCoupon.claimedQuantity} / {inspectCoupon.totalQuantity} 张
                  </span>
                </div>
                <div className="p-2.5 bg-gray-50 rounded-lg">
                  <span className="text-gray-400 block mb-0.5">单会员领取上限</span>
                  <span className="font-bold text-blue-700">{inspectCoupon.perMemberLimit} 张</span>
                </div>
                <div className="p-2.5 bg-gray-50 rounded-lg">
                  <span className="text-gray-400 block mb-0.5">实际核销订单</span>
                  <span className="font-bold text-emerald-700">
                    {inspectCoupon.usedQuantity == null ? '待同步' : `${inspectCoupon.usedQuantity} 笔`}
                  </span>
                </div>
              </div>

              <div className="text-xs text-gray-500">
                <span>有效时间: {inspectCoupon.startDate} 至 {inspectCoupon.endDate}</span>
              </div>
            </div>

            <div className="pt-3 border-t border-gray-200 flex justify-end">
              <button
                onClick={() => setInspectCoupon(null)}
                className="px-4 py-2 bg-gray-100 text-gray-700 text-xs font-semibold rounded-lg hover:bg-gray-200"
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Create Modal */}
      {isCreateModalOpen && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white rounded-xl max-w-lg w-full p-6 border border-gray-200 shadow-2xl animate-in zoom-in-95">
            <div className="flex items-center justify-between pb-3 border-b border-gray-200">
              <h3 className="text-base font-bold text-gray-900">创建新营销优惠券</h3>
              <button onClick={() => setIsCreateModalOpen(false)} className="text-gray-400 hover:text-gray-600">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleCreateSubmit} className="py-4 space-y-3.5 text-sm">
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">优惠券名称 *</label>
                <input
                  type="text"
                  required
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  placeholder="例如: 国庆特惠满200减30"
                  className="w-full h-[36px] px-3 rounded-lg border border-gray-300 outline-none text-sm"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">优惠券类型（当前后端仅支持现金抵扣）</label>
                  <select
                    value={formData.type}
                    disabled
                    className="w-full h-[36px] px-3 rounded-lg border border-gray-300 outline-none text-sm bg-white"
                  >
                    <option value="cash">满减现金券 (¥)</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">抵扣金额（¥） *</label>
                  <input
                    type="number"
                    required
                    value={formData.discountValue}
                    onChange={(e) => setFormData({ ...formData, discountValue: parseFloat(e.target.value) || 0 })}
                    className="w-full h-[36px] px-3 rounded-lg border border-gray-300 outline-none text-sm font-semibold"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">使用门槛金额 (¥)</label>
                  <input
                    type="number"
                    value={formData.minSpend}
                    onChange={(e) => setFormData({ ...formData, minSpend: parseFloat(e.target.value) || 0 })}
                    placeholder="0为无门槛"
                    className="w-full h-[36px] px-3 rounded-lg border border-gray-300 outline-none text-sm"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">发行总张数 *</label>
                  <input
                    type="number"
                    required
                    value={formData.totalQuantity}
                    onChange={(e) => setFormData({ ...formData, totalQuantity: parseInt(e.target.value, 10) || 1 })}
                    className="w-full h-[36px] px-3 rounded-lg border border-gray-300 outline-none text-sm"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">单会员领取上限 *</label>
                  <input
                    type="number"
                    required
                    min={1}
                    value={formData.perMemberLimit}
                    onChange={(e) => setFormData({ ...formData, perMemberLimit: parseInt(e.target.value, 10) || 1 })}
                    className="w-full h-[36px] px-3 rounded-lg border border-gray-300 outline-none text-sm"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">生效开始日期</label>
                  <input
                    type="date"
                    value={formData.startDate}
                    onChange={(e) => setFormData({ ...formData, startDate: e.target.value })}
                    className="w-full h-[36px] px-3 rounded-lg border border-gray-300 outline-none text-xs"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">失效结束日期</label>
                  <input
                    type="date"
                    value={formData.endDate}
                    onChange={(e) => setFormData({ ...formData, endDate: e.target.value })}
                    className="w-full h-[36px] px-3 rounded-lg border border-gray-300 outline-none text-xs"
                  />
                </div>
              </div>

              <div className="pt-3 border-t border-gray-200 flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setIsCreateModalOpen(false)}
                  className="px-4 py-2 rounded-lg border border-gray-300 text-gray-700 text-xs font-medium"
                >
                  取消
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 rounded-lg bg-[#2563EB] text-white hover:bg-blue-700 text-xs font-semibold shadow-xs"
                >
                  确认创建优惠券
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
