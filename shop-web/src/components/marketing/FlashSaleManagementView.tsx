import React, { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AlertCircle, Clock, Edit3, ImageOff, PackagePlus, Plus, RefreshCw, Trash2, X, Zap } from 'lucide-react';
import {
  BackendCatalogSku,
  BackendMarketingFlashSale,
  BackendMarketingFlashSaleItem,
  BackendPage,
  deleteMarketingFlashSale,
  listCatalogProductsByIds,
  listCatalogSkus,
  listMarketingFlashSaleItems,
  listMarketingFlashSales,
  saveMarketingFlashSale,
  updateMarketingFlashSaleStatus,
} from '../../api/adminApi';
import { ProductPickerModal, ProductPickerSelection } from '../common/ProductPickerModal';
import { PermissionDenied } from '../common/PermissionGate';
import { useAdmin } from '../../context/AdminContext';

type StatusFilter = '' | '0' | '1' | '2';
type ItemForm = {
  id?: number;
  productId: string;
  productName?: string;
  productCode?: string;
  imageUrl?: string;
  skuId: string;
  skuName?: string;
  skuCode?: string;
  originalPrice?: number;
  availableStock?: number;
  soldStock?: number;
  activityPrice: string;
  totalStock: string;
  limitPerMember: string;
  status: '0' | '1';
  version?: number;
};
type ActivityForm = {
  id?: number;
  activityCode: string;
  activityName: string;
  startAt: string;
  endAt: string;
  limitPerMember: string;
  status: '0' | '1' | '2';
  version?: number;
  items: ItemForm[];
};
type FlashSaleRecord = BackendMarketingFlashSale & { items: BackendMarketingFlashSaleItem[] };
const PAGE_SIZE = 20;

/** 转换后端时间至 datetime-local 控件格式。 */
function dateInput(value?: string): string { return value ? value.replace(' ', 'T').slice(0, 16) : ''; }

/** 补齐时间秒数，确保后端 LocalDateTime 可解析。 */
function backendDate(value: string): string { return value.length === 16 ? `${value}:00` : value; }

/** 列表展示用时间。后端返回的 LocalDateTime 带毫秒，这里截断到分钟。 */
function displayDate(value?: string): string {
  const matched = /^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2})/.exec(value || '');
  return matched ? `${matched[1]} ${matched[2]}` : value || '—';
}

/** 创建新活动默认表单。商品明细一律通过商品选择弹窗加入。 */
function emptyForm(): ActivityForm {
  const start = new Date(Date.now() + 3600000);
  const end = new Date(start.getTime() + 86400000);
  const input = (date: Date) => date.toISOString().slice(0, 16);
  return {
    activityCode: `FLASH${Date.now().toString().slice(-8)}`,
    activityName: '',
    startAt: input(start),
    endAt: input(end),
    limitPerMember: '1',
    status: '0',
    items: [],
  };
}

/** 将活动实体及商品明细转换为编辑表单。商品名称与主图随后由服务端回填。 */
function editForm(record: FlashSaleRecord): ActivityForm {
  return {
    id: record.id,
    activityCode: record.activityCode,
    activityName: record.activityName,
    startAt: dateInput(record.startAt),
    endAt: dateInput(record.endAt),
    limitPerMember: String(record.limitPerMember || 1),
    status: String(record.status) as ActivityForm['status'],
    version: record.version,
    items: record.items.map((item) => ({
      id: item.id,
      productId: String(item.productId),
      skuId: item.skuId == null ? '' : String(item.skuId),
      activityPrice: String(item.activityPrice || ''),
      totalStock: String(item.totalStock || 0),
      limitPerMember: String(item.limitPerMember || 1),
      status: item.status === 0 ? '0' : '1',
      soldStock: item.soldStock || 0,
      version: item.version,
    })),
  };
}

/** 返回活动状态文本，启用状态下结合排期时间展示实时阶段。 */
function statusText(status: number, startAt?: string, endAt?: string): string {
  if (status === 0) return '草稿';
  if (status === 2) return '已停用';
  const now = Date.now();
  const start = startAt ? new Date(startAt).getTime() : NaN;
  const end = endAt ? new Date(endAt).getTime() : NaN;
  if (Number.isFinite(start) && now < start) return '即将开始';
  if (Number.isFinite(end) && now > end) return '已结束';
  return '进行中';
}

