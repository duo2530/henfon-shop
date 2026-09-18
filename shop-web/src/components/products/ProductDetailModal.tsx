import React, { useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  CalendarClock,
  CheckCircle2,
  Clock3,
  Edit3,
  FileText,
  Hash,
  Images,
  Layers,
  Loader2,
  MessageSquareQuote,
  PackageSearch,
  Percent,
  Ruler,
  ShieldCheck,
  Star,
  Tag,
  Warehouse,
  X
} from 'lucide-react';
import { useAdmin } from '../../context/AdminContext';
import { PermissionGate } from '../common/PermissionGate';
import { useBodyScrollLock } from '../../hooks/useBodyScrollLock';
import { Product } from '../../types';
import { formatDateTime } from '../../utils/datetime';
import {
  BackendCatalogProduct,
  BackendCatalogProductContent,
  BackendCatalogSku,
  BackendContentReview,
  BackendInventoryStock,
  getCatalogProductContent,
  listCatalogProductsByIds,
  listCatalogSkus,
  listContentReviews,
  listInventoryStocks
} from '../../api/adminApi';

interface ProductDetailModalProps {
  /** 列表行数据，用于接口返回前先渲染骨架内容。 */
  product: Product;
  onClose: () => void;
  onEdit: (product: Product) => void;
  onAdjustStock: (product: Product) => void;
}

/** 商品审核状态徽标样式。 */
const AUDIT_META: Record<number, { label: string; className: string }> = {
  0: { label: '待审核', className: 'border-amber-200 bg-amber-50 text-amber-700' },
  1: { label: '审核通过', className: 'border-[#CEEAD6] bg-[#E6F4EA] text-[#137333]' },
  2: { label: '审核驳回', className: 'border-[#FAD2CF] bg-[#FCE8E6] text-[#C5221F]' }
};

/** 评价审核状态徽标样式，与门户/后台统一的三态口径一致。 */
const REVIEW_META: Record<number, { label: string; className: string }> = {
  0: { label: '待审核', className: 'border-amber-200 bg-amber-50 text-amber-700' },
  1: { label: '已通过', className: 'border-[#CEEAD6] bg-[#E6F4EA] text-[#137333]' },
  2: { label: '未通过', className: 'border-[#FAD2CF] bg-[#FCE8E6] text-[#C5221F]' }
};

/** 解析后端下发的 JSON 对象字符串（SKU 规格），结构不符合预期时返回空对象。 */
function parseAttributes(value?: string): Array<{ name: string; value: string }> {
  if (!value) return [];
  try {
    const parsed = JSON.parse(value);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return [];
    return Object.entries(parsed as Record<string, unknown>).map(([name, item]) => ({ name, value: String(item) }));
  } catch {
    return [];
  }
}

/** 解析评价图 JSON 数组字符串，非法内容一律忽略，避免整块详情渲染失败。 */
function parseImageList(value?: string): string[] {
  if (!value) return [];
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string' && item.length > 0) : [];
  } catch {
    return [];
  }
}

const money = (value?: number | null): string => `¥${Number(value ?? 0).toFixed(2)}`;

/** 详情页统一的分区卡片。 */
const SectionCard: React.FC<{
  title: string;
  icon: React.ReactNode;
  extra?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
}> = ({ title, icon, extra, children, className = '' }) => (
  <section className={`rounded-xl border border-[#E2E8F0] bg-white ${className}`}>
    <header className="flex items-center justify-between gap-3 border-b border-[#E2E8F0] px-4 py-2.5">
      <h4 className="flex items-center gap-2 text-sm font-semibold text-slate-800">
        <span className="text-[#1D4ED8]">{icon}</span>
        {title}
      </h4>
      {extra}
    </header>
    <div className="px-4 py-3">{children}</div>
  </section>
);

