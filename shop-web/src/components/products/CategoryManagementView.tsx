import React, { FormEvent, useEffect, useMemo, useState } from 'react';
import {
  ArrowDown,
  ArrowUp,
  ChevronDown,
  ChevronRight,
  Edit3,
  FolderTree,
  GripVertical,
  Image as ImageIcon,
  Loader2,
  Plus,
  Power,
  RefreshCw,
  Save,
  Trash2,
  Upload,
  X
} from 'lucide-react';
import {
  BackendCatalogCategory,
  deleteCatalogCategory,
  listManageCatalogCategories,
  saveCatalogCategory,
  updateCatalogCategoryStatus,
  uploadStorageFile
} from '../../api/adminApi';
import { useAdmin } from '../../context/AdminContext';
import { useBodyScrollLock } from '../../hooks/useBodyScrollLock';

interface CategoryNode extends BackendCatalogCategory {
  children: CategoryNode[];
}

interface CategoryForm {
  id?: number;
  parentId: number;
  categoryName: string;
  categoryCode: string;
  sortNo: number;
  status: number;
  iconUrl: string;
  remark: string;
}

const emptyForm: CategoryForm = {
  parentId: 0,
  categoryName: '',
  categoryCode: '',
  sortNo: 0,
  status: 1,
  iconUrl: '',
  remark: ''
};

function buildTree(categories: BackendCatalogCategory[]): CategoryNode[] {
  const byParent = new Map<number, BackendCatalogCategory[]>();
  categories.forEach((category) => {
    const parentId = category.parentId || 0;
    const siblings = byParent.get(parentId) || [];
    siblings.push(category);
    byParent.set(parentId, siblings);
  });
  const attach = (category: BackendCatalogCategory, ancestors: Set<number>): CategoryNode => {
    const nextAncestors = new Set(ancestors).add(category.id);
    const children = (byParent.get(category.id) || [])
      .filter((child) => !nextAncestors.has(child.id))
      .sort((a, b) => (a.sortNo || 0) - (b.sortNo || 0) || a.id - b.id)
      .map((child) => attach(child, nextAncestors));
    return { ...category, children };
  };
  return (byParent.get(0) || [])
    .sort((a, b) => (a.sortNo || 0) - (b.sortNo || 0) || a.id - b.id)
    .map((category) => attach(category, new Set()));
}

function flattenTree(nodes: CategoryNode[], depth = 0): Array<{ category: CategoryNode; depth: number }> {
  return nodes.flatMap((node) => [{ category: node, depth }, ...flattenTree(node.children, depth + 1)]);
}

function descendantsOf(id: number, categories: BackendCatalogCategory[]): Set<number> {
  const result = new Set<number>();
  const pending = [id];
  while (pending.length) {
    const parentId = pending.shift()!;
    categories.filter((item) => (item.parentId || 0) === parentId).forEach((item) => {
      if (!result.has(item.id)) {
        result.add(item.id);
        pending.push(item.id);
      }
    });
  }
  return result;
}

/**
 * 汇总「自身停用或任一祖先停用」的类目 ID。
 *
 * 门户导航收到的是一张平表、由前端拼树，父类目停用后子节点会被顶成一级；
 * 因此服务端读取时会按父链把整棵子树滤掉，这里用同一口径标注，
 * 避免管理员看到「开关还开着、门户却不显示」而找不到原因。
 */
function collectDisabledBranchIds(categories: BackendCatalogCategory[]): Set<number> {
  const disabled = new Set<number>();
  categories.forEach((category) => {
    if (category.status !== 1) disabled.add(category.id);
  });
  let expanded = true;
  while (expanded) {
    expanded = false;
    categories.forEach((category) => {
      const parentId = category.parentId || 0;
      if (parentId !== 0 && disabled.has(parentId) && !disabled.has(category.id)) {
        disabled.add(category.id);
        expanded = true;
      }
    });
  }
  return disabled;
}

function formFromCategory(category: BackendCatalogCategory): CategoryForm {
  return {
    id: category.id,
    parentId: category.parentId || 0,
    categoryName: category.categoryName,
    categoryCode: category.categoryCode,
    sortNo: category.sortNo || 0,
    status: category.status,
    iconUrl: category.iconUrl || '',
    remark: category.remark || ''
  };
}