/** 选择结果转为表单明细行，默认以原价和可用库存作为活动价、活动库存的初值。 */
function selectionToItem(selection: ProductPickerSelection): ItemForm {
  return {
    productId: String(selection.productId),
    skuId: selection.skuId === undefined ? '' : String(selection.skuId),
    productName: selection.productName,
    productCode: selection.productCode,
    imageUrl: selection.imageUrl,
    skuName: selection.skuName,
    skuCode: selection.skuCode,
    originalPrice: selection.originalPrice,
    availableStock: selection.availableStock,
    activityPrice: selection.originalPrice ? String(selection.originalPrice) : '',
    totalStock: String(selection.availableStock || 0),
    limitPerMember: '1',
    status: '1',
  };
}

export const FlashSaleManagementView: React.FC = () => {
  const { showToast, hasPermission, confirm } = useAdmin();
  const canQuery = hasPermission('marketing:flash:query');
  const canSave = hasPermission('marketing:flash:save');
  const canStatus = hasPermission('marketing:flash:status');
  const canDelete = hasPermission('marketing:flash:delete');
  const [records, setRecords] = useState<FlashSaleRecord[]>([]);
  const [page, setPage] = useState<BackendPage<BackendMarketingFlashSale> | null>(null);
  const [current, setCurrent] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<StatusFilter>('');
  const [filter, setFilter] = useState<{ keyword: string; status?: number }>({ keyword: '' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [modal, setModal] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState<ActivityForm>(() => emptyForm());
  const [hydrating, setHydrating] = useState(false);
  const [picker, setPicker] = useState<{ mode: 'multi' | 'single'; index: number | null } | null>(null);
  const hydrateToken = useRef(0);

  const load = useCallback(async () => {
    if (!canQuery) return;
    setLoading(true);
    setError(null);
    try {
      const result = await listMarketingFlashSales({ current, size: PAGE_SIZE, keyword: filter.keyword || undefined, status: filter.status });
      const enriched = await Promise.all((result.records || []).map(async (record) => ({ ...record, items: await listMarketingFlashSaleItems(record.id) })));
      setPage(result);
      setRecords(enriched);
    } catch (requestError) {
      const message = requestError instanceof Error ? requestError.message : '秒杀活动加载失败，请稍后重试';
      setError(message);
      setRecords([]);
      showToast(message, 'error');
    } finally {
      setLoading(false);
    }
  }, [canQuery, current, filter, showToast]);

  useEffect(() => { void load(); }, [load]);

  /**
   * 回填商品名称、主图与可用库存。
   *
   * <p>活动明细表只保存商品与 SKU 主键，编辑已有活动时需要按主键把展示信息取回来。</p>
   */
  const hydrateItems = useCallback(async (items: ItemForm[]): Promise<ItemForm[]> => {
    const targets = items.filter((item) => item.productId && !item.productName);
    if (targets.length === 0) return items;
    const productIds = Array.from(new Set(targets.map((item) => Number(item.productId))));
    const products = await listCatalogProductsByIds(productIds);
    const productMap = new Map(products.map((product) => [product.id, product]));
    const skuProductIds = Array.from(new Set(targets.filter((item) => item.skuId).map((item) => Number(item.productId))));
    const skuEntries = await Promise.all(skuProductIds.map(async (productId) => {
      try {
        return [productId, (await listCatalogSkus(productId)) || []] as const;
      } catch {
        return [productId, [] as BackendCatalogSku[]] as const;
      }
    }));
    const skuMap = new Map<number, BackendCatalogSku[]>(skuEntries);
    return items.map((item) => {
      if (!item.productId || item.productName) return item;
      const product = productMap.get(Number(item.productId));
      if (!product) return { ...item, productName: `商品 #${item.productId}（已删除或下架）` };
      const sku = item.skuId
        ? (skuMap.get(Number(item.productId)) || []).find((candidate) => String(candidate.id) === item.skuId)
        : undefined;
      return {
        ...item,
        productName: product.productName,
        productCode: product.productCode,
        imageUrl: product.mainImageUrl,
        skuName: sku?.skuName,
        skuCode: sku?.skuCode,
        originalPrice: sku ? Number(sku.price || 0) : Number(product.price || 0),
        availableStock: sku ? Number(sku.stock || 0) : Number(product.currentStock || 0),
      };
    });
  }, []);

  /** 打开新建弹窗。 */
  const openCreate = () => {
    hydrateToken.current += 1;
    setForm(emptyForm());
    setHydrating(false);
    setModal(true);
  };

  /** 打开编辑弹窗并异步回填商品展示信息。 */
  const openEdit = async (record: FlashSaleRecord) => {
    const token = hydrateToken.current + 1;
    hydrateToken.current = token;
    const next = editForm(record);
    setForm(next);
    setModal(true);
    setHydrating(next.items.length > 0);
    if (next.items.length === 0) return;
    try {
      const hydrated = await hydrateItems(next.items);
      if (hydrateToken.current !== token) return;
      setForm((previous) => ({ ...previous, items: hydrated }));
    } catch (requestError) {
      if (hydrateToken.current !== token) return;
      showToast(requestError instanceof Error ? requestError.message : '商品信息回显失败，请重新选择商品', 'warning');
    } finally {
      if (hydrateToken.current === token) setHydrating(false);
    }
  };

  const total = page?.total || 0;
  const totalPages = Math.max(page?.pages || Math.ceil(total / (page?.size || PAGE_SIZE)) || 1, 1);

  const updateItem = (index: number, changes: Partial<ItemForm>) => {
    setForm((previous) => ({ ...previous, items: previous.items.map((item, itemIndex) => (itemIndex === index ? { ...item, ...changes } : item)) }));
  };

  const removeItem = (index: number) => {
    setForm((previous) => ({ ...previous, items: previous.items.filter((_, itemIndex) => itemIndex !== index) }));
  };

  /** 已被其他明细行占用的组合，弹窗中置灰避免重复添加。 */
  const pickerExcludeKeys = useMemo(() => {
    if (!picker) return [];
    return form.items
      .filter((item, index) => (picker.index === null || index !== picker.index) && item.productId)
      .map((item) => `${item.productId}:${item.skuId || 0}`);
  }, [picker, form.items]);

  /** 「更换」时把当前行的选择带进弹窗，便于对照。 */
  const pickerInitialSelection = useMemo<ProductPickerSelection[]>(() => {
    if (!picker || picker.index === null) return [];
    const item = form.items[picker.index];
    if (!item || !item.productId) return [];
    return [{
      productId: Number(item.productId),
      skuId: item.skuId ? Number(item.skuId) : undefined,
      productName: item.productName || `商品 #${item.productId}`,
      productCode: item.productCode || '',
      skuName: item.skuName,
      skuCode: item.skuCode,
      imageUrl: item.imageUrl,
      originalPrice: Number(item.originalPrice || 0),
      availableStock: Number(item.availableStock || 0),
    }];
  }, [picker, form.items]);

  /** 弹窗确认：批量追加，或替换指定行且保留已填的活动价与活动库存。 */
  const handlePickerConfirm = (selections: ProductPickerSelection[]) => {
    if (!picker || selections.length === 0) return;
    const target = picker;
    setForm((previous) => {
      const items = [...previous.items];
      const defaultLimit = previous.limitPerMember || '1';
      if (target.index === null) {
        items.push(...selections.map(selectionToItem));
      } else {
        const existing = items[target.index];
        const [first] = selections;
        const next = selectionToItem(first);
        const originalPrice = next.originalPrice || 0;
        const keptPrice = existing?.activityPrice && Number(existing.activityPrice) > 0 && Number(existing.activityPrice) <= originalPrice
          ? existing.activityPrice
          : next.activityPrice;
        items[target.index] = {
          ...next,
          id: existing?.id,
          version: existing?.version,
          soldStock: existing?.soldStock,
          activityPrice: keptPrice,
          totalStock: existing?.totalStock || next.totalStock,
          limitPerMember: existing?.limitPerMember || defaultLimit,
          status: existing?.status || '1',
        };
      }
      return { ...previous, items };
    });
    setPicker(null);
  };

  if (!canQuery) return <PermissionDenied title="暂无秒杀活动查看权限" />;

  const submitForm = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!form.activityCode.trim() || !form.activityName.trim()) return showToast('请填写活动编码和活动名称', 'warning');
    if (!form.startAt || !form.endAt || new Date(form.startAt).getTime() >= new Date(form.endAt).getTime()) return showToast('活动开始时间必须早于结束时间', 'warning');
    const activityLimit = Number(form.limitPerMember);
    if (!Number.isInteger(activityLimit) || activityLimit < 1) return showToast('活动限购数量必须为正整数', 'warning');
    if (form.items.length === 0) return showToast('请至少添加一个活动商品', 'warning');
    const formItemsValid = form.items.every((item) => {
      const productId = Number(item.productId);
      const activityPrice = Number(item.activityPrice);
      const totalStock = Number(item.totalStock);
      const itemLimit = Number(item.limitPerMember);
      const stockValid = item.availableStock === undefined || totalStock - Number(item.soldStock || 0) <= item.availableStock;
      const priceValid = item.originalPrice === undefined || activityPrice <= item.originalPrice;
      return Number.isInteger(productId) && productId > 0 && Number.isFinite(activityPrice) && activityPrice > 0
        && Number.isInteger(totalStock) && totalStock >= 0 && Number.isInteger(itemLimit) && itemLimit >= 1
        && totalStock >= itemLimit && stockValid && priceValid;
    });
    if (!formItemsValid) return showToast('请检查商品、SKU、活动价、活动库存和限购数量', 'warning');
    const itemKeys = form.items.map((item) => `${item.productId}:${item.skuId || 0}`);
    if (new Set(itemKeys).size !== itemKeys.length) return showToast('活动商品不可重复', 'warning');
    setSaving(true);
    try {
      await saveMarketingFlashSale({
        id: form.id,
        activityCode: form.activityCode.trim().toUpperCase(),
        activityName: form.activityName.trim(),
        startAt: backendDate(form.startAt),
        endAt: backendDate(form.endAt),
        limitPerMember: activityLimit,
        status: Number(form.status),
        version: form.version,
        items: form.items.map((item) => ({ id: item.id, productId: Number(item.productId), skuId: item.skuId ? Number(item.skuId) : undefined, activityPrice: Number(item.activityPrice), totalStock: Number(item.totalStock), limitPerMember: Number(item.limitPerMember), status: Number(item.status), version: item.version })),
      });
      setModal(false);
      showToast('秒杀活动已保存', 'success');
      await load();
    } catch (requestError) {
      showToast(requestError instanceof Error ? requestError.message : '秒杀活动保存失败', 'error');
    } finally {
      setSaving(false);
    }
  };

  const submitFilter = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setCurrent(1);
    setFilter({ keyword: keyword.trim(), status: status === '' ? undefined : Number(status) });
  };
  const resetFilter = () => { setKeyword(''); setStatus(''); setCurrent(1); setFilter({ keyword: '' }); };
  const toggleStatus = async (record: FlashSaleRecord) => {
    if (!canStatus) return;
    try {
      const next = record.status === 1 ? 2 : 1;
      await updateMarketingFlashSaleStatus(record.id, next);
      showToast(`活动已${next === 1 ? '启用' : '停用'}`, 'success');
      await load();
    } catch (requestError) {
      showToast(requestError instanceof Error ? requestError.message : '活动状态更新失败', 'error');
    }
  };
  const remove = async (record: FlashSaleRecord) => {
    if (!canDelete || !await confirm(`确定删除活动「${record.activityName}」吗？`, '删除秒杀活动')) return;
    try {
      await deleteMarketingFlashSale(record.id);
      showToast('活动已删除', 'success');
      if (records.length === 1 && current > 1) setCurrent((value) => value - 1); else await load();
    } catch (requestError) {
      showToast(requestError instanceof Error ? requestError.message : '活动删除失败', 'error');
    }
  };

  return <div className="space-y-6 animate-in fade-in-50 duration-200">
    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
      <div>
        <h2 className="text-xl md:text-2xl font-bold text-[#191C1E]">限时秒杀活动管理</h2>
        <p className="text-xs md:text-sm text-[#434655] mt-1">配置活动排期、限量库存和会员限购，并实时控制活动启停。</p>
      </div>
      {canSave && <button type="button" onClick={openCreate} className="h-9 px-4 rounded-lg bg-orange-600 text-white text-xs font-semibold inline-flex items-center gap-2"><Plus className="w-4 h-4" />新建活动</button>}
    </div>

    <form onSubmit={submitFilter} className="flex flex-wrap items-end gap-2 bg-white rounded-xl border border-[#E2E8F0] p-4">
      <label className="flex flex-col gap-1 text-xs text-gray-500">活动编码或名称<input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="输入关键字" className="h-8 w-52 px-2 rounded-lg border border-gray-200 text-xs" /></label>
      <label className="flex flex-col gap-1 text-xs text-gray-500">活动状态<select value={status} onChange={(event) => setStatus(event.target.value as StatusFilter)} className="h-8 w-32 px-2 rounded-lg border border-gray-200 text-xs bg-white"><option value="">全部状态</option><option value="0">草稿</option><option value="1">已启用</option><option value="2">已停用</option></select></label>
      <button type="submit" className="h-8 px-3 rounded-lg bg-orange-600 text-white text-xs font-semibold">查询</button>
      <button type="button" onClick={resetFilter} className="h-8 px-3 rounded-lg border border-gray-200 text-xs">重置</button>
      <button type="button" onClick={() => void load()} disabled={loading} className="h-8 px-3 rounded-lg border border-gray-200 text-xs inline-flex items-center gap-1"><RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />刷新</button>
    </form>

    {error ? <div role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 flex items-center justify-between text-sm text-red-700"><span className="inline-flex items-center gap-2"><AlertCircle className="w-4 h-4" />{error}</span><button type="button" onClick={() => void load()} className="text-xs font-semibold">重试</button></div>
      : loading ? <div role="status" className="py-14 text-center text-sm text-gray-400">活动加载中...</div>
      : records.length === 0 ? <div className="py-14 text-center text-sm text-gray-400">暂无符合条件的活动，请调整筛选条件或新建活动。</div>
      : <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
        {records.map((record) => {
          const sold = record.items.reduce((sum, item) => sum + Number(item.soldStock || 0), 0);
          const stock = record.items.reduce((sum, item) => sum + Number(item.totalStock || 0), 0);
          const progress = stock ? Math.min(100, sold / stock * 100) : 0;
          return <article key={record.id} className="bg-white rounded-xl border border-[#E2E8F0] p-5 shadow-xs">
            <div className="flex justify-between gap-3">
              <div className="flex gap-2 min-w-0">
                <span className="p-1.5 rounded-lg bg-orange-50 text-orange-600"><Zap className="w-4 h-4" /></span>
                <div className="min-w-0">
                  <h3 className="font-bold text-sm truncate">{record.activityName}</h3>
                  <p className="font-mono text-[11px] text-gray-400 truncate">{record.activityCode}</p>
                </div>
              </div>
              <span className="text-[11px] font-semibold text-gray-600 bg-gray-100 px-2 py-0.5 rounded-full shrink-0">{statusText(record.status, record.startAt, record.endAt)}</span>
            </div>
            <div className="mt-4 space-y-2 text-xs text-gray-600">
              <div className="flex items-center gap-1"><Clock className="w-3.5 h-3.5 text-gray-400" />{displayDate(record.startAt)} 至 {displayDate(record.endAt)}</div>
              <div className="flex justify-between"><span>活动商品 {record.items.length} 个</span><span>已售 {sold} / {stock} 件</span></div>
              <div className="flex flex-wrap gap-1">
                {record.items.slice(0, 3).map((item) => <span key={item.id} className="bg-orange-50 text-orange-700 px-1.5 py-0.5 rounded text-[11px]">商品 #{item.productId} ¥{Number(item.activityPrice || 0).toFixed(2)}</span>)}
              </div>
              <div className="h-1.5 bg-gray-100 rounded-full overflow-hidden"><div className="h-full bg-orange-500" style={{ width: `${progress}%` }} /></div>
            </div>
            <div className="mt-4 pt-3 border-t border-gray-100 flex justify-between items-center">
              <span className="text-xs text-gray-500">单会员限购 {record.limitPerMember} 件</span>
              <span className="flex items-center gap-2">
                {canSave && <button type="button" onClick={() => void openEdit(record)} className="text-xs text-orange-600 inline-flex items-center gap-1"><Edit3 className="w-3.5 h-3.5" />编辑</button>}
                {canStatus && <button type="button" onClick={() => void toggleStatus(record)} className="text-xs text-gray-600">{record.status === 1 ? '停用' : '启用'}</button>}
                {canDelete && <button type="button" onClick={() => void remove(record)} className="text-xs text-red-600 inline-flex items-center gap-1"><Trash2 className="w-3.5 h-3.5" />删除</button>}
              </span>
            </div>
          </article>;
        })}
      </div>}

    {!error && !loading && page && <div className="flex items-center justify-between text-xs text-gray-500">
      <span>共 {total} 条，第 {Math.min(current, totalPages)} / {totalPages} 页</span>
      <span className="flex gap-2">
        <button type="button" onClick={() => setCurrent((value) => Math.max(1, value - 1))} disabled={current <= 1} className="h-8 px-3 rounded-lg border border-gray-200 disabled:opacity-40">上一页</button>
        <button type="button" onClick={() => setCurrent((value) => Math.min(totalPages, value + 1))} disabled={current >= totalPages} className="h-8 px-3 rounded-lg border border-gray-200 disabled:opacity-40">下一页</button>
      </span>
    </div>}

    {modal && canSave && <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
      <form onSubmit={submitForm} className="bg-white rounded-xl max-w-3xl w-full max-h-[90vh] overflow-y-auto p-6">
        <div className="flex justify-between items-center border-b border-gray-200 pb-3">
          <h3 className="font-bold">{form.id ? '编辑秒杀活动' : '新建秒杀活动'}</h3>
          <button type="button" onClick={() => setModal(false)}><X className="w-5 h-5 text-gray-400" /></button>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 py-4">
          <label className="text-xs text-gray-600">活动编码<input required value={form.activityCode} onChange={(event) => setForm((value) => ({ ...value, activityCode: event.target.value }))} className="mt-1 h-9 w-full border rounded px-2 text-sm" /></label>
          <label className="text-xs text-gray-600">活动名称<input required value={form.activityName} onChange={(event) => setForm((value) => ({ ...value, activityName: event.target.value }))} className="mt-1 h-9 w-full border rounded px-2 text-sm" /></label>
          <label className="text-xs text-gray-600">开始时间<input required type="datetime-local" value={form.startAt} onChange={(event) => setForm((value) => ({ ...value, startAt: event.target.value }))} className="mt-1 h-9 w-full border rounded px-2 text-sm" /></label>
          <label className="text-xs text-gray-600">结束时间<input required type="datetime-local" value={form.endAt} onChange={(event) => setForm((value) => ({ ...value, endAt: event.target.value }))} className="mt-1 h-9 w-full border rounded px-2 text-sm" /></label>
          <label className="text-xs text-gray-600">活动限购<input required min="1" type="number" value={form.limitPerMember} onChange={(event) => setForm((value) => ({ ...value, limitPerMember: event.target.value }))} className="mt-1 h-9 w-full border rounded px-2 text-sm" /></label>
          <label className="text-xs text-gray-600">状态<select value={form.status} onChange={(event) => setForm((value) => ({ ...value, status: event.target.value as ActivityForm['status'] }))} className="mt-1 h-9 w-full border rounded px-2 text-sm bg-white"><option value="0">草稿</option><option value="1">启用</option><option value="2">停用</option></select></label>
        </div>

        <div className="border-t pt-4">
          <div className="flex flex-wrap items-end justify-between gap-2 mb-3">
            <div>
              <h4 className="text-sm font-semibold">活动商品</h4>
              <p className="text-[11px] text-gray-400 mt-1">仅可选择已上架商品，支持按关键字与分类检索；活动价和库存由服务端再次校验。</p>
            </div>
            <button type="button" onClick={() => setPicker({ mode: 'multi', index: null })} className="h-8 px-3 rounded-lg bg-orange-600 text-white text-xs font-semibold inline-flex items-center gap-1.5"><Plus className="w-3.5 h-3.5" />选择商品</button>
          </div>

          {hydrating && <p className="mb-3 text-[11px] text-gray-400">正在回显商品信息...</p>}

          {form.items.length === 0
            ? <div className="py-10 text-center text-gray-400 border border-dashed border-gray-200 rounded-lg">
              <PackagePlus className="w-9 h-9 mx-auto mb-2 opacity-40" />
              <p className="text-xs">还没有选择商品，点击右上方「选择商品」添加。</p>
            </div>
            : form.items.map((item, index) => <div key={`${item.id || item.productId}-${index}`} className="mb-3 rounded-lg border border-gray-200 bg-gray-50 p-3">
              <div className="flex items-start gap-3">
                <div className="h-14 w-14 shrink-0 overflow-hidden rounded-lg border border-gray-200 bg-white flex items-center justify-center">
                  {item.imageUrl ? <img src={item.imageUrl} alt={item.productName || '商品主图'} className="h-full w-full object-cover" /> : <ImageOff className="w-5 h-5 text-gray-300" />}
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-gray-900 truncate">{item.productName || `商品 #${item.productId}（信息加载中）`}</p>
                      <p className="mt-0.5 font-mono text-[11px] text-gray-400 truncate">{item.productCode || '-'}</p>
                    </div>
                    <div className="flex items-center gap-1 shrink-0">
                      <button type="button" onClick={() => setPicker({ mode: 'single', index })} className="h-7 rounded border border-gray-300 bg-white px-2 text-[11px] text-orange-600">更换</button>
                      <button type="button" onClick={() => removeItem(index)} className="h-7 rounded border border-gray-300 bg-white px-2 text-[11px] text-red-600">移除</button>
                    </div>
                  </div>
                  <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-gray-500">
                    <span>{item.skuName ? `规格：${item.skuName}` : '规格：商品整体（不指定 SKU）'}</span>
                    {item.skuCode ? <span className="font-mono text-gray-400">{item.skuCode}</span> : null}
                    <span>可用库存：{item.availableStock === undefined ? '-' : item.availableStock}</span>
                    <span>已售活动库存：{item.soldStock || 0}</span>
                  </div>
                </div>
              </div>
              <div className="mt-2.5 grid grid-cols-2 sm:grid-cols-4 gap-2">
                <label className="text-[11px] text-gray-600">原价<input readOnly value={item.originalPrice === undefined ? '' : Number(item.originalPrice).toFixed(2)} className="mt-1 h-8 w-full border rounded px-2 text-xs bg-gray-100" /></label>
                <label className="text-[11px] text-gray-600">活动价<input required type="number" min="0.01" step="0.01" value={item.activityPrice} onChange={(event) => updateItem(index, { activityPrice: event.target.value })} className="mt-1 h-8 w-full border rounded px-2 text-xs" /></label>
                <label className="text-[11px] text-gray-600">活动库存<input required type="number" min="0" value={item.totalStock} onChange={(event) => updateItem(index, { totalStock: event.target.value })} className="mt-1 h-8 w-full border rounded px-2 text-xs" /></label>
                <label className="text-[11px] text-gray-600">单会员限购<input required min="1" type="number" value={item.limitPerMember} onChange={(event) => updateItem(index, { limitPerMember: event.target.value })} className="mt-1 h-8 w-full border rounded px-2 text-xs" /></label>
              </div>
            </div>)}
        </div>

        <div className="flex justify-end gap-2 border-t pt-4 mt-4">
          <button type="button" onClick={() => setModal(false)} className="h-9 px-4 border rounded text-xs">取消</button>
          <button type="submit" disabled={saving} className="h-9 px-4 rounded bg-orange-600 text-white text-xs disabled:opacity-50">{saving ? '保存中...' : '保存活动'}</button>
        </div>
      </form>
    </div>}

    {picker && <ProductPickerModal
      mode={picker.mode}
      excludeKeys={pickerExcludeKeys}
      initialSelection={pickerInitialSelection}
      onClose={() => setPicker(null)}
      onConfirm={handlePickerConfirm}
    />}
  </div>;
};
