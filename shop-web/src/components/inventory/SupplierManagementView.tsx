import React, { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import { useAdmin } from '../../context/AdminContext';
import {
  BackendInventorySupplier,
  deleteInventorySupplier,
  listInventorySuppliers,
  saveInventorySupplier,
  updateInventorySupplierStatus
} from '../../api/adminApi';
import {
  Edit3,
  Loader2,
  FileText,
  MapPin,
  Phone,
  Plus,
  RefreshCw,
  Save,
  Search,
  Trash2,
  Truck,
  X
} from 'lucide-react';

interface SupplierForm {
  id?: number;
  supplierCode: string;
  supplierName: string;
  contactName: string;
  contactPhone: string;
  address: string;
  status: number;
  remark: string;
  version?: number;
}

const emptySupplierForm = (): SupplierForm => ({
  supplierCode: '',
  supplierName: '',
  contactName: '',
  contactPhone: '',
  address: '',
  status: 1,
  remark: ''
});

const supplierStatusText = (status: number) => status === 1 ? '已启用' : '已停用';

export const SupplierManagementView: React.FC = () => {
  const { showToast } = useAdmin();
  const [suppliers, setSuppliers] = useState<BackendInventorySupplier[]>([]);
  const [supplierLoading, setSupplierLoading] = useState(true);
  const [supplierError, setSupplierError] = useState<string | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState<'all' | '1' | '0'>('all');
  const [editingSupplier, setEditingSupplier] = useState<SupplierForm | null>(null);
  const [supplierSaving, setSupplierSaving] = useState(false);
  const [supplierActionId, setSupplierActionId] = useState<number | null>(null);

  const loadSuppliers = useCallback(async () => {
    setSupplierLoading(true);
    setSupplierError(null);
    try {
      const page = await listInventorySuppliers({
        current: 1,
        size: 200
      });
      setSuppliers(page.records || []);
    } catch (error) {
      const message = error instanceof Error ? error.message : '供应商数据加载失败';
      // 接口失败时清空列表，避免把旧数据误当成最新供应商档案。
      setSuppliers([]);
      setSupplierError(message);
      showToast(`${message}，请稍后重试`, 'error');
    } finally {
      setSupplierLoading(false);
    }
  }, [showToast]);

  useEffect(() => {
    void loadSuppliers();
  }, [loadSuppliers]);

  const openSupplierForm = (supplier?: BackendInventorySupplier) => {
    setEditingSupplier(supplier ? {
      id: supplier.id,
      supplierCode: supplier.supplierCode,
      supplierName: supplier.supplierName,
      contactName: supplier.contactName || '',
      contactPhone: supplier.contactPhone || '',
      address: supplier.address || '',
      status: supplier.status,
      remark: supplier.remark || '',
      version: supplier.version
    } : emptySupplierForm());
  };

  const updateSupplierForm = <K extends keyof SupplierForm>(key: K, value: SupplierForm[K]) => {
    setEditingSupplier((current) => current ? { ...current, [key]: value } : current);
  };

  const submitSupplier = async (event: FormEvent) => {
    event.preventDefault();
    if (!editingSupplier) return;
    const payload = {
      ...editingSupplier,
      supplierCode: editingSupplier.supplierCode.trim().toUpperCase(),
      supplierName: editingSupplier.supplierName.trim(),
      contactName: editingSupplier.contactName.trim() || undefined,
      contactPhone: editingSupplier.contactPhone.trim() || undefined,
      address: editingSupplier.address.trim() || undefined,
      remark: editingSupplier.remark.trim() || undefined
    };
    if (!payload.supplierCode || !payload.supplierName) {
      showToast('请填写供应商编码和供应商名称', 'warning');
      return;
    }
    setSupplierSaving(true);
    try {
      await saveInventorySupplier(payload);
      setEditingSupplier(null);
      await loadSuppliers();
      showToast('供应商保存成功', 'success');
    } catch (error) {
      // 保存失败时保留表单内容，用户可以修正后重试，不覆盖本地编辑状态。
      showToast(error instanceof Error ? error.message : '供应商保存失败', 'error');
    } finally {
      setSupplierSaving(false);
    }
  };

  const toggleSupplierStatus = async (supplier: BackendInventorySupplier) => {
    const nextStatus = supplier.status === 1 ? 0 : 1;
    const previous = suppliers;
    setSupplierActionId(supplier.id);
    // 先更新界面提升响应速度，接口失败后使用快照回滚。
    setSuppliers((current) => current.map((item) => item.id === supplier.id ? { ...item, status: nextStatus } : item));
    try {
      await updateInventorySupplierStatus(supplier.id, nextStatus);
      showToast(nextStatus === 1 ? '供应商已启用' : '供应商已停用', 'success');
    } catch (error) {
      setSuppliers(previous);
      showToast(error instanceof Error ? error.message : '供应商状态更新失败', 'error');
    } finally {
      setSupplierActionId(null);
    }
  };

  const removeSupplier = async (supplier: BackendInventorySupplier) => {
    if (!window.confirm(`确认删除供应商「${supplier.supplierName}」吗？`)) return;
    const previous = suppliers;
    setSupplierActionId(supplier.id);
    setSuppliers((current) => current.filter((item) => item.id !== supplier.id));
    try {
      await deleteInventorySupplier(supplier.id);
      showToast('供应商已删除', 'success');
    } catch (error) {
      setSuppliers(previous);
      showToast(error instanceof Error ? error.message : '供应商删除失败', 'error');
    } finally {
      setSupplierActionId(null);
    }
  };

  const filtered = useMemo(() => {
    const keyword = searchTerm.trim().toLowerCase();
    return suppliers.filter((supplier) => {
      if (keyword && ![supplier.supplierName, supplier.supplierCode, supplier.contactName || '']
        .some((value) => value.toLowerCase().includes(keyword))) return false;
      return statusFilter === 'all' || supplier.status === Number(statusFilter);
    });
  }, [searchTerm, statusFilter, suppliers]);

  return (
    <div className="space-y-6 animate-in fade-in-50 duration-200">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-xl md:text-2xl font-bold text-[#191C1E] tracking-tight">供应商档案与采购管理</h2>
            <span className="text-xs bg-slate-100 text-slate-700 font-semibold px-2 py-0.5 rounded-full border border-slate-200">供应链 SRM</span>
          </div>
          <p className="text-xs md:text-sm text-[#434655] mt-0.5">维护供应商资质、联系人和供货状态，数据来自库存服务接口。</p>
        </div>
        <button
          type="button"
          onClick={() => openSupplierForm()}
          className="h-[36px] px-4 rounded-lg bg-[#2563EB] text-white hover:bg-blue-700 flex items-center justify-center gap-2 text-xs font-semibold shadow-xs"
        >
          <Plus className="w-4 h-4" />新建供应商档案
        </button>
      </div>

      <section className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs overflow-hidden">
        <div className="p-4 border-b border-[#E2E8F0] bg-[#F8FAFC]/50 flex flex-wrap gap-3 items-center justify-between">
          <div className="relative max-w-md flex-1 min-w-[220px]">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              type="text"
              value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
              placeholder="搜索供应商编码、名称或联系人…"
              className="w-full h-9 pl-9 pr-3 text-sm rounded-lg border border-[#E2E8F0] bg-white outline-none"
            />
          </div>
          <div className="flex items-center gap-2">
            <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as 'all' | '1' | '0')} className="h-9 px-3 text-sm rounded-lg border border-[#E2E8F0] bg-white">
              <option value="all">全部状态</option>
              <option value="1">已启用</option>
              <option value="0">已停用</option>
            </select>
            <button type="button" onClick={() => void loadSuppliers()} disabled={supplierLoading} className="h-9 px-3 rounded-lg border border-[#E2E8F0] text-xs font-semibold text-gray-600 hover:text-blue-600 disabled:opacity-50 flex items-center gap-1.5">
              <RefreshCw className={`w-4 h-4 ${supplierLoading ? 'animate-spin' : ''}`} />刷新
            </button>
          </div>
        </div>
        {supplierLoading ? (
          <div className="flex items-center justify-center py-16 text-sm text-gray-500"><Loader2 className="w-4 h-4 mr-2 animate-spin" />正在加载供应商…</div>
        ) : supplierError ? (
          <div className="py-16 text-center text-sm text-red-600">
            <p>{supplierError}</p>
            <button type="button" onClick={() => void loadSuppliers()} className="mt-3 inline-flex items-center gap-1 text-blue-600 hover:underline"><RefreshCw className="w-3.5 h-3.5" />重新加载</button>
          </div>
        ) : filtered.length === 0 ? (
          <div className="py-16 text-center text-sm text-gray-500">
            <Truck className="w-8 h-8 mx-auto mb-2 text-gray-300" />
            {suppliers.length === 0 ? '暂无供应商档案，请先新增供应商。' : '没有匹配当前筛选条件的供应商。'}
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4 p-4">
            {filtered.map((supplier) => {
              const actionLoading = supplierActionId === supplier.id;
              const active = supplier.status === 1;
              return (
                <article key={supplier.id} className="bg-white rounded-xl border border-[#E2E8F0] shadow-xs p-5 hover:border-blue-300 transition-all flex flex-col justify-between">
                  <div>
                    <div className="flex items-start justify-between gap-2 mb-2">
                      <span className="text-xs font-semibold text-blue-600 bg-blue-50 px-2 py-0.5 rounded">{supplier.supplierCode}</span>
                      <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${active ? 'text-emerald-800 bg-emerald-100' : 'text-gray-600 bg-gray-100'}`}>{supplierStatusText(supplier.status)}</span>
                    </div>
                    <h3 className="font-bold text-gray-900 text-sm mb-1">{supplier.supplierName}</h3>
                    <div className="space-y-1.5 text-xs text-gray-600">
                      <div className="flex items-center gap-1.5"><Phone className="w-3.5 h-3.5 text-gray-400" /><span>{supplier.contactName || '未填写联系人'} {supplier.contactPhone ? `(${supplier.contactPhone})` : ''}</span></div>
                      <div className="flex items-center gap-1.5"><FileText className="w-3.5 h-3.5 text-gray-400" /><span>{supplier.remark || '暂无备注'}</span></div>
                      <div className="flex items-center gap-1.5"><MapPin className="w-3.5 h-3.5 text-gray-400 shrink-0" /><span className="truncate">{supplier.address || '未填写地址'}</span></div>
                    </div>
                  </div>
                  <div className="mt-4 pt-3 border-t border-gray-100 flex items-center justify-between text-xs">
                    <button type="button" disabled={actionLoading} onClick={() => void toggleSupplierStatus(supplier)} className={`font-semibold px-2 py-1 rounded disabled:opacity-50 ${active ? 'text-amber-700 hover:bg-amber-50' : 'text-emerald-700 hover:bg-emerald-50'}`}>{actionLoading ? '处理中…' : active ? '停用' : '启用'}</button>
                    <div className="inline-flex items-center gap-1">
                      <button type="button" disabled={actionLoading} onClick={() => openSupplierForm(supplier)} className="p-2 text-gray-400 hover:text-blue-600 rounded disabled:opacity-40" title="编辑供应商" aria-label="编辑供应商"><Edit3 className="w-4 h-4" /></button>
                      <button type="button" disabled={actionLoading} onClick={() => void removeSupplier(supplier)} className="p-2 text-gray-400 hover:text-red-600 rounded disabled:opacity-40" title="删除供应商" aria-label="删除供应商"><Trash2 className="w-4 h-4" /></button>
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </section>

      {editingSupplier && (
        <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-label="供应商编辑">
          <form onSubmit={submitSupplier} className="bg-white rounded-xl shadow-xl w-full max-w-lg p-6 space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-bold text-gray-900">{editingSupplier.id ? '编辑供应商' : '新增供应商'}</h3>
              <button type="button" onClick={() => setEditingSupplier(null)} className="p-2 text-gray-400 hover:text-gray-700 rounded" aria-label="关闭"><X className="w-5 h-5" /></button>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
              <label className="text-xs text-gray-600">供应商编码
                <input required maxLength={64} value={editingSupplier.supplierCode} onChange={(event) => updateSupplierForm('supplierCode', event.target.value)} className="mt-1 w-full h-10 border border-[#E2E8F0] rounded-lg px-3 text-sm uppercase" placeholder="例如：SUP-SZ-001" />
              </label>
              <label className="text-xs text-gray-600">供应商名称
                <input required maxLength={128} value={editingSupplier.supplierName} onChange={(event) => updateSupplierForm('supplierName', event.target.value)} className="mt-1 w-full h-10 border border-[#E2E8F0] rounded-lg px-3 text-sm" placeholder="请输入企业全称" />
              </label>
              <label className="text-xs text-gray-600">联系人
                <input maxLength={64} value={editingSupplier.contactName} onChange={(event) => updateSupplierForm('contactName', event.target.value)} className="mt-1 w-full h-10 border border-[#E2E8F0] rounded-lg px-3 text-sm" />
              </label>
              <label className="text-xs text-gray-600">联系电话
                <input maxLength={32} value={editingSupplier.contactPhone} onChange={(event) => updateSupplierForm('contactPhone', event.target.value)} className="mt-1 w-full h-10 border border-[#E2E8F0] rounded-lg px-3 text-sm" />
              </label>
              <label className="text-xs text-gray-600">状态
                <select value={editingSupplier.status} onChange={(event) => updateSupplierForm('status', Number(event.target.value))} className="mt-1 w-full h-10 border border-[#E2E8F0] rounded-lg px-3 text-sm"><option value={1}>启用</option><option value={0}>停用</option></select>
              </label>
              <label className="text-xs text-gray-600 md:col-span-2">地址
                <input maxLength={255} value={editingSupplier.address} onChange={(event) => updateSupplierForm('address', event.target.value)} className="mt-1 w-full h-10 border border-[#E2E8F0] rounded-lg px-3 text-sm" />
              </label>
              <label className="text-xs text-gray-600 md:col-span-2">备注
                <textarea maxLength={500} rows={3} value={editingSupplier.remark} onChange={(event) => updateSupplierForm('remark', event.target.value)} className="mt-1 w-full border border-[#E2E8F0] rounded-lg px-3 py-2 text-sm" />
              </label>
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <button type="button" onClick={() => setEditingSupplier(null)} className="px-4 py-2 rounded-lg border border-[#E2E8F0] text-sm">取消</button>
              <button disabled={supplierSaving} type="submit" className="px-4 py-2 rounded-lg bg-blue-600 text-white text-sm flex items-center gap-2 disabled:opacity-60"><Save className="w-4 h-4" />{supplierSaving ? '保存中…' : '保存'}</button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