/** 指标格：详情页里成组出现的数字都走它，避免每处各写一套字号与留白。 */
const Metric: React.FC<{ label: string; value: React.ReactNode; hint?: React.ReactNode; tone?: 'default' | 'blue' | 'emerald' | 'amber' | 'red' }> = ({
  label,
  value,
  hint,
  tone = 'default'
}) => {
  const toneClass = {
    default: 'text-slate-900',
    blue: 'text-[#1D4ED8]',
    emerald: 'text-emerald-700',
    amber: 'text-amber-600',
    red: 'text-[#C5221F]'
  }[tone];
  return (
    <div className="rounded-lg border border-[#E2E8F0] bg-[#F8FAFC] px-3 py-2.5">
      <div className="text-[11px] text-slate-500">{label}</div>
      <div className={`mt-1 text-lg font-bold leading-tight ${toneClass}`}>{value}</div>
      {hint ? <div className="mt-0.5 text-[11px] text-slate-400">{hint}</div> : null}
    </div>
  );
};

const EmptyHint: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <div className="flex items-center gap-2 rounded-lg border border-dashed border-[#E2E8F0] bg-[#F8FAFC] px-3 py-2.5 text-xs text-slate-400">
    <FileText className="h-4 w-4 shrink-0" />
    {children}
  </div>
);

const Stars: React.FC<{ value: number }> = ({ value }) => (
  <div className="flex items-center gap-0.5" aria-label={`评分 ${value} 分`}>
    {[1, 2, 3, 4, 5].map((index) => (
      <Star
        key={index}
        className={`h-3.5 w-3.5 ${index <= Math.round(value) ? 'fill-amber-400 text-amber-400' : 'text-slate-300'}`}
      />
    ))}
  </div>
);

/**
 * 商品详情弹层。
 *
 * 数据全部来自服务端：商品主档、图文内容、SKU 列表、库存台账、买家评价分别在挂载时并发拉取，
 * 单个接口失败只影响对应分区，不阻断其余内容展示。
 */