/**
 * 商品类目树维护页面，接入后台类目查询、保存、启停和删除接口。
 *
 * @author Henfon
 * @date 2026-09-01
 */
export const CategoryManagementView: React.FC = () => {
  const { showToast, requirePermission, confirm } = useAdmin();
  const [categories, setCategories] = useState<BackendCatalogCategory[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState<CategoryForm | null>(null);
  const [iconUploading, setIconUploading] = useState(false);
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set());

  const tree = useMemo(() => buildTree(categories), [categories]);
  const rows = useMemo(() => flattenTree(tree), [tree]);
  // 自身停用或上级停用的节点在门户与商品编辑选择器里都不会出现。
  const disabledBranchIds = useMemo(() => collectDisabledBranchIds(categories), [categories]);
  const parentOptions = useMemo(() => {
    const excluded = editing?.id ? new Set([editing.id, ...descendantsOf(editing.id, categories)]) : new Set<number>();
    return rows.filter(({ category }) => !excluded.has(category.id));
  }, [categories, editing, rows]);

  /** 重新读取后台类目树，确保保存和排序后的顺序与服务端一致。 */
  const loadCategories = async () => {
    setLoading(true);
    try {
      const result = await listManageCatalogCategories();
      const records = result || [];
      setCategories(records);
      setExpandedIds((previous) => {
        const next = new Set(previous);
        records.forEach((item) => next.add(item.id));
        return next;
      });
    } catch (error) {
      showToast(error instanceof Error ? error.message : '类目加载失败', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void loadCategories(); }, []);

  const openCreate = (parentId = 0) => {
    if (!requirePermission('catalog:category:query', '新增类目')) return;
    const maxSort = categories
      .filter((item) => (item.parentId || 0) === parentId)
      .reduce((max, item) => Math.max(max, item.sortNo || 0), 0);
    setEditing({ ...emptyForm, parentId, sortNo: maxSort + 10 });
  };

  const openEdit = (category: BackendCatalogCategory) => {
    if (!requirePermission('catalog:category:query', '编辑类目')) return;
    setEditing(formFromCategory(category));
  };

  const updateForm = (key: keyof CategoryForm, value: string | number) => {
    setEditing((current) => current ? { ...current, [key]: value } : current);
  };

  /**
   * 上传类目图标。
   *
   * 表单里存上传接口返回的访问地址，服务端写入时会归一化成对象键，避免把会过期的签名地址写进类目。
   */
  const handleIconUpload = async (file: File) => {
    setIconUploading(true);
    try {
      const uploaded = await uploadStorageFile(file);
      updateForm('iconUrl', uploaded.url);
      showToast('图标上传成功，保存后生效', 'success');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '图标上传失败，请稍后重试', 'error');
    } finally {
      setIconUploading(false);
    }
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!editing || !requirePermission('catalog:category:query', '保存类目')) return;
    if (!editing.categoryName.trim() || !editing.categoryCode.trim()) {
      showToast('请填写类目名称和编码', 'warning');
      return;
    }
    setSaving(true);
    try {
      await saveCatalogCategory({
        id: editing.id,
        parentId: editing.parentId || 0,
        categoryName: editing.categoryName.trim(),
        categoryCode: editing.categoryCode.trim(),
        sortNo: Number(editing.sortNo) || 0,
        status: Number(editing.status),
        iconUrl: editing.iconUrl.trim() || undefined,
        remark: editing.remark.trim() || undefined
      });
      setEditing(null);
      showToast('类目已保存', 'success');
      await loadCategories();
    } catch (error) {
      showToast(error instanceof Error ? error.message : '类目保存失败', 'error');
    } finally {
      setSaving(false);
    }
  };

  const toggleStatus = async (category: BackendCatalogCategory) => {
    if (!requirePermission('catalog:category:query', '修改类目状态')) return;
    if (category.status === 1) {
      const descendants = descendantsOf(category.id, categories);
      if (descendants.size > 0
        && !await confirm(`停用「${category.categoryName}」后，其下 ${descendants.size} 个子类目会一并从门户导航与商品类目选择器中隐藏（子类目自身状态不变，重新启用上级即恢复）。确定停用吗？`, '停用商品类目')) {
        return;
      }
    }
    try {
      await updateCatalogCategoryStatus(category.id, category.status === 1 ? 0 : 1);
      showToast(category.status === 1 ? '类目已停用' : '类目已启用', 'success');
      await loadCategories();
    } catch (error) {
      showToast(error instanceof Error ? error.message : '类目状态更新失败', 'error');
    }
  };

  const remove = async (category: BackendCatalogCategory) => {
    if (!requirePermission('catalog:category:query', '删除类目')) return;
    if (!await confirm(`确定删除「${category.categoryName}」吗？如存在子类目或商品引用，服务端会拒绝删除。`, '删除商品类目')) return;
    try {
      await deleteCatalogCategory(category.id);
      showToast('类目已删除', 'success');
      await loadCategories();
    } catch (error) {
      showToast(error instanceof Error ? error.message : '类目删除失败', 'error');
    }
  };

  const reorder = async (category: BackendCatalogCategory, direction: -1 | 1) => {
    if (!requirePermission('catalog:category:query', '调整类目排序')) return;
    const siblings = categories
      .filter((item) => (item.parentId || 0) === (category.parentId || 0))
      .sort((a, b) => (a.sortNo || 0) - (b.sortNo || 0) || a.id - b.id);
    const index = siblings.findIndex((item) => item.id === category.id);
    const target = siblings[index + direction];
    if (!target) return;
    const reordered = siblings.slice();
    [reordered[index], reordered[index + direction]] = [reordered[index + direction], reordered[index]];
    try {
      await Promise.all(reordered.map((item, itemIndex) => saveCatalogCategory({
        id: item.id,
        parentId: item.parentId || 0,
        categoryName: item.categoryName,
        categoryCode: item.categoryCode,
        sortNo: (itemIndex + 1) * 10,
        status: item.status,
        iconUrl: item.iconUrl,
        remark: item.remark
      })));
      showToast('类目排序已更新', 'success');
      await loadCategories();
    } catch (error) {
      showToast(error instanceof Error ? error.message : '类目排序失败', 'error');
    }
  };

  const toggleExpanded = (id: number) => setExpandedIds((previous) => {
    const next = new Set(previous);
    if (next.has(id)) next.delete(id); else next.add(id);
    return next;
  });

  const renderRows = (nodes: CategoryNode[], depth = 0): React.ReactNode => nodes.map((category) => {
    const hasChildren = category.children.length > 0;
    const expanded = expandedIds.has(category.id);
    // 自身仍为启用，但父链上有停用类目——门户不展示，开关必须置灰，否则管理员会反复点。
    const hiddenByDisabledParent = category.status === 1 && disabledBranchIds.has(category.id);
    const siblings = categories.filter((item) => (item.parentId || 0) === (category.parentId || 0));
    const sortedSiblings = siblings.slice().sort((a, b) => (a.sortNo || 0) - (b.sortNo || 0) || a.id - b.id);
    const position = sortedSiblings.findIndex((item) => item.id === category.id);
    return <React.Fragment key={category.id}>
      <div className="grid grid-cols-[minmax(220px,1fr)_150px_90px_180px] items-center border-b border-slate-100 px-4 py-3 text-sm hover:bg-slate-50">
        <div className="flex min-w-0 items-center gap-2" style={{ paddingLeft: `${depth * 24}px` }}>
          {hasChildren ? <button type="button" onClick={() => toggleExpanded(category.id)} className="rounded p-0.5 text-slate-400 hover:bg-slate-200" aria-label={expanded ? '收起子类目' : '展开子类目'}>{expanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}</button> : <span className="w-5" />}
          <GripVertical className="h-4 w-4 shrink-0 text-slate-300" aria-hidden="true" />
          <FolderTree className={`h-4 w-4 shrink-0 ${depth === 0 ? 'text-blue-500' : 'text-slate-400'}`} />
          <span className={`truncate font-medium ${hiddenByDisabledParent ? 'text-slate-400' : 'text-slate-800'}`}>{category.categoryName}</span>
          {hiddenByDisabledParent && <span className="shrink-0 rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-medium text-slate-500" title="上级类目已停用，门户导航与商品类目选择器都不展示该节点">随上级停用</span>}
          <span className="shrink-0 rounded bg-slate-100 px-1.5 py-0.5 font-mono text-[10px] text-slate-500">{category.categoryCode}</span>
        </div>
        <div className="text-xs text-slate-500">{category.levelNo ? `第${category.levelNo}级` : `第${depth + 1}级`} · {category.parentId ? `父级 #${category.parentId}` : '根类目'}</div>
        <div><span title={hiddenByDisabledParent ? '上级类目已停用，该节点不会出现在门户导航与商品类目选择器中' : undefined} className={`rounded-full px-2 py-1 text-[11px] font-semibold ${category.status === 1 ? (hiddenByDisabledParent ? 'bg-slate-100 text-slate-500' : 'bg-emerald-50 text-emerald-700') : 'bg-slate-100 text-slate-500'}`}>{category.status === 1 ? '启用' : '停用'}</span></div>
        <div className="flex items-center justify-end gap-1">
          <button type="button" onClick={() => void reorder(category, -1)} disabled={position <= 0} className="rounded p-1 text-slate-400 hover:bg-slate-100 hover:text-blue-600 disabled:cursor-not-allowed disabled:opacity-30" title="上移"><ArrowUp className="h-4 w-4" /></button>
          <button type="button" onClick={() => void reorder(category, 1)} disabled={position < 0 || position >= sortedSiblings.length - 1} className="rounded p-1 text-slate-400 hover:bg-slate-100 hover:text-blue-600 disabled:cursor-not-allowed disabled:opacity-30" title="下移"><ArrowDown className="h-4 w-4" /></button>
          <button type="button" onClick={() => openCreate(category.id)} className="rounded p-1 text-slate-400 hover:bg-slate-100 hover:text-blue-600" title="新增子类目"><Plus className="h-4 w-4" /></button>
          <button type="button" onClick={() => openEdit(category)} className="rounded p-1 text-slate-400 hover:bg-slate-100 hover:text-blue-600" title="编辑"><Edit3 className="h-4 w-4" /></button>
          <button type="button" onClick={() => void toggleStatus(category)} disabled={hiddenByDisabledParent} className={`rounded p-1 disabled:cursor-not-allowed disabled:opacity-30 ${category.status === 1 ? 'text-emerald-500 hover:text-amber-600' : 'text-slate-400 hover:text-emerald-600'}`} title={hiddenByDisabledParent ? '上级类目停用中，需先启用上级' : category.status === 1 ? '停用' : '启用'}><Power className="h-4 w-4" /></button>
          <button type="button" onClick={() => void remove(category)} className="rounded p-1 text-slate-400 hover:text-red-600" title="删除"><Trash2 className="h-4 w-4" /></button>
        </div>
      </div>
      {expanded && hasChildren && renderRows(category.children, depth + 1)}
    </React.Fragment>;
  });

  // 弹层打开期间锁住底层文档滚动，避免出现滚动穿透。
  useBodyScrollLock(Boolean(editing));
  return <div className="animate-in fade-in-50 space-y-6 duration-200">
    <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
      <div><div className="flex items-center gap-2"><h2 className="text-xl font-bold tracking-tight text-[#191C1E] md:text-2xl">商品类目维护</h2><span className="rounded-full border border-blue-200 bg-blue-50 px-2 py-0.5 text-xs font-semibold text-blue-700">Catalog</span></div><p className="mt-0.5 text-xs text-[#434655] md:text-sm">维护商品类目层级、编码、排序及上下架状态，最多支持三级类目。</p></div>
      <div className="flex items-center gap-2"><button type="button" onClick={() => void loadCategories()} className="flex h-9 items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 text-xs font-semibold text-slate-600 hover:bg-slate-50"><RefreshCw className="h-4 w-4" />刷新</button><button type="button" onClick={() => openCreate()} className="flex h-9 items-center gap-2 rounded-lg bg-[#2563EB] px-4 text-xs font-semibold text-white shadow-sm hover:bg-blue-700"><Plus className="h-4 w-4" />新增根类目</button></div>
    </div>

    <div className="overflow-hidden rounded-xl border border-[#E2E8F0] bg-white shadow-xs">
      <div className="grid grid-cols-[minmax(220px,1fr)_150px_90px_180px] border-b border-slate-200 bg-slate-50 px-4 py-3 text-xs font-semibold text-slate-500"><div>类目名称 / 编码</div><div>层级</div><div>状态</div><div className="text-right">操作</div></div>
      {loading ? <div className="flex items-center justify-center py-20 text-sm text-slate-500"><Loader2 className="mr-2 h-4 w-4 animate-spin" />正在加载类目…</div> : categories.length === 0 ? <div className="py-20 text-center text-sm text-slate-500"><FolderTree className="mx-auto mb-3 h-9 w-9 text-slate-300" />暂无类目，点击右上角新增根类目。</div> : renderRows(tree)}
    </div>

    {editing && <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"><form onSubmit={submit} className="max-h-[90vh] w-full max-w-xl space-y-4 overflow-y-auto rounded-xl bg-white p-6 shadow-xl"><div className="flex items-center justify-between"><h3 className="text-lg font-bold text-slate-900">{editing.id ? '编辑类目' : '新增类目'}</h3><button type="button" onClick={() => setEditing(null)} className="text-slate-400 hover:text-slate-700" aria-label="关闭"><X className="h-5 w-5" /></button></div><div className="grid grid-cols-1 gap-4 md:grid-cols-2"><label className="text-xs text-slate-600 md:col-span-2">类目名称 *<input required maxLength={128} value={editing.categoryName} onChange={(event) => updateForm('categoryName', event.target.value)} className="mt-1 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-blue-500" /></label><label className="text-xs text-slate-600">类目编码 *<input required maxLength={64} value={editing.categoryCode} onChange={(event) => updateForm('categoryCode', event.target.value.toUpperCase())} className="mt-1 w-full rounded-lg border border-slate-200 px-3 py-2 font-mono text-sm uppercase outline-none focus:border-blue-500" placeholder="例如 ELECTRONICS" /></label><label className="text-xs text-slate-600">父级类目<select value={editing.parentId} onChange={(event) => updateForm('parentId', Number(event.target.value))} className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:border-blue-500"><option value={0}>根类目</option>{parentOptions.map(({ category, depth }) => <option key={category.id} value={category.id}>{'　'.repeat(depth)}{category.categoryName}</option>)}</select></label><label className="text-xs text-slate-600">排序号<input type="number" min={0} value={editing.sortNo} onChange={(event) => updateForm('sortNo', Number(event.target.value))} className="mt-1 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-blue-500" /></label><label className="text-xs text-slate-600">状态<select value={editing.status} onChange={(event) => updateForm('status', Number(event.target.value))} className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:border-blue-500"><option value={1}>启用</option><option value={0}>停用</option></select></label><div className="text-xs text-slate-600 md:col-span-2"><span className="font-medium">图标</span><div className="mt-1 flex items-center gap-3"><div className="flex h-14 w-14 shrink-0 items-center justify-center overflow-hidden rounded-lg border border-slate-200 bg-slate-50">{editing.iconUrl ? <img src={editing.iconUrl} alt="类目图标预览" className="h-full w-full object-cover" /> : <ImageIcon className="h-5 w-5 text-slate-300" />}</div><div className="min-w-0 flex-1"><div className="flex items-center gap-3"><label className={`inline-flex items-center gap-1 text-[11px] font-medium text-blue-600 hover:text-blue-700 cursor-pointer ${iconUploading ? 'opacity-60 pointer-events-none' : ''}`}>{iconUploading ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Upload className="h-3.5 w-3.5" />}{iconUploading ? '上传中…' : '上传图片'}<input type="file" accept="image/jpeg,image/png,image/webp,image/gif" className="hidden" disabled={iconUploading} onChange={(event) => { const file = event.target.files?.[0]; if (file) void handleIconUpload(file); event.target.value = ''; }} /></label>{editing.iconUrl && <button type="button" onClick={() => updateForm('iconUrl', '')} className="text-[11px] text-slate-400 hover:text-rose-600">清除</button>}</div><input maxLength={512} value={editing.iconUrl} onChange={(event) => updateForm('iconUrl', event.target.value)} className="mt-1 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-blue-500" placeholder="可上传图片，也可填写外部图片地址" /></div></div></div><label className="text-xs text-slate-600 md:col-span-2">备注<textarea maxLength={500} rows={3} value={editing.remark} onChange={(event) => updateForm('remark', event.target.value)} className="mt-1 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-blue-500" /></label></div><div className="flex justify-end gap-2 pt-2"><button type="button" onClick={() => setEditing(null)} className="rounded-lg border border-slate-200 px-4 py-2 text-sm text-slate-600">取消</button><button disabled={saving} type="submit" className="flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60">{saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}保存</button></div></form></div>}
  </div>;
};
