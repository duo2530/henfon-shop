import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { AlertCircle, Check, ChevronDown, ChevronRight, ImageOff, RefreshCw, X } from 'lucide-react';
import {
  BackendCatalogCategory,
  BackendCatalogProduct,
  BackendCatalogSku,
  listCatalogCategories,
  listCatalogProducts,
  listCatalogSkus,
} from '../../api/adminApi';
import { Pagination } from './Pagination';
import { useAdmin } from '../../context/AdminContext';
import { useBodyScrollLock } from '../../hooks/useBodyScrollLock';

/** 弹窗确认后返回的选择项，字段可直接填充活动明细行。 */
export interface ProductPickerSelection {
  productId: number;
  skuId?: number;
  productName: string;
  productCode: string;
  skuName?: string;
  skuCode?: string;
  imageUrl?: string;
  originalPrice: number;
  availableStock: number;
}

interface ProductPickerModalProps {
  /** multi：批量添加；single：替换单个明细行，选中项互斥。 */
  mode?: 'multi' | 'single';
  /** 已被其他明细行占用的组合键 `${productId}:${skuId || 0}`，命中则置灰。 */
  excludeKeys?: string[];
  /** 打开时预选中的项，用于「更换」场景回显当前选择。 */
  initialSelection?: ProductPickerSelection[];
  onClose: () => void;
  onConfirm: (selections: ProductPickerSelection[]) => void;
}

const PAGE_SIZE = 10;

/** 组合键：未指定 SKU 时用 0 占位，与后端明细去重规则保持一致。 */
function selectionKey(productId: number, skuId?: number): string {
  return `${productId}:${skuId || 0}`;
}

/** 解析后端用逗号分隔的标签字段。 */
function parseTags(tagsCsv?: string): string[] {
  return (tagsCsv || '')
    .split(',')
    .map((tag) => tag.trim())
    .filter(Boolean)
    .slice(0, 4);
}

/**
 * 管理端通用商品选择弹窗。
 *
 * <p>商品列表走服务端分页与关键字/分类筛选，因此不受单页条数限制；勾选商品行表示
 * 以「不指定 SKU」的方式加入，展开商品行后可进一步勾选具体规格。两部分互不冲突，
 * 可以分别加入，与后端 `productId:skuId` 的明细去重规则一致。</p>
 */