export const ProductDetailModal: React.FC<ProductDetailModalProps> = ({ product, onClose, onEdit, onAdjustStock }) => {
  const { hasPermission } = useAdmin();
  // 弹层内容高、自带滚动区，必须同时锁住底层文档，否则滚到底后底层页面会跟着滚。
  useBodyScrollLock();

  const productId = Number(product.id);
  const canLoadDetail = Number.isFinite(productId);

  const [loading, setLoading] = useState(canLoadDetail);
  const [backend, setBackend] = useState<BackendCatalogProduct | null>(null);
  const [content, setContent] = useState<BackendCatalogProductContent | null>(null);
  const [skus, setSkus] = useState<BackendCatalogSku[] | null>(null);
  const [stocks, setStocks] = useState<BackendInventoryStock[] | null>(null);
  const [reviews, setReviews] = useState<BackendContentReview[] | null>(null);
  const [reviewTotal, setReviewTotal] = useState<number | null>(null);
  const [pendingReviewTotal, setPendingReviewTotal] = useState<number | null>(null);
  const [activeImage, setActiveImage] = useState(0);

  const canQueryStock = hasPermission('inventory:stock:query');

  useEffect(() => {
    if (!canLoadDetail) return undefined;
    let cancelled = false;
    (async () => {
      const [productResult, contentResult, skuResult, reviewResult, pendingResult] = await Promise.allSettled([
        listCatalogProductsByIds([productId]),
        getCatalogProductContent(productId),
        listCatalogSkus(productId),
        listContentReviews({ productId, current: 1, size: 5 }),
        listContentReviews({ productId, status: 0, current: 1, size: 1 })
      ]);
      if (cancelled) return;
      if (productResult.status === 'fulfilled') setBackend(productResult.value?.[0] ?? null);
      if (contentResult.status === 'fulfilled') setContent(contentResult.value);
      if (skuResult.status === 'fulfilled') setSkus(skuResult.value ?? []);
      if (reviewResult.status === 'fulfilled') {
        setReviews(reviewResult.value?.records ?? []);
        setReviewTotal(reviewResult.value?.total ?? 0);
      }
      if (pendingResult.status === 'fulfilled') setPendingReviewTotal(pendingResult.value?.total ?? 0);
      setLoading(false);
    })();
    return () => {
      cancelled = true;
    };
  }, [canLoadDetail, productId]);

  useEffect(() => {
    if (!canQueryStock) {
      setStocks([]);
      return undefined;
    }
    if (skus === null) return undefined;
    if (skus.length === 0) {
      setStocks([]);
      return undefined;
    }
    let cancelled = false;
    (async () => {
      // 单商品 SKU 数量有限（当前演示数据为 1 条），限制批量上限避免异常数据放大请求量。
      const results = await Promise.allSettled(
        skus.slice(0, 10).map((sku) => listInventoryStocks({ skuId: sku.id, current: 1, size: 20 }))
      );
      if (cancelled) return;
      const merged: BackendInventoryStock[] = [];
      results.forEach((result) => {
        if (result.status === 'fulfilled') merged.push(...(result.value?.records ?? []));
      });
      setStocks(merged);
    })();
    return () => {
      cancelled = true;
    };
  }, [canQueryStock, skus]);

  const images = useMemo(() => {
    const list: string[] = [];
    const push = (url?: string) => {
      const value = (url || '').trim();
      if (value && !list.includes(value)) list.push(value);
    };
    push(backend?.mainImageUrl || product.imageUrl);
    (content?.media || []).forEach((item) => {
      if (item.mediaType !== 'VIDEO') push(item.mediaUrl);
    });
    return list;
  }, [backend?.mainImageUrl, content, product.imageUrl]);

  const videos = useMemo(() => (content?.media || []).filter((item) => item.mediaType === 'VIDEO' && item.mediaUrl), [content]);

  const price = Number(backend?.price ?? product.price ?? 0);
  const marketPrice = Number(backend?.marketPrice ?? product.originalPrice ?? 0);
  const costPrice = Number(backend?.costPrice ?? product.costPrice ?? 0);
  const currentStock = Number(backend?.currentStock ?? product.stock ?? 0);
  const safetyStock = Number(backend?.safetyStock ?? product.safetyStock ?? 0);
  const salesCount = Number(backend?.salesCount ?? product.salesCount ?? 0);
  const grossProfit = price - costPrice;
  const marginRate = price > 0 ? grossProfit / price : 0;
  const costRatio = price > 0 ? Math.min(1, Math.max(0, costPrice / price)) : 0;

  const stockHealth = currentStock === 0
    ? { label: '已售罄', className: 'border-[#FAD2CF] bg-[#FCE8E6] text-[#C5221F]' }
    : currentStock <= safetyStock
      ? { label: '低于安全线', className: 'border-amber-200 bg-amber-50 text-amber-700' }
      : { label: '库存充足', className: 'border-[#CEEAD6] bg-[#E6F4EA] text-[#137333]' };

  const ledger = useMemo(() => {
    if (!stocks || stocks.length === 0) return null;
    // 同一仓库会因多 SKU 出现多行，仓库数按 warehouseId 去重统计。
    const warehouseIds = new Set<number>();
    const totals = stocks.reduce(
      (total, item) => {
        warehouseIds.add(Number(item.warehouseId));
        return {
          available: total.available + Number(item.availableStock || 0),
          locked: total.locked + Number(item.lockedStock || 0),
          sold: total.sold + Number(item.soldStock || 0)
        };
      },
      { available: 0, locked: 0, sold: 0 }
    );
    return { ...totals, warehouses: warehouseIds.size };
  }, [stocks]);

  const tags = product.tags ?? [];
  const auditMeta = backend?.auditStatus !== undefined ? AUDIT_META[backend.auditStatus] : undefined;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-xs">
      <div
        role="dialog"
        aria-modal="true"
        aria-label={`${product.name} 商品详情`}
        className="flex max-h-[92vh] w-full max-w-5xl flex-col overflow-hidden rounded-2xl border border-[#E2E8F0] bg-white shadow-2xl"
      >
        {/* 头部：身份信息与快捷操作 */}
        <header className="flex items-start justify-between gap-4 border-b border-[#E2E8F0] bg-gradient-to-r from-[#EFF6FF] via-white to-white px-6 py-4">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <span className="rounded bg-[#EFF6FF] px-2 py-0.5 text-xs font-semibold text-[#1D4ED8]">{product.categoryName}</span>
              {product.status === 'active' ? (
                <span className="inline-flex items-center rounded-full border border-[#CEEAD6] bg-[#E6F4EA] px-2 py-0.5 text-xs font-semibold text-[#137333]">
                  <span className="mr-1.5 h-1.5 w-1.5 rounded-full bg-[#137333]" />
                  上架中
                </span>
              ) : (
                <span className="inline-flex items-center rounded-full border border-[#FAD2CF] bg-[#FCE8E6] px-2 py-0.5 text-xs font-semibold text-[#C5221F]">
                  <span className="mr-1.5 h-1.5 w-1.5 rounded-full bg-[#C5221F]" />
                  已下架
                </span>
              )}
              {auditMeta ? (
                <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-semibold ${auditMeta.className}`}>
                  {auditMeta.label}
                </span>
              ) : null}
              <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-semibold ${stockHealth.className}`}>
                {stockHealth.label}
              </span>
            </div>
            <h3 className="mt-2 truncate text-lg font-bold text-slate-900">{product.name}</h3>
            <div className="mt-1.5 flex flex-wrap items-center gap-x-5 gap-y-1 text-xs text-slate-500">
              <span className="flex items-center gap-1 font-mono">
                <Hash className="h-3.5 w-3.5 text-slate-400" />
                {backend?.productCode || product.productCode || '—'}
              </span>
              <span className="flex items-center gap-1 font-mono">
                <Layers className="h-3.5 w-3.5 text-slate-400" />
                默认 SKU {backend?.defaultSkuCode || product.sku || '—'}
              </span>
              {backend?.brandName ? <span>品牌 {backend.brandName}</span> : null}
              {backend?.weightGram !== undefined ? (
                <span className="flex items-center gap-1">
                  <Ruler className="h-3.5 w-3.5 text-slate-400" />
                  计费重量 {backend.weightGram} g
                </span>
              ) : null}
            </div>
          </div>

          <div className="flex shrink-0 items-center gap-2">
            <PermissionGate permission="product:edit">
              <button
                type="button"
                onClick={() => onEdit(product)}
                className="inline-flex items-center gap-1.5 rounded-lg border border-[#E2E8F0] bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 transition-colors hover:border-[#BFDBFE] hover:text-[#1D4ED8]"
              >
                <Edit3 className="h-3.5 w-3.5" />
                编辑商品
              </button>
            </PermissionGate>
            <PermissionGate permission="inventory:stock:adjust">
              <button
                type="button"
                onClick={() => onAdjustStock(product)}
                className="inline-flex items-center gap-1.5 rounded-lg border border-[#E2E8F0] bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 transition-colors hover:border-[#BFDBFE] hover:text-[#1D4ED8]"
              >
                <PackageSearch className="h-3.5 w-3.5" />
                库存调整
              </button>
            </PermissionGate>
            <button
              type="button"
              onClick={onClose}
              aria-label="关闭详情"
              className="rounded-lg p-1.5 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
            >
              <X className="h-5 w-5" />
            </button>
          </div>
        </header>

        {/* 主体：左图册 + 右指标与描述，下方为图文、SKU、评价 */}
        <div className="flex-1 space-y-4 overflow-y-auto overscroll-contain bg-[#F8FAFC] px-6 py-5">
          <div className="grid gap-4 lg:grid-cols-[320px_minmax(0,1fr)]">
            <SectionCard
              title="商品图册"
              icon={<Images className="h-4 w-4" />}
              extra={<span className="text-[11px] text-slate-400">{images.length + videos.length} 项素材</span>}
            >
              {images.length === 0 ? (
                <div className="flex aspect-square items-center justify-center rounded-lg border border-dashed border-[#E2E8F0] bg-[#F8FAFC] px-4 text-center text-xs text-slate-400">
                  {loading ? '正在加载商品图册…' : '未维护商品图片，可在编辑弹层上传主图与媒体'}
                </div>
              ) : (
                <>
                  <div className="aspect-square overflow-hidden rounded-lg border border-[#E2E8F0] bg-white">
                    <img
                      src={images[Math.min(activeImage, images.length - 1)]}
                      alt={product.name}
                      className="h-full w-full object-cover"
                    />
                  </div>
                  {images.length > 1 ? (
                    <div className="mt-2 grid grid-cols-5 gap-1.5">
                      {images.map((url, index) => (
                        <button
                          key={url}
                          type="button"
                          onClick={() => setActiveImage(index)}
                          aria-label={`查看第 ${index + 1} 张图片`}
                          className={`aspect-square overflow-hidden rounded border transition-colors ${
                            index === activeImage ? 'border-[#2563EB] ring-2 ring-[#BFDBFE]' : 'border-[#E2E8F0] hover:border-[#BFDBFE]'
                          }`}
                        >
                          <img src={url} alt="" className="h-full w-full object-cover" />
                        </button>
                      ))}
                    </div>
                  ) : null}
                </>
              )}

              {videos.length > 0 ? (
                <div className="mt-3 space-y-2">
                  {videos.map((video) => (
                    <video
                      key={video.id}
                      src={video.mediaUrl}
                      controls
                      className="w-full rounded-lg border border-[#E2E8F0] bg-black"
                    />
                  ))}
                </div>
              ) : null}

              {tags.length > 0 ? (
                <div className="mt-3">
                  <div className="mb-1.5 flex items-center gap-1 text-[11px] text-slate-400">
                    <Tag className="h-3.5 w-3.5" />
                    商品标签
                  </div>
                  <div className="flex flex-wrap gap-1">
                    {tags.map((tag) => (
                      <span key={tag} className="rounded-full bg-[#EFF6FF] px-2 py-0.5 text-xs font-medium text-[#1D4ED8]">
                        #{tag}
                      </span>
                    ))}
                  </div>
                </div>
              ) : null}

              <div className="mt-3 grid grid-cols-2 gap-2 border-t border-[#E2E8F0] pt-3 text-xs">
                <div>
                  <div className="text-[11px] text-slate-400">上架时间</div>
                  <div className="mt-0.5 font-semibold text-slate-800">{formatDateTime(backend?.createdAt ?? product.createdAt)}</div>
                </div>
                <div>
                  <div className="text-[11px] text-slate-400">累计成交</div>
                  <div className="mt-0.5 font-semibold text-slate-800">{salesCount} 件</div>
                </div>
              </div>
            </SectionCard>

            <div className="space-y-4">
              <SectionCard
                title="价格与利润"
                icon={<Percent className="h-4 w-4" />}
                extra={<span className="text-[11px] text-slate-400">单位：人民币</span>}
              >
                <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-4">
                  <Metric label="零售标价" value={money(price)} tone="blue" />
                  <Metric label="市场参考价" value={money(marketPrice)} hint={price > 0 && marketPrice > price ? `高出 ${money(marketPrice - price)}` : '与标价一致'} />
                  <PermissionGate
                    permission="product:cost:view"
                    fallback={<Metric label="采购成本" value="已隐藏" hint="缺少成本查看权限" />}
                  >
                    <Metric label="采购成本" value={money(costPrice)} />
                  </PermissionGate>
                  <PermissionGate
                    permission="product:cost:view"
                    fallback={<Metric label="毛利 / 毛利率" value="已隐藏" hint="缺少成本查看权限" />}
                  >
                    <Metric
                      label="毛利 / 毛利率"
                      value={money(grossProfit)}
                      tone={grossProfit >= 0 ? 'emerald' : 'red'}
                      hint={`毛利率 ${(marginRate * 100).toFixed(1)}%`}
                    />
                  </PermissionGate>
                </div>

                <PermissionGate permission="product:cost:view">
                  <div className="mt-3">
                    <div className="mb-1 flex items-center justify-between text-[11px] text-slate-500">
                      <span>成本占比</span>
                      <span className="font-mono">{(costRatio * 100).toFixed(1)}%</span>
                    </div>
                    <div className="flex h-2 overflow-hidden rounded-full bg-[#E2E8F0]">
                      <div className="h-full bg-slate-400" style={{ width: `${costRatio * 100}%` }} />
                      <div className="h-full bg-emerald-500" style={{ width: `${Math.max(0, 100 - costRatio * 100)}%` }} />
                    </div>
                    <div className="mt-1 flex items-center gap-4 text-[11px] text-slate-400">
                      <span className="flex items-center gap-1">
                        <span className="h-2 w-2 rounded-sm bg-slate-400" /> 采购成本
                      </span>
                      <span className="flex items-center gap-1">
                        <span className="h-2 w-2 rounded-sm bg-emerald-500" /> 毛利空间
                      </span>
                    </div>
                  </div>
                </PermissionGate>
              </SectionCard>

              <SectionCard
                title="库存概览"
                icon={<Warehouse className="h-4 w-4" />}
                extra={<span className="text-[11px] text-slate-400">商品快照 + 库存台账</span>}
              >
                <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-4">
                  <Metric label="商品快照库存" value={`${currentStock} 件`} tone="blue" hint={`安全线 ${safetyStock} 件`} />
                  <Metric
                    label="台账可用"
                    value={ledger ? `${ledger.available} 件` : '—'}
                    hint={ledger ? `${ledger.warehouses} 个仓库` : '未建立库存台账'}
                  />
                  <Metric label="台账锁定" value={ledger ? `${ledger.locked} 件` : '—'} hint="下单预占未释放" />
                  <Metric label="台账已售" value={ledger ? `${ledger.sold} 件` : '—'} hint="发货后转出库" />
                </div>

                {ledger && ledger.available <= safetyStock ? (
                  <div className="mt-3 flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-700">
                    <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                    台账可用量 {ledger.available} 件已触及安全线 {safetyStock} 件，建议补货或核对预占订单。
                  </div>
                ) : null}

                {stocks !== null && stocks.length === 0 ? (
                  <div className="mt-3 flex items-start gap-2 rounded-lg border border-[#E2E8F0] bg-[#F8FAFC] px-3 py-2 text-xs text-slate-500">
                    <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-slate-400" />
                    {canQueryStock
                      ? '该商品在库存台账中没有记录。下单卡口读取的是 inventory_stock，与上方商品快照库存不同源。'
                      : '当前账号没有库存查询权限，无法展示台账数据。'}
                  </div>
                ) : null}
              </SectionCard>

              <SectionCard title="商品描述" icon={<FileText className="h-4 w-4" />}>
                <p className="whitespace-pre-line text-sm leading-relaxed text-slate-700">
                  {backend?.description || backend?.shortDescription || product.description || '未维护商品描述。'}
                </p>
                {backend?.shortDescription && backend?.description && backend.shortDescription !== backend.description ? (
                  <p className="mt-2 rounded-lg bg-[#F8FAFC] px-3 py-2 text-xs text-slate-500">卖点摘要：{backend.shortDescription}</p>
                ) : null}
                {backend?.remark ? (
                  <p className="mt-2 text-xs text-slate-500">商品备注：{backend.remark}</p>
                ) : null}
              </SectionCard>
            </div>
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <SectionCard
              title="核心卖点"
              icon={<CheckCircle2 className="h-4 w-4" />}
              extra={<span className="text-[11px] text-slate-400">{content?.features?.length ?? 0} 条</span>}
            >
              {content && content.features.length > 0 ? (
                <ul className="space-y-1.5">
                  {content.features.map((feature) => (
                    <li key={feature.id} className="flex items-start gap-2 text-sm text-slate-700">
                      <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-500" />
                      {feature.featureText}
                    </li>
                  ))}
                </ul>
              ) : (
                <EmptyHint>{loading ? '正在加载卖点…' : '该商品还未维护核心卖点，可在编辑弹层的「图文与媒体」中补充。'}</EmptyHint>
              )}
            </SectionCard>

            <SectionCard
              title="规格参数"
              icon={<Ruler className="h-4 w-4" />}
              extra={<span className="text-[11px] text-slate-400">{content?.specs?.length ?? 0} 项</span>}
            >
              {content && content.specs.length > 0 ? (
                <dl className="grid grid-cols-1 gap-x-4 gap-y-2 sm:grid-cols-2">
                  {content.specs.map((spec) => (
                    <div key={spec.id} className="flex items-baseline justify-between gap-3 border-b border-dashed border-[#E2E8F0] pb-1.5">
                      <dt className="text-xs text-slate-500">{spec.specName}</dt>
                      <dd className="text-right text-xs font-semibold text-slate-800">{spec.specValue}</dd>
                    </div>
                  ))}
                </dl>
              ) : (
                <EmptyHint>{loading ? '正在加载参数…' : '该商品还未维护规格参数。'}</EmptyHint>
              )}
            </SectionCard>
          </div>

          <SectionCard
            title="SKU 明细"
            icon={<Layers className="h-4 w-4" />}
            extra={<span className="text-[11px] text-slate-400">{skus?.length ?? 0} 个 SKU</span>}
          >
            {skus === null ? (
              <div className="flex items-center gap-2 py-6 text-xs text-slate-400">
                <Loader2 className="h-4 w-4 animate-spin" />
                正在加载 SKU…
              </div>
            ) : skus.length === 0 ? (
              <EmptyHint>该商品尚未维护 SKU，门户按商品主档价格售卖。</EmptyHint>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[720px] text-xs">
                  <thead>
                    <tr className="text-left text-[11px] text-slate-500">
                      <th className="pb-2 font-medium">SKU 编码</th>
                      <th className="pb-2 font-medium">SKU 名称</th>
                      <th className="pb-2 font-medium">规格</th>
                      <th className="pb-2 text-right font-medium">售价</th>
                      <th className="pb-2 text-right font-medium">成本</th>
                      <th className="pb-2 text-right font-medium">库存</th>
                      <th className="pb-2 text-right font-medium">台账可用</th>
                      <th className="pb-2 text-center font-medium">状态</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#E2E8F0]">
                    {skus.map((sku) => {
                      const attributes = parseAttributes(sku.attributesJson);
                      const skuLedger = (stocks || []).filter((item) => item.skuId === sku.id);
                      const skuAvailable = skuLedger.length > 0
                        ? skuLedger.reduce((total, item) => total + Number(item.availableStock || 0), 0)
                        : null;
                      const lowStock = Number(sku.stock || 0) <= Number(sku.safetyStock || 0);
                      return (
                        <tr key={sku.id} className="text-slate-700">
                          <td className="py-2.5 pr-3 font-mono text-[11px] text-slate-500">{sku.skuCode}</td>
                          <td className="py-2.5 pr-3 font-medium text-slate-900">{sku.skuName}</td>
                          <td className="py-2.5 pr-3">
                            {attributes.length > 0 ? (
                              <div className="flex flex-wrap gap-1">
                                {attributes.map((attribute) => (
                                  <span key={attribute.name} className="rounded bg-[#F1F5F9] px-1.5 py-0.5 text-[11px] text-slate-600">
                                    {attribute.name}：{attribute.value}
                                  </span>
                                ))}
                              </div>
                            ) : (
                              <span className="text-slate-400">—</span>
                            )}
                          </td>
                          <td className="py-2.5 pr-3 text-right font-semibold">{money(sku.price)}</td>
                          <td className="py-2.5 pr-3 text-right">{money(sku.costPrice)}</td>
                          <td className={`py-2.5 pr-3 text-right font-semibold ${lowStock ? 'text-amber-600' : 'text-slate-800'}`}>
                            {Number(sku.stock || 0)}
                            <span className="ml-1 text-[11px] font-normal text-slate-400">/ 安全 {Number(sku.safetyStock || 0)}</span>
                          </td>
                          <td className="py-2.5 pr-3 text-right">{skuAvailable === null ? <span className="text-slate-400">—</span> : skuAvailable}</td>
                          <td className="py-2.5 text-center">
                            {Number(sku.status) === 1 ? (
                              <span className="rounded-full bg-[#E6F4EA] px-2 py-0.5 text-[11px] font-semibold text-[#137333]">启用</span>
                            ) : (
                              <span className="rounded-full bg-[#FCE8E6] px-2 py-0.5 text-[11px] font-semibold text-[#C5221F]">停用</span>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </SectionCard>

          <SectionCard
            title="买家评价"
            icon={<MessageSquareQuote className="h-4 w-4" />}
            extra={
              <div className="flex items-center gap-3 text-[11px]">
                <span className="text-slate-500">
                  共 <strong className="text-slate-800">{reviewTotal ?? '—'}</strong> 条
                </span>
                {pendingReviewTotal ? (
                  <span className="rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 font-semibold text-amber-700">
                    待审核 {pendingReviewTotal}
                  </span>
                ) : null}
              </div>
            }
          >
            {reviews === null ? (
              <div className="flex items-center gap-2 py-6 text-xs text-slate-400">
                <Loader2 className="h-4 w-4 animate-spin" />
                正在加载评价…
              </div>
            ) : reviews.length === 0 ? (
              <EmptyHint>该商品暂无买家评价。</EmptyHint>
            ) : (
              <ul className="space-y-3">
                {reviews.map((review) => {
                  const meta = REVIEW_META[review.status] ?? REVIEW_META[0];
                  const reviewImages = parseImageList(review.imageUrls);
                  return (
                    <li key={review.id} className="rounded-lg border border-[#E2E8F0] px-3 py-2.5">
                      <div className="flex items-start justify-between gap-3">
                        <div className="flex items-center gap-2">
                          <span className="text-xs font-semibold text-slate-800">{review.memberName || '匿名会员'}</span>
                          <Stars value={Number(review.rating || 0)} />
                          {review.variantSummary ? <span className="text-[11px] text-slate-400">{review.variantSummary}</span> : null}
                        </div>
                        <div className="flex shrink-0 items-center gap-2">
                          <span className={`rounded-full border px-2 py-0.5 text-[11px] font-semibold ${meta.className}`}>{meta.label}</span>
                          <span className="flex items-center gap-1 text-[11px] text-slate-400">
                            <Clock3 className="h-3 w-3" />
                            {formatDateTime(review.createdAt)}
                          </span>
                        </div>
                      </div>
                      <p className="mt-2 text-sm leading-relaxed text-slate-700">{review.reviewContent}</p>
                      {reviewImages.length > 0 ? (
                        <div className="mt-2 flex flex-wrap gap-1.5">
                          {reviewImages.map((url) => (
                            <img
                              key={url}
                              src={url}
                              alt="评价图片"
                              className="h-14 w-14 rounded border border-[#E2E8F0] object-cover"
                            />
                          ))}
                        </div>
                      ) : null}
                      {review.replyContent ? (
                        <div className="mt-2 rounded-lg bg-[#F8FAFC] px-3 py-2 text-xs text-slate-600">
                          <span className="font-semibold text-slate-700">商家回复：</span>
                          {review.replyContent}
                        </div>
                      ) : null}
                    </li>
                  );
                })}
              </ul>
            )}
          </SectionCard>
        </div>

        {/* 底部：时间轴与收尾操作 */}
        <footer className="flex flex-wrap items-center justify-between gap-3 border-t border-[#E2E8F0] bg-white px-6 py-3 text-xs text-slate-500">
          <div className="flex flex-wrap items-center gap-x-5 gap-y-1">
            <span className="flex items-center gap-1">
              <CalendarClock className="h-3.5 w-3.5 text-slate-400" />
              创建 {formatDateTime(backend?.createdAt ?? product.createdAt)}
            </span>
            <span className="flex items-center gap-1">
              <Clock3 className="h-3.5 w-3.5 text-slate-400" />
              更新 {formatDateTime(backend?.updatedAt)}
            </span>
            {backend?.auditRemark ? <span>审核备注：{backend.auditRemark}</span> : null}
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg border border-[#E2E8F0] bg-white px-3.5 py-1.5 text-xs font-medium text-slate-700 transition-colors hover:bg-slate-50"
            >
              关闭
            </button>
            <PermissionGate permission="product:edit">
              <button
                type="button"
                onClick={() => onEdit(product)}
                className="rounded-lg bg-[#2563EB] px-4 py-1.5 text-xs font-semibold text-white shadow-xs transition-colors hover:bg-[#1D4ED8]"
              >
                编辑此商品
              </button>
            </PermissionGate>
          </div>
        </footer>
      </div>
    </div>
  );
};