export const ProductPickerModal: React.FC<ProductPickerModalProps> = ({
  mode = 'multi',
  excludeKeys,
  initialSelection,
  onClose,
  onConfirm,
}) => {
  const { showToast } = useAdmin();
  const [keyword, setKeyword] = useState('');
  const [submittedKeyword, setSubmittedKeyword] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [page, setPage] = useState(1);
  const [products, setProducts] = useState<BackendCatalogProduct[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [categories, setCategories] = useState<BackendCatalogCategory[]>([]);
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [skuMap, setSkuMap] = useState<Record<number, BackendCatalogSku[]>>({});
  const [skuLoading, setSkuLoading] = useState<Record<number, boolean>>({});
  const [failedImages, setFailedImages] = useState<Set<number>>(() => new Set());
  const [selection, setSelection] = useState<ProductPickerSelection[]>(() => (initialSelection ? [...initialSelection] : []));

  const excludeSet = useMemo(() => new Set(excludeKeys || []), [excludeKeys]);
  const selectionKeys = useMemo(() => new Set(selection.map((item) => selectionKey(item.productId, item.skuId))), [selection]);
  const totalPages = Math.max(Math.ceil(total / PAGE_SIZE), 1);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await listCatalogProducts({
        current: page,
        size: PAGE_SIZE,
        keyword: submittedKeyword || undefined,
        status: 1,
        categoryId: categoryId ? Number(categoryId) : undefined,
      });
      setProducts(result.records || []);
      setTotal(result.total || 0);
    } catch (requestError) {
      const message = requestError instanceof Error ? requestError.message : '商品加载失败';
      setError(message);
      setProducts([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [page, submittedKeyword, categoryId]);

  useEffect(() => { void load(); }, [load]);

  // 分类只作为筛选项，加载失败不阻塞选品主流程。
  useEffect(() => {
    let cancelled = false;
    void listCatalogCategories()
      .then((list) => { if (!cancelled) setCategories(list || []); })
      .catch(() => { if (!cancelled) setCategories([]); });
    return () => { cancelled = true; };
  }, []);

  /** 展开商品行并懒加载其启用状态的 SKU。 */
  const toggleExpand = async (product: BackendCatalogProduct) => {
    setExpandedId((current) => (current === product.id ? null : product.id));
    if (skuMap[product.id]) return;
    setSkuLoading((previous) => ({ ...previous, [product.id]: true }));
    try {
      const list = await listCatalogSkus(product.id);
      setSkuMap((previous) => ({ ...previous, [product.id]: (list || []).filter((sku) => sku.status === 1) }));
    } catch (requestError) {
      showToast(requestError instanceof Error ? requestError.message : 'SKU 加载失败', 'error');
    } finally {
      setSkuLoading((previous) => ({ ...previous, [product.id]: false }));
    }
  };

  /** 勾选或取消勾选：sku 为空表示选择商品整体。 */
  const toggleSelection = (product: BackendCatalogProduct, sku: BackendCatalogSku | null) => {
    const key = selectionKey(product.id, sku ? sku.id : undefined);
    if (excludeSet.has(key)) return;
    setSelection((previous) => {
      if (previous.some((item) => selectionKey(item.productId, item.skuId) === key)) {
        return previous.filter((item) => selectionKey(item.productId, item.skuId) !== key);
      }
      const next: ProductPickerSelection = {
        productId: product.id,
        skuId: sku ? sku.id : undefined,
        productName: product.productName,
        productCode: product.productCode,
        skuName: sku ? sku.skuName : undefined,
        skuCode: sku ? sku.skuCode : undefined,
        imageUrl: product.mainImageUrl,
        originalPrice: Number(sku ? sku.price : product.price) || 0,
        availableStock: Number(sku ? sku.stock : product.currentStock) || 0,
      };
      return mode === 'single' ? [next] : [...previous, next];
    });
  };

  const submitSearch = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setPage(1);
    setSubmittedKeyword(keyword.trim());
    setExpandedId(null);
  };

  const resetSearch = () => {
    setKeyword('');
    setCategoryId('');
    setPage(1);
    setSubmittedKeyword('');
    setExpandedId(null);
  };

  const confirm = () => {
    if (selection.length === 0) {
      showToast('请至少选择一个商品或规格', 'warning');
      return;
    }
    onConfirm(mode === 'single' ? selection.slice(0, 1) : selection);
  };

  // 组件挂载即代表弹层打开，期间锁住底层文档滚动，避免出现滚动穿透。
  useBodyScrollLock();
  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-950/45 p-4 backdrop-blur-[2px]" role="presentation">
      <div role="dialog" aria-modal="true" aria-label="选择商品" className="flex max-h-[90vh] w-full max-w-5xl flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl animate-in fade-in zoom-in-95 duration-200">
        <div className="flex items-start gap-3 border-b border-slate-100 px-5 py-4">
          <div className="min-w-0 flex-1">
            <h2 className="text-base font-bold text-slate-900">{mode === 'single' ? '更换商品' : '选择商品'}</h2>
            <p className="mt-1 text-xs text-slate-500">仅显示已上架商品。勾选商品表示不指定 SKU，展开后可指定具体规格，两者可分别加入活动。</p>
          </div>
          <button type="button" onClick={onClose} className="rounded-lg p-1 text-slate-400 transition hover:bg-slate-100 hover:text-slate-700" aria-label="关闭">
            <X className="h-4 w-4" />
          </button>
        </div>

        <form onSubmit={submitSearch} className="flex flex-wrap items-end gap-2 border-b border-slate-100 px-5 py-3">
          <label className="flex flex-col gap-1 text-xs text-slate-500">
            商品关键字
            <input
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="商品名称 / 编码 / SKU 编码"
              className="h-8 w-60 rounded-lg border border-slate-200 px-2 text-xs text-slate-700 outline-none transition focus:border-blue-500"
            />
          </label>
          <label className="flex flex-col gap-1 text-xs text-slate-500">
            商品分类
            <select
              value={categoryId}
              onChange={(event) => { setCategoryId(event.target.value); setPage(1); setExpandedId(null); }}
              className="h-8 w-40 rounded-lg border border-slate-200 bg-white px-2 text-xs text-slate-700 outline-none transition focus:border-blue-500"
            >
              <option value="">全部分类</option>
              {categories.map((category) => <option key={category.id} value={category.id}>{category.categoryName}</option>)}
            </select>
          </label>
          <button type="submit" className="h-8 rounded-lg bg-blue-600 px-3 text-xs font-semibold text-white">查询</button>
          <button type="button" onClick={resetSearch} className="h-8 rounded-lg border border-slate-200 px-3 text-xs text-slate-600">重置</button>
          <button type="button" onClick={() => void load()} disabled={loading} className="ml-auto inline-flex h-8 items-center gap-1 rounded-lg border border-slate-200 px-3 text-xs text-slate-600">
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
            刷新
          </button>
        </form>

        <div className="min-h-0 flex-1 overflow-y-auto px-5">
          {error ? (
            <div role="alert" className="my-4 flex items-center justify-between rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              <span className="inline-flex items-center gap-2"><AlertCircle className="h-4 w-4" />{error}</span>
              <button type="button" onClick={() => void load()} className="text-xs font-semibold">重试</button>
            </div>
          ) : (
            <table className="w-full border-collapse text-left">
              <thead className="sticky top-0 z-10 bg-[#F8FAFC] text-[11px] font-semibold uppercase tracking-wider text-slate-500">
                <tr className="border-b border-slate-200">
                  <th className="w-16 py-2.5 pl-3" title="勾选表示不指定 SKU">选择</th>
                  <th className="w-14 py-2.5 px-2">主图</th>
                  <th className="py-2.5 px-2">商品</th>
                  <th className="w-28 py-2.5 px-2">分类</th>
                  <th className="w-28 py-2.5 px-2">售价</th>
                  <th className="w-24 py-2.5 px-2">库存</th>
                  <th className="w-20 py-2.5 px-2">销量</th>
                </tr>
              </thead>
              <tbody className="text-sm text-slate-800">
                {loading && products.length === 0 ? (
                  <tr><td colSpan={7} className="py-14 text-center text-sm text-slate-400">商品加载中...</td></tr>
                ) : products.length === 0 ? (
                  <tr><td colSpan={7} className="py-14 text-center text-sm text-slate-400">没有符合条件的商品，请调整筛选条件。</td></tr>
                ) : products.map((product) => {
                  const expanded = expandedId === product.id;
                  const skus = skuMap[product.id] || [];
                  const wholeKey = selectionKey(product.id);
                  const wholeTaken = excludeSet.has(wholeKey);
                  const tags = parseTags(product.tagsCsv);
                  const lowStock = Number(product.currentStock) <= Number(product.safetyStock);
                  return (
                    <React.Fragment key={product.id}>
                      <tr className={`border-b border-slate-100 transition-colors ${expanded ? 'bg-slate-50/80' : 'hover:bg-slate-50/60'}`}>
                        <td className="py-2.5 pl-3 pr-1">
                          <div className="flex items-center gap-1">
                            <input
                              type="checkbox"
                              checked={selectionKeys.has(wholeKey)}
                              disabled={wholeTaken}
                              onChange={() => toggleSelection(product, null)}
                              title={wholeTaken ? '该商品已在本活动中' : '选择商品整体（不指定 SKU）'}
                              className="h-4 w-4 cursor-pointer accent-blue-600 disabled:cursor-not-allowed disabled:opacity-30"
                            />
                            <button
                              type="button"
                              onClick={() => void toggleExpand(product)}
                              title={expanded ? '收起规格' : '展开规格'}
                              className="rounded p-0.5 text-slate-400 transition hover:bg-slate-200 hover:text-slate-700"
                            >
                              {expanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
                            </button>
                          </div>
                        </td>
                        <td className="py-2.5 px-2">
                          <div className="flex h-10 w-10 items-center justify-center overflow-hidden rounded-lg border border-slate-200 bg-slate-50">
                            {product.mainImageUrl && !failedImages.has(product.id) ? (
                              <img
                                src={product.mainImageUrl}
                                alt={product.productName}
                                loading="lazy"
                                className="h-full w-full object-cover"
                                onError={() => setFailedImages((previous) => new Set(previous).add(product.id))}
                              />
                            ) : (
                              <ImageOff className="h-4 w-4 text-slate-300" />
                            )}
                          </div>
                        </td>
                        <td className="max-w-xs py-2.5 px-2">
                          <div className="line-clamp-1 text-xs font-semibold text-slate-900">{product.productName}</div>
                          <div className="mt-0.5 flex flex-wrap items-center gap-1.5 text-[11px] text-slate-400">
                            <span className="font-mono">{product.productCode}</span>
                            {product.brandName ? <><span>·</span><span>{product.brandName}</span></> : null}
                          </div>
                          {tags.length > 0 && (
                            <div className="mt-1 flex flex-wrap gap-1">
                              {tags.map((tag) => (
                                <span key={tag} className="rounded border border-slate-200 bg-slate-50 px-1.5 py-0.5 text-[10px] text-slate-500">{tag}</span>
                              ))}
                            </div>
                          )}
                        </td>
                        <td className="py-2.5 px-2 text-xs text-slate-600">{product.categoryName || '未分类'}</td>
                        <td className="py-2.5 px-2 text-xs">
                          <div className="font-medium text-slate-800">¥{Number(product.price || 0).toFixed(2)}</div>
                          {product.marketPrice ? <div className="text-[11px] text-slate-400 line-through">¥{Number(product.marketPrice).toFixed(2)}</div> : null}
                        </td>
                        <td className="py-2.5 px-2 text-xs">
                          <span className={lowStock ? 'font-medium text-red-600' : 'text-slate-700'}>{Number(product.currentStock || 0)}</span>
                          {lowStock && <div className="text-[10px] text-red-500">低于安全库存</div>}
                        </td>
                        <td className="py-2.5 px-2 text-xs text-slate-600">{Number(product.salesCount || 0)}</td>
                      </tr>
                      {expanded && (
                        <tr className="border-b border-slate-100 bg-slate-50/40">
                          <td colSpan={7} className="px-3 py-2">
                            <div className="space-y-0.5">
                              <label className={`flex items-center gap-2 rounded-md px-2 py-1.5 text-xs ${wholeTaken ? 'opacity-50' : 'cursor-pointer hover:bg-white'}`}>
                                <input
                                  type="checkbox"
                                  checked={selectionKeys.has(wholeKey)}
                                  disabled={wholeTaken}
                                  onChange={() => toggleSelection(product, null)}
                                  className="h-3.5 w-3.5 cursor-pointer accent-blue-600 disabled:cursor-not-allowed"
                                />
                                <span className="font-medium text-slate-700">商品整体 / 不指定 SKU</span>
                                <span className="text-slate-400">库存 {Number(product.currentStock || 0)}，¥{Number(product.price || 0).toFixed(2)}</span>
                                {wholeTaken && <span className="text-amber-600">已添加</span>}
                              </label>
                              {skuLoading[product.id] ? (
                                <p className="px-2 py-1.5 text-xs text-slate-400">SKU 加载中...</p>
                              ) : skus.length === 0 ? (
                                <p className="px-2 py-1.5 text-xs text-slate-400">该商品没有启用 SKU，将按商品整体参与秒杀。</p>
                              ) : skus.map((sku) => {
                                const key = selectionKey(product.id, sku.id);
                                const taken = excludeSet.has(key);
                                return (
                                  <label key={sku.id} className={`flex items-center gap-2 rounded-md px-2 py-1.5 text-xs ${taken ? 'opacity-50' : 'cursor-pointer hover:bg-white'}`}>
                                    <input
                                      type="checkbox"
                                      checked={selectionKeys.has(key)}
                                      disabled={taken}
                                      onChange={() => toggleSelection(product, sku)}
                                      className="h-3.5 w-3.5 cursor-pointer accent-blue-600 disabled:cursor-not-allowed"
                                    />
                                    <span className="font-medium text-slate-700">{sku.skuName}</span>
                                    <span className="font-mono text-[11px] text-slate-400">{sku.skuCode}</span>
                                    <span className="text-slate-400">库存 {Number(sku.stock || 0)}，¥{Number(sku.price || 0).toFixed(2)}</span>
                                    {taken && <span className="text-amber-600">已添加</span>}
                                  </label>
                                );
                              })}
                            </div>
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>

        <div className="shrink-0 border-t border-slate-100 px-5 py-3">
          <div className="mb-2 flex min-h-[22px] flex-wrap items-center gap-1.5">
            {selection.length === 0 ? (
              <span className="text-xs text-slate-400">尚未选择商品</span>
            ) : selection.map((item) => (
              <span key={selectionKey(item.productId, item.skuId)} className="inline-flex items-center gap-1 rounded-md bg-blue-50 px-1.5 py-0.5 text-[11px] text-blue-700">
                {item.productName}{item.skuName ? ` · ${item.skuName}` : ''}
                <button
                  type="button"
                  onClick={() => setSelection((previous) => previous.filter((candidate) => selectionKey(candidate.productId, candidate.skuId) !== selectionKey(item.productId, item.skuId)))}
                  aria-label={`移除 ${item.productName}`}
                  className="rounded p-0.5 hover:bg-blue-100"
                >
                  <X className="h-3 w-3" />
                </button>
              </span>
            ))}
          </div>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <span className="text-xs text-slate-500">共 {total} 条，已选 {selection.length} 项</span>
            <div className="flex items-center gap-3">
              <Pagination currentPage={page} totalPages={totalPages} onPageChange={setPage} disabled={loading} />
              <button type="button" onClick={onClose} className="h-8 rounded-lg border border-slate-200 px-3 text-xs text-slate-600">取消</button>
              <button type="button" onClick={confirm} disabled={selection.length === 0} className="inline-flex h-8 items-center gap-1.5 rounded-lg bg-blue-600 px-3 text-xs font-semibold text-white transition hover:bg-blue-700 disabled:opacity-50">
                <Check className="h-3.5 w-3.5" />
                确定
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
