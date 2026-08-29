import React, { useState, useMemo, useEffect } from 'react';
import { Product } from '../types/ecommerce';
import {
  X,
  Scale,
  ShoppingBag,
  Star,
  Check,
  Sparkles,
  ShieldCheck,
  Truck,
  SlidersHorizontal,
  Plus,
  Eye,
  AlertCircle,
  BarChart3,
  Table,
  Palette,
  CheckSquare,
  Square,
  ArrowRight,
  Layers,
  RotateCcw,
} from 'lucide-react';
import { CompareChartsView } from './CompareChartsView';

interface CompareModalProps {
  isOpen: boolean;
  products: Product[];
  onClose: () => void;
  onRemoveProduct: (productId: string) => void;
  onAddToCart: (
    product: Product,
    selectedVariants?: Record<string, string>,
    quantity?: number
  ) => void;
  onBatchAddToCart?: (
    items: {
      product: Product;
      selectedVariants?: Record<string, string>;
      quantity?: number;
    }[]
  ) => void;
  onQuickView: (product: Product) => void;
  onClearAll: () => void;
  lastComparedProducts?: Product[];
  onRestoreLastCompare?: () => void;
}

export const CompareModal: React.FC<CompareModalProps> = ({
  isOpen,
  products = [],
  onClose,
  onRemoveProduct,
  onAddToCart,
  onBatchAddToCart,
  onQuickView,
  onClearAll,
  lastComparedProducts = [],
  onRestoreLastCompare,
}) => {
  const [activeTab, setActiveTab] = useState<'table' | 'charts'>('table');
  const [onlyDifferences, setOnlyDifferences] = useState<boolean>(false);

  // Batch selection state: which products are checked for batch adding to cart (default to all selected)
  const [selectedForBatch, setSelectedForBatch] = useState<Record<string, boolean>>({});

  // Customized/selected variant option overrides per product: { [productId]: { [variantName]: selectedLabel } }
  const [customVariants, setCustomVariants] = useState<Record<string, Record<string, string>>>({});

  // Added animation state for individual items
  const [addedItemIds, setAddedItemIds] = useState<Record<string, boolean>>({});
  // Added animation state for batch CTA button
  const [isBatchAdding, setIsBatchAdding] = useState<boolean>(false);
  const [batchSuccess, setBatchSuccess] = useState<boolean>(false);

  // Initialize and sync batch selection when products change
  useEffect(() => {
    setSelectedForBatch((prev) => {
      const updated: Record<string, boolean> = {};
      products.forEach((p) => {
        // Keep previous choice if exists, otherwise default to true
        updated[p.id] = prev[p.id] !== undefined ? prev[p.id] : true;
      });
      return updated;
    });

    // Initialize default variants
    setCustomVariants((prev) => {
      const updated: Record<string, Record<string, string>> = { ...prev };
      products.forEach((p) => {
        if (!updated[p.id] && p.variants) {
          const defaults: Record<string, string> = {};
          p.variants.forEach((v) => {
            defaults[v.name] = v.options[0]?.label || '';
          });
          updated[p.id] = defaults;
        }
      });
      return updated;
    });
  }, [products]);

  // Helper to resolve effective variants for a product (defaults to first option of each variant)
  const getEffectiveVariants = (product: Product): Record<string, string> => {
    const resolved: Record<string, string> = {};
    if (product.variants) {
      product.variants.forEach((v) => {
        resolved[v.name] =
          customVariants[product.id]?.[v.name] || v.options[0]?.label || '';
      });
    }
    return resolved;
  };

  // Helper to calculate total price for a product including variant price modifiers
  const getProductFinalPrice = (product: Product): number => {
    let finalPrice = product.price;
    const variants = getEffectiveVariants(product);
    if (product.variants) {
      product.variants.forEach((v) => {
        const chosenLabel = variants[v.name];
        const opt = v.options.find((o) => o.label === chosenLabel);
        if (opt?.priceModifier) {
          finalPrice += opt.priceModifier;
        }
      });
    }
    return finalPrice;
  };

  // Toggle single product selection for batch cart
  const handleToggleProductSelection = (productId: string) => {
    setSelectedForBatch((prev) => ({
      ...prev,
      [productId]: !prev[productId],
    }));
  };

  // Select all or deselect all
  const handleToggleSelectAll = (checked: boolean) => {
    const next: Record<string, boolean> = {};
    products.forEach((p) => {
      next[p.id] = checked;
    });
    setSelectedForBatch(next);
  };

  // Change a variant option for a product
  const handleSelectVariantOption = (
    productId: string,
    variantName: string,
    optionLabel: string
  ) => {
    setCustomVariants((prev) => ({
      ...prev,
      [productId]: {
        ...(prev[productId] || {}),
        [variantName]: optionLabel,
      },
    }));
  };

  // Single product add to cart with animation feedback
  const handleSingleAddToCart = (product: Product) => {
    const effectiveVariants = getEffectiveVariants(product);
    onAddToCart(product, effectiveVariants, 1);

    setAddedItemIds((prev) => ({ ...prev, [product.id]: true }));
    setTimeout(() => {
      setAddedItemIds((prev) => ({ ...prev, [product.id]: false }));
    }, 1800);
  };

  // Batch add selected products to cart
  const handleBatchAdd = () => {
    const selectedProducts = products.filter((p) => selectedForBatch[p.id]);
    if (selectedProducts.length === 0) return;

    setIsBatchAdding(true);

    const batchPayload = selectedProducts.map((product) => ({
      product,
      selectedVariants: getEffectiveVariants(product),
      quantity: 1,
    }));

    if (onBatchAddToCart) {
      onBatchAddToCart(batchPayload);
    } else {
      batchPayload.forEach((item) => {
        onAddToCart(item.product, item.selectedVariants, item.quantity);
      });
    }

    setIsBatchAdding(false);
    setBatchSuccess(true);
    setTimeout(() => {
      setBatchSuccess(false);
    }, 2500);
  };

  // Selected products calculation
  const selectedProducts = useMemo(() => {
    return products.filter((p) => selectedForBatch[p.id]);
  }, [products, selectedForBatch]);

  const totalSelectedPrice = useMemo(() => {
    return selectedProducts.reduce((sum, p) => sum + getProductFinalPrice(p), 0);
  }, [selectedProducts, customVariants]);

  const totalSelectedOriginalPrice = useMemo(() => {
    return selectedProducts.reduce((sum, p) => sum + p.originalPrice, 0);
  }, [selectedProducts]);

  const totalSavedPrice = totalSelectedOriginalPrice - totalSelectedPrice;

  // Helper to check if any general property differs across the compared products
  const isPropDifferent = (getter: (p: Product) => any) => {
    if (products.length <= 1) return false;
    const firstVal = JSON.stringify(getter(products[0]) ?? null);
    return products.some((p) => JSON.stringify(getter(p) ?? null) !== firstVal);
  };

  // Collect all unique variant names (e.g. 配色, 轴体选择, 套装规格, 容量)
  const allVariantNames = useMemo(() => {
    const namesSet = new Set<string>();
    products.forEach((p) => {
      p.variants?.forEach((v) => namesSet.add(v.name));
    });
    return Array.from(namesSet);
  }, [products]);

  // Check if a specific variant (e.g. 配色/型号) differs across products
  const isVariantDifferent = (varName: string) => {
    if (products.length <= 1) return false;
    const getVariantString = (p: Product) => {
      const v = p.variants?.find((item) => item.name === varName);
      if (!v) return '无此配置项';
      return v.options.map((o) => o.label).join(' / ');
    };
    const firstVal = getVariantString(products[0]);
    return products.some((p) => getVariantString(p) !== firstVal);
  };

  // Collect all unique spec keys from all selected products
  const allSpecKeys = useMemo(() => {
    const keysSet = new Set<string>();
    products.forEach((p) => {
      if (p.specs) {
        Object.keys(p.specs).forEach((k) => keysSet.add(k));
      }
    });
    return Array.from(keysSet);
  }, [products]);

  // Check if a specific spec row has differences across products
  const isSpecDifferent = (key: string) => {
    if (products.length <= 1) return false;
    const firstVal = products[0]?.specs?.[key] || '—';
    return products.some((p) => (p.specs?.[key] || '—') !== firstVal);
  };

  // Filtered spec keys if user toggles "only differences"
  const visibleSpecKeys = useMemo(() => {
    if (!onlyDifferences) return allSpecKeys;
    return allSpecKeys.filter((key) => isSpecDifferent(key));
  }, [allSpecKeys, onlyDifferences, products]);

  // Filtered variant names if user toggles "only differences"
  const visibleVariantNames = useMemo(() => {
    if (!onlyDifferences) return allVariantNames;
    return allVariantNames.filter((name) => isVariantDifferent(name));
  }, [allVariantNames, onlyDifferences, products]);

  // Calculate total differences across all dimensions
  const diffSummary = useMemo(() => {
    if (products.length <= 1) return { totalDiffs: 0, hasDifferences: false };

    let count = 0;
    // Price
    if (isPropDifferent((p) => p.price)) count++;
    // Category
    if (isPropDifferent((p) => p.categoryLabel)) count++;
    // Brand
    if (isPropDifferent((p) => p.brand)) count++;
    // Rating
    if (isPropDifferent((p) => p.rating)) count++;
    // Sales
    if (isPropDifferent((p) => p.salesCount)) count++;
    // Stock
    if (isPropDifferent((p) => (p.stock > 10 ? 'in-stock' : p.stock > 0 ? 'low-stock' : 'out-of-stock'))) count++;
    // Delivery
    if (isPropDifferent((p) => p.deliveryEstimate)) count++;
    // Variants / Colors / Configs
    allVariantNames.forEach((name) => {
      if (isVariantDifferent(name)) count++;
    });
    // Specs
    allSpecKeys.forEach((key) => {
      if (isSpecDifferent(key)) count++;
    });

    return {
      totalDiffs: count,
      hasDifferences: count > 0,
    };
  }, [products, allVariantNames, allSpecKeys]);

  if (!isOpen) return null;

  const isAllSelected = products.length > 0 && selectedProducts.length === products.length;

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-black/60 backdrop-blur-xs flex items-center justify-center p-2 sm:p-4 md:p-6 animate-in fade-in duration-200">
      <div
        className="bg-white rounded-3xl border border-zinc-200 shadow-2xl max-w-5xl w-full overflow-hidden relative flex flex-col max-h-[92vh]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Modal Header */}
        <div className="p-4 sm:p-6 border-b border-zinc-200 flex flex-wrap items-center justify-between gap-3 bg-zinc-50/70">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-zinc-900 text-white flex items-center justify-center shadow-xs">
              <Scale className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg font-bold text-zinc-900">商品参数多维深度对比</h2>
                <span className="text-xs px-2.5 py-0.5 rounded-full bg-amber-100 text-amber-900 font-bold">
                  {products.length} / 3 款商品
                </span>
              </div>
              <p className="text-xs text-zinc-500 mt-0.5">
                高亮差异、默认规格自动匹配，支持勾选批量加入购物车
              </p>
            </div>
          </div>

          {/* Header Controls & View Switcher */}
          <div className="flex items-center gap-2 sm:gap-3 flex-wrap">
            {/* View Mode Switcher: Table vs Charts */}
            <div className="flex items-center p-1 bg-zinc-200/70 rounded-xl">
              <button
                onClick={() => setActiveTab('table')}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition ${
                  activeTab === 'table'
                    ? 'bg-white text-zinc-900 shadow-xs'
                    : 'text-zinc-600 hover:text-zinc-900'
                }`}
              >
                <Table className="w-3.5 h-3.5 text-emerald-600" />
                <span>详细参数表格</span>
              </button>
              <button
                onClick={() => setActiveTab('charts')}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition ${
                  activeTab === 'charts'
                    ? 'bg-white text-zinc-900 shadow-xs'
                    : 'text-zinc-600 hover:text-zinc-900'
                }`}
              >
                <BarChart3 className="w-3.5 h-3.5 text-blue-600" />
                <span>可视化图表</span>
              </button>
            </div>

            {/* Toggle Only Differences (only in table view) */}
            {activeTab === 'table' && products.length > 1 && (
              <button
                onClick={() => setOnlyDifferences(!onlyDifferences)}
                className={`px-3 py-1.5 rounded-xl text-xs font-semibold border transition flex items-center gap-1.5 select-none ${
                  onlyDifferences
                    ? 'bg-amber-400 text-zinc-950 border-amber-400 shadow-xs'
                    : 'bg-white text-zinc-700 border-zinc-200 hover:bg-zinc-100'
                }`}
              >
                <SlidersHorizontal className="w-3.5 h-3.5" />
                <span>{onlyDifferences ? '显示全部参数' : '仅看参数差异'}</span>
                {diffSummary.totalDiffs > 0 && (
                  <span
                    className={`text-[10px] px-1.5 py-0.2 rounded-full font-bold ${
                      onlyDifferences
                        ? 'bg-zinc-900 text-white'
                        : 'bg-amber-100 text-amber-900'
                    }`}
                  >
                    {diffSummary.totalDiffs}
                  </span>
                )}
              </button>
            )}

            {/* Clear All */}
            <button
              onClick={onClearAll}
              className="px-3 py-1.5 rounded-xl text-xs font-medium text-zinc-500 hover:text-rose-600 hover:bg-rose-50 border border-transparent hover:border-rose-200 transition"
              title="清空对比栏"
            >
              清空
            </button>

            {/* Close Button */}
            <button
              onClick={onClose}
              className="p-1.5 rounded-xl hover:bg-zinc-200/70 text-zinc-500 hover:text-zinc-900 transition"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Modal Main Comparison Table / Chart Area */}
        <div className="overflow-y-auto overflow-x-auto flex-1 p-4 sm:p-6 space-y-6">
          {products.length === 0 ? (
            <div className="py-20 text-center space-y-3">
              <Scale className="w-12 h-12 mx-auto text-zinc-300 stroke-1" />
              <h3 className="text-base font-bold text-zinc-800">暂未选择任何对比商品</h3>
              <p className="text-xs text-zinc-400 max-w-sm mx-auto">
                请在商品列表中点击商品卡片上的“加入对比”勾选框，最多可同时对比 3 款商品的差异。
              </p>
              <div className="flex items-center justify-center gap-3 mt-3">
                {lastComparedProducts.length > 0 && onRestoreLastCompare && (
                  <button
                    onClick={onRestoreLastCompare}
                    className="px-4 py-2 rounded-xl bg-amber-400 text-zinc-950 text-xs font-bold hover:bg-amber-300 transition flex items-center gap-1.5 shadow-sm"
                  >
                    <RotateCcw className="w-3.5 h-3.5" />
                    <span>恢复上次对比（{lastComparedProducts.length}款商品）</span>
                  </button>
                )}
                <button
                  onClick={onClose}
                  className="px-4 py-2 rounded-xl bg-zinc-900 text-white text-xs font-semibold hover:bg-zinc-800 transition"
                >
                  返回商品列表选择
                </button>
              </div>
            </div>
          ) : activeTab === 'charts' ? (
            <CompareChartsView
              products={products}
              onAddToCart={(p) => handleSingleAddToCart(p)}
              onQuickView={onQuickView}
            />
          ) : (
            <div className="min-w-[620px] space-y-6">
              {/* Highlight Notification & Batch Selection Quick Banner */}
              {products.length > 1 && (
                <div className="flex items-center justify-between p-3.5 rounded-2xl bg-amber-50/90 border border-amber-200/80 text-amber-950 text-xs shadow-xs">
                  <div className="flex items-center gap-2.5">
                    <div className="w-6 h-6 rounded-lg bg-amber-400 text-zinc-950 flex items-center justify-center font-bold shrink-0">
                      <Sparkles className="w-3.5 h-3.5" />
                    </div>
                    <div>
                      <span className="font-bold">参数差异智能高亮 & 批量选购：</span>
                      <span className="text-amber-900 ml-1">
                        差异项已用
                        <strong className="text-amber-950 bg-amber-200/80 px-1.5 py-0.5 rounded mx-1 font-bold">
                          淡黄色背景
                        </strong>
                        突出标记；每列商品均可勾选，支持一键以默认规格批量加入购物车。
                      </span>
                    </div>
                  </div>
                  <button
                    onClick={() => setOnlyDifferences(!onlyDifferences)}
                    className="shrink-0 px-3 py-1 rounded-xl bg-white text-amber-900 border border-amber-300 hover:bg-amber-100 font-semibold text-xs transition shadow-xs ml-2"
                  >
                    {onlyDifferences ? '查看所有参数' : '过滤只看差异项'}
                  </button>
                </div>
              )}

              {/* Product Cards Row (Sticky or Top Columns) */}
              <div className="grid grid-cols-4 gap-4 pb-6 border-b border-zinc-200">
                {/* Column 0: Label Header & Batch Selection Summary */}
                <div className="flex flex-col justify-between p-2">
                  <div>
                    <span className="text-xs font-bold text-zinc-400 uppercase tracking-wider">
                      对比维度 / 商品一览
                    </span>
                    <p className="text-[11px] text-zinc-400 mt-1">
                      勾选卡片以包含在批量加购清单中，支持自选或自动匹配默认规格
                    </p>
                  </div>

                  {/* Quick Select All in Header */}
                  {products.length > 0 && (
                    <div className="mt-4 pt-3 border-t border-zinc-100 space-y-2">
                      <button
                        onClick={() => handleToggleSelectAll(!isAllSelected)}
                        className="flex items-center gap-2 text-xs font-semibold text-zinc-700 hover:text-zinc-950 transition"
                      >
                        {isAllSelected ? (
                          <CheckSquare className="w-4 h-4 text-zinc-900" />
                        ) : (
                          <Square className="w-4 h-4 text-zinc-400" />
                        )}
                        <span>{isAllSelected ? '取消全选' : '全选所有对比商品'}</span>
                      </button>
                      <div className="text-[11px] text-zinc-500">
                        已勾选{' '}
                        <strong className="text-zinc-900 font-bold">
                          {selectedProducts.length}
                        </strong>{' '}
                        / {products.length} 款
                      </div>
                    </div>
                  )}
                </div>

                {/* Columns 1, 2, 3: Products */}
                {products.map((p) => {
                  const isSelected = !!selectedForBatch[p.id];
                  const finalPrice = getProductFinalPrice(p);
                  const effectiveVariants = getEffectiveVariants(p);
                  const isJustAdded = !!addedItemIds[p.id];
                  const discountPercent = Math.round(
                    ((p.originalPrice - p.price) / p.originalPrice) * 100
                  );

                  return (
                    <div
                      key={p.id}
                      className={`rounded-2xl border p-3.5 flex flex-col justify-between relative group transition duration-200 shadow-xs ${
                        isSelected
                          ? 'bg-zinc-50/90 border-zinc-900/40 ring-1 ring-zinc-900/10'
                          : 'bg-zinc-50/50 border-zinc-200/90 opacity-80 hover:opacity-100'
                      }`}
                    >
                      {/* Top Checkbox Badge for Batch Selection */}
                      <div className="flex items-center justify-between mb-2">
                        <button
                          onClick={() => handleToggleProductSelection(p.id)}
                          className={`flex items-center gap-1.5 px-2 py-1 rounded-lg text-[11px] font-semibold transition ${
                            isSelected
                              ? 'bg-zinc-900 text-white shadow-xs'
                              : 'bg-white text-zinc-600 border border-zinc-200 hover:bg-zinc-100'
                          }`}
                        >
                          {isSelected ? (
                            <CheckSquare className="w-3.5 h-3.5" />
                          ) : (
                            <Square className="w-3.5 h-3.5 text-zinc-400" />
                          )}
                          <span>{isSelected ? '已选批量加购' : '勾选加入批量'}</span>
                        </button>

                        {/* Delete from compare button */}
                        <button
                          onClick={() => onRemoveProduct(p.id)}
                          className="p-1.5 rounded-full bg-white/90 text-zinc-400 hover:text-rose-500 hover:bg-rose-50 shadow-xs transition"
                          title="从对比中移除"
                        >
                          <X className="w-3.5 h-3.5" />
                        </button>
                      </div>

                      <div>
                        {/* Thumbnail */}
                        <div
                          onClick={() => onQuickView(p)}
                          className="w-full aspect-square rounded-xl overflow-hidden bg-white mb-2.5 cursor-pointer border border-zinc-100 relative"
                        >
                          <img
                            src={p.images[0]}
                            alt={p.title}
                            className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                          />
                          {p.stock <= 5 && p.stock > 0 && (
                            <span className="absolute bottom-1.5 right-1.5 px-1.5 py-0.5 rounded-md bg-amber-500/90 text-white text-[9px] font-bold">
                              仅剩 {p.stock}
                            </span>
                          )}
                        </div>

                        {/* Brand & Title */}
                        <span className="text-[10px] font-bold text-zinc-400 uppercase tracking-wider block">
                          {p.brand}
                        </span>
                        <h4
                          onClick={() => onQuickView(p)}
                          className="text-xs font-bold text-zinc-900 hover:text-zinc-600 transition cursor-pointer line-clamp-2 mt-0.5 leading-snug"
                        >
                          {p.title}
                        </h4>

                        {/* Price */}
                        <div className="mt-2 flex items-baseline gap-1.5">
                          <span className="text-xs font-bold text-zinc-900">¥</span>
                          <span className="text-lg font-black text-zinc-900 tracking-tight">
                            {finalPrice}
                          </span>
                          <span className="text-[11px] text-zinc-400 line-through">
                            ¥{p.originalPrice}
                          </span>
                          {discountPercent > 0 && (
                            <span className="text-[9px] font-bold px-1 py-0.5 rounded bg-rose-50 text-rose-600">
                              -{discountPercent}%
                            </span>
                          )}
                        </div>

                        {/* Default Config Preview Pill */}
                        {p.variants && p.variants.length > 0 && (
                          <div className="mt-2 py-1 px-2 rounded-lg bg-zinc-100/90 text-[10px] text-zinc-600 flex items-center gap-1 truncate">
                            <span className="font-semibold text-zinc-800 shrink-0">默认:</span>
                            <span className="truncate text-zinc-700">
                              {Object.values(effectiveVariants).join(' / ')}
                            </span>
                          </div>
                        )}
                      </div>

                      {/* Action buttons */}
                      <div className="mt-3 pt-2.5 border-t border-zinc-200/80 flex flex-col gap-1.5">
                        <button
                          onClick={() => handleSingleAddToCart(p)}
                          className={`w-full py-1.5 px-2 rounded-xl text-xs font-semibold transition flex items-center justify-center gap-1.5 shadow-xs ${
                            isJustAdded
                              ? 'bg-emerald-600 text-white'
                              : 'bg-zinc-900 text-white hover:bg-zinc-800'
                          }`}
                        >
                          {isJustAdded ? (
                            <>
                              <Check className="w-3.5 h-3.5" />
                              <span>已加入购物车</span>
                            </>
                          ) : (
                            <>
                              <ShoppingBag className="w-3.5 h-3.5" />
                              <span>加购物车 (默认规格)</span>
                            </>
                          )}
                        </button>
                        <button
                          onClick={() => onQuickView(p)}
                          className="w-full py-1 px-2 rounded-xl bg-white border border-zinc-200 text-zinc-700 hover:bg-zinc-100 text-[11px] font-medium transition flex items-center justify-center gap-1"
                        >
                          <Eye className="w-3 h-3" />
                          <span>查看详情</span>
                        </button>
                      </div>
                    </div>
                  );
                })}

                {/* Empty Placeholders if fewer than 3 */}
                {Array.from({ length: 3 - products.length }).map((_, i) => (
                  <div
                    key={`placeholder-${i}`}
                    className="rounded-2xl border-2 border-dashed border-zinc-200 p-4 flex flex-col items-center justify-center text-center text-zinc-400 bg-zinc-50/40"
                  >
                    <Plus className="w-8 h-8 stroke-1 text-zinc-300 mb-2" />
                    <span className="text-xs font-medium text-zinc-500">待添加商品</span>
                    <span className="text-[11px] text-zinc-400 mt-0.5">
                      还可添加 {3 - products.length - i} 款
                    </span>
                  </div>
                ))}
              </div>

              {/* Section 1: 型号配置与可选配色对比 (Variants & Configurations with Interactive Switching) */}
              {(visibleVariantNames.length > 0 || !onlyDifferences) && allVariantNames.length > 0 && (
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <h3 className="text-xs font-bold text-zinc-900 uppercase tracking-wider flex items-center gap-1.5">
                      <Palette className="w-3.5 h-3.5 text-amber-600" />
                      型号配置与可选规格（可点击切换加购默认项）
                    </h3>
                    <span className="text-[11px] text-zinc-400">
                      点击选项可为该商品定制批量加购时的规格
                    </span>
                  </div>

                  <div className="rounded-2xl border border-zinc-200 overflow-hidden divide-y divide-zinc-200 text-xs">
                    {visibleVariantNames.map((varName) => {
                      const isDiff = isVariantDifferent(varName);
                      return (
                        <div
                          key={varName}
                          className={`grid grid-cols-4 p-3 items-center transition ${
                            isDiff
                              ? 'bg-amber-50/75 border-y border-amber-200/60 shadow-xs'
                              : 'bg-white'
                          }`}
                        >
                          <div className="font-semibold text-zinc-700 flex items-center gap-1.5">
                            <span className={isDiff ? 'text-amber-950 font-bold' : ''}>
                              {varName}
                            </span>
                            {isDiff && (
                              <span className="text-[9px] px-1.5 py-0.2 rounded bg-amber-200/90 text-amber-950 font-bold border border-amber-300/80">
                                差异
                              </span>
                            )}
                          </div>

                          {products.map((p) => {
                            const variantObj = p.variants?.find((v) => v.name === varName);
                            if (!variantObj) {
                              return (
                                <div key={p.id} className="text-zinc-300 px-2">
                                  —
                                </div>
                              );
                            }

                            const currentSelectedLabel =
                              customVariants[p.id]?.[varName] || variantObj.options[0]?.label;

                            return (
                              <div key={p.id} className="px-2 space-y-1">
                                <div className="flex flex-wrap gap-1">
                                  {variantObj.options.map((opt) => {
                                    const isChosen = currentSelectedLabel === opt.label;
                                    return (
                                      <button
                                        key={opt.id}
                                        onClick={() =>
                                          handleSelectVariantOption(p.id, varName, opt.label)
                                        }
                                        className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-medium transition ${
                                          isChosen
                                            ? 'bg-zinc-900 text-white shadow-xs'
                                            : isDiff
                                            ? 'bg-amber-100/90 text-amber-950 border border-amber-300/60 hover:bg-amber-200'
                                            : 'bg-zinc-100 text-zinc-700 border border-zinc-200/60 hover:bg-zinc-200'
                                        }`}
                                        title={`点击切换此规格为加购项`}
                                      >
                                        {isChosen && <Check className="w-2.5 h-2.5" />}
                                        <span>{opt.label}</span>
                                        {opt.priceModifier ? (
                                          <span
                                            className={
                                              isChosen ? 'text-zinc-300' : 'text-zinc-500'
                                            }
                                          >
                                            (+¥{opt.priceModifier})
                                          </span>
                                        ) : null}
                                      </button>
                                    );
                                  })}
                                </div>
                              </div>
                            );
                          })}

                          {Array.from({ length: 3 - products.length }).map((_, i) => (
                            <div key={i} className="text-zinc-300 px-2">—</div>
                          ))}
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}

              {/* Section 2: 核心关键属性对比 (Overview Section) */}
              <div className="space-y-3">
                <h3 className="text-xs font-bold text-zinc-900 uppercase tracking-wider flex items-center gap-1.5 pt-2">
                  <Sparkles className="w-3.5 h-3.5 text-amber-500" />
                  基本参数与评价
                </h3>

                <div className="rounded-2xl border border-zinc-200 overflow-hidden divide-y divide-zinc-200 text-xs">
                  {/* Price & Discount row */}
                  {(!onlyDifferences || isPropDifferent((p) => p.price)) && (
                    <div
                      className={`grid grid-cols-4 p-3 items-center transition ${
                        isPropDifferent((p) => p.price)
                          ? 'bg-amber-50/75 border-y border-amber-200/60 shadow-xs'
                          : 'bg-white'
                      }`}
                    >
                      <div className="font-semibold text-zinc-700 flex items-center gap-1.5">
                        <span className={isPropDifferent((p) => p.price) ? 'text-amber-950 font-bold' : ''}>
                          当前售价
                        </span>
                        {isPropDifferent((p) => p.price) && (
                          <span className="text-[9px] px-1.5 py-0.2 rounded bg-amber-200/90 text-amber-950 font-bold border border-amber-300/80">
                            差异
                          </span>
                        )}
                      </div>
                      {products.map((p) => (
                        <div key={p.id} className="text-zinc-900 font-bold px-2 flex items-baseline gap-1">
                          <span className="text-xs">¥</span>
                          <span className="text-base font-black">{p.price}</span>
                          <span className="text-[10px] text-zinc-400 font-normal line-through">
                            ¥{p.originalPrice}
                          </span>
                        </div>
                      ))}
                      {Array.from({ length: 3 - products.length }).map((_, i) => (
                        <div key={i} className="text-zinc-300 px-2">—</div>
                      ))}
                    </div>
                  )}

                  {/* Category row */}
                  {(!onlyDifferences || isPropDifferent((p) => p.categoryLabel)) && (
                    <div
                      className={`grid grid-cols-4 p-3 items-center transition ${
                        isPropDifferent((p) => p.categoryLabel)
                          ? 'bg-amber-50/75 border-y border-amber-200/60 shadow-xs'
                          : 'bg-zinc-50/50'
                      }`}
                    >
                      <div className="font-semibold text-zinc-700 flex items-center gap-1.5">
                        <span className={isPropDifferent((p) => p.categoryLabel) ? 'text-amber-950 font-bold' : ''}>
                          所属品类
                        </span>
                        {isPropDifferent((p) => p.categoryLabel) && (
                          <span className="text-[9px] px-1.5 py-0.2 rounded bg-amber-200/90 text-amber-950 font-bold border border-amber-300/80">
                            差异
                          </span>
                        )}
                      </div>
                      {products.map((p) => (
                        <div key={p.id} className="text-zinc-800 font-medium px-2">
                          {p.categoryLabel}
                        </div>
                      ))}
                      {Array.from({ length: 3 - products.length }).map((_, i) => (
                        <div key={i} className="text-zinc-300 px-2">—</div>
                      ))}
                    </div>
                  )}

                  {/* Rating & Review count row */}
                  {(!onlyDifferences || isPropDifferent((p) => p.rating)) && (
                    <div
                      className={`grid grid-cols-4 p-3 items-center transition ${
                        isPropDifferent((p) => p.rating)
                          ? 'bg-amber-50/75 border-y border-amber-200/60 shadow-xs'
                          : 'bg-white'
                      }`}
                    >
                      <div className="font-semibold text-zinc-700 flex items-center gap-1.5">
                        <span className={isPropDifferent((p) => p.rating) ? 'text-amber-950 font-bold' : ''}>
                          评分与评价
                        </span>
                        {isPropDifferent((p) => p.rating) && (
                          <span className="text-[9px] px-1.5 py-0.2 rounded bg-amber-200/90 text-amber-950 font-bold border border-amber-300/80">
                            差异
                          </span>
                        )}
                      </div>
                      {products.map((p) => (
                        <div key={p.id} className="px-2 flex items-center gap-1.5">
                          <div className="flex items-center gap-1 text-amber-500 font-bold">
                            <Star className="w-3.5 h-3.5 fill-amber-400 text-amber-400" />
                            <span>{p.rating}</span>
                          </div>
                          <span className="text-zinc-400 text-[11px]">({p.reviewCount}人评价)</span>
                        </div>
                      ))}
                      {Array.from({ length: 3 - products.length }).map((_, i) => (
                        <div key={i} className="text-zinc-300 px-2">—</div>
                      ))}
                    </div>
                  )}

                  {/* Sales count row */}
                  {(!onlyDifferences || isPropDifferent((p) => p.salesCount)) && (
                    <div
                      className={`grid grid-cols-4 p-3 items-center transition ${
                        isPropDifferent((p) => p.salesCount)
                          ? 'bg-amber-50/75 border-y border-amber-200/60 shadow-xs'
                          : 'bg-zinc-50/50'
                      }`}
                    >
                      <div className="font-semibold text-zinc-700 flex items-center gap-1.5">
                        <span className={isPropDifferent((p) => p.salesCount) ? 'text-amber-950 font-bold' : ''}>
                          近期销量
                        </span>
                        {isPropDifferent((p) => p.salesCount) && (
                          <span className="text-[9px] px-1.5 py-0.2 rounded bg-amber-200/90 text-amber-950 font-bold border border-amber-300/80">
                            差异
                          </span>
                        )}
                      </div>
                      {products.map((p) => (
                        <div key={p.id} className="text-zinc-800 px-2 font-medium">
                          已售 {p.salesCount}+ 件
                        </div>
                      ))}
                      {Array.from({ length: 3 - products.length }).map((_, i) => (
                        <div key={i} className="text-zinc-300 px-2">—</div>
                      ))}
                    </div>
                  )}

                  {/* Stock status row */}
                  {(!onlyDifferences || isPropDifferent((p) => (p.stock > 10 ? '充足' : p.stock > 0 ? '紧张' : '无货'))) && (
                    <div
                      className={`grid grid-cols-4 p-3 items-center transition ${
                        isPropDifferent((p) => (p.stock > 10 ? '充足' : p.stock > 0 ? '紧张' : '无货'))
                          ? 'bg-amber-50/75 border-y border-amber-200/60 shadow-xs'
                          : 'bg-white'
                      }`}
                    >
                      <div className="font-semibold text-zinc-700 flex items-center gap-1.5">
                        <span className={isPropDifferent((p) => (p.stock > 10 ? '充足' : p.stock > 0 ? '紧张' : '无货')) ? 'text-amber-950 font-bold' : ''}>
                          库存状态
                        </span>
                        {isPropDifferent((p) => (p.stock > 10 ? '充足' : p.stock > 0 ? '紧张' : '无货')) && (
                          <span className="text-[9px] px-1.5 py-0.2 rounded bg-amber-200/90 text-amber-950 font-bold border border-amber-300/80">
                            差异
                          </span>
                        )}
                      </div>
                      {products.map((p) => (
                        <div key={p.id} className="px-2">
                          {p.stock > 10 ? (
                            <span className="inline-flex items-center gap-1 text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded font-medium text-[11px]">
                              <Check className="w-3 h-3" />
                              现货充足 ({p.stock}件)
                            </span>
                          ) : p.stock > 0 ? (
                            <span className="inline-flex items-center gap-1 text-amber-700 bg-amber-50 px-2 py-0.5 rounded font-medium text-[11px]">
                              <AlertCircle className="w-3 h-3" />
                              仅剩 {p.stock} 件
                            </span>
                          ) : (
                            <span className="text-rose-600 font-medium text-[11px]">已售罄</span>
                          )}
                        </div>
                      ))}
                      {Array.from({ length: 3 - products.length }).map((_, i) => (
                        <div key={i} className="text-zinc-300 px-2">—</div>
                      ))}
                    </div>
                  )}

                  {/* Delivery and logistics */}
                  {(!onlyDifferences || isPropDifferent((p) => p.deliveryEstimate)) && (
                    <div
                      className={`grid grid-cols-4 p-3 items-center transition ${
                        isPropDifferent((p) => p.deliveryEstimate)
                          ? 'bg-amber-50/75 border-y border-amber-200/60 shadow-xs'
                          : 'bg-zinc-50/50'
                      }`}
                    >
                      <div className="font-semibold text-zinc-700 flex items-center gap-1.5">
                        <span className={isPropDifferent((p) => p.deliveryEstimate) ? 'text-amber-950 font-bold' : ''}>
                          物流时效
                        </span>
                        {isPropDifferent((p) => p.deliveryEstimate) && (
                          <span className="text-[9px] px-1.5 py-0.2 rounded bg-amber-200/90 text-amber-950 font-bold border border-amber-300/80">
                            差异
                          </span>
                        )}
                      </div>
                      {products.map((p) => (
                        <div key={p.id} className="px-2 text-zinc-700 flex items-center gap-1.5">
                          <Truck className="w-3.5 h-3.5 text-emerald-600 shrink-0" />
                          <span>{p.deliveryEstimate}</span>
                        </div>
                      ))}
                      {Array.from({ length: 3 - products.length }).map((_, i) => (
                        <div key={i} className="text-zinc-300 px-2">—</div>
                      ))}
                    </div>
                  )}
                </div>
              </div>

              {/* Section 3: 硬件与功能技术规格 (Specs Breakdown) */}
              <div className="space-y-3 pt-2">
                <div className="flex items-center justify-between">
                  <h3 className="text-xs font-bold text-zinc-900 uppercase tracking-wider flex items-center gap-1.5">
                    <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />
                    硬件与技术规格明细
                  </h3>
                  {visibleSpecKeys.length > 0 && (
                    <span className="text-[11px] text-zinc-400">
                      共对比 {visibleSpecKeys.length} 项规格参数
                    </span>
                  )}
                </div>

                {visibleSpecKeys.length === 0 ? (
                  <div className="p-6 text-center text-xs text-zinc-400 bg-zinc-50 rounded-2xl border border-zinc-200">
                    在当前对比商品中未发现差异项
                  </div>
                ) : (
                  <div className="rounded-2xl border border-zinc-200 overflow-hidden divide-y divide-zinc-200 text-xs">
                    {visibleSpecKeys.map((specKey, index) => {
                      const isDiff = isSpecDifferent(specKey);
                      return (
                        <div
                          key={specKey}
                          className={`grid grid-cols-4 p-3 items-center transition ${
                            isDiff
                              ? 'bg-amber-50/75 border-y border-amber-200/60 shadow-xs'
                              : index % 2 === 0
                              ? 'bg-white'
                              : 'bg-zinc-50/50'
                          }`}
                        >
                          <div className="font-semibold text-zinc-700 flex items-center gap-1.5">
                            <span className={isDiff ? 'text-amber-950 font-bold' : ''}>
                              {specKey}
                            </span>
                            {isDiff && (
                              <span className="text-[9px] px-1.5 py-0.2 rounded bg-amber-200/90 text-amber-950 font-bold border border-amber-300/80">
                                差异
                              </span>
                            )}
                          </div>

                          {products.map((p) => {
                            const val = p.specs?.[specKey];
                            return (
                              <div
                                key={p.id}
                                className={`px-2 ${
                                  val
                                    ? isDiff
                                      ? 'text-amber-950 font-bold'
                                      : 'text-zinc-900 font-medium'
                                    : 'text-zinc-300'
                                }`}
                              >
                                {val || '—'}
                              </div>
                            );
                          })}

                          {Array.from({ length: 3 - products.length }).map((_, i) => (
                            <div key={i} className="text-zinc-300 px-2">—</div>
                          ))}
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>

              {/* Section 4: 核心卖点清单 (Features Checklist) */}
              <div className="space-y-3 pt-2">
                <h3 className="text-xs font-bold text-zinc-900 uppercase tracking-wider flex items-center gap-1.5">
                  <Check className="w-3.5 h-3.5 text-indigo-600" />
                  官方核心亮点与卖点
                </h3>

                <div className="grid grid-cols-4 gap-4">
                  <div className="p-3 text-xs font-semibold text-zinc-500">
                    主推亮点特色
                  </div>
                  {products.map((p) => (
                    <div
                      key={p.id}
                      className="p-3 rounded-2xl bg-zinc-50 border border-zinc-200 text-xs space-y-1.5"
                    >
                      {p.features.map((feat, idx) => (
                        <div key={idx} className="flex items-start gap-1.5 text-zinc-700 leading-tight">
                          <Check className="w-3 h-3 text-emerald-600 shrink-0 mt-0.5" />
                          <span>{feat}</span>
                        </div>
                      ))}
                    </div>
                  ))}
                  {Array.from({ length: 3 - products.length }).map((_, i) => (
                    <div
                      key={i}
                      className="p-3 rounded-2xl border border-dashed border-zinc-200 text-zinc-300 text-xs text-center flex items-center justify-center"
                    >
                      —
                    </div>
                  ))}
                </div>
              </div>

              {/* Section 5: 每列商品底部批量加购控制区 (Column Footer Batch Cart Action Blocks) */}
              <div className="space-y-3 pt-4 border-t border-zinc-200">
                <div className="flex items-center justify-between">
                  <h3 className="text-xs font-bold text-zinc-900 uppercase tracking-wider flex items-center gap-1.5">
                    <ShoppingBag className="w-3.5 h-3.5 text-zinc-900" />
                    商品专属批量加购操作列
                  </h3>
                  <span className="text-[11px] text-zinc-500">
                    自动处理默认规格并合并至购物车
                  </span>
                </div>

                <div className="grid grid-cols-4 gap-4">
                  {/* Column 0: Batch Action Summary Card */}
                  <div className="p-4 rounded-2xl bg-zinc-900 text-white flex flex-col justify-between shadow-xs">
                    <div>
                      <div className="flex items-center gap-2">
                        <Layers className="w-4 h-4 text-amber-400" />
                        <span className="text-xs font-bold">批量选购结算</span>
                      </div>
                      <p className="text-[11px] text-zinc-300 mt-1 leading-relaxed">
                        已勾选右侧{' '}
                        <strong className="text-amber-400 font-bold">
                          {selectedProducts.length}
                        </strong>{' '}
                        款商品，将以各商品的默认配置或您指定的配置合并加购。
                      </p>
                    </div>

                    <div className="mt-4 pt-3 border-t border-zinc-800 space-y-2">
                      <button
                        onClick={() => handleToggleSelectAll(!isAllSelected)}
                        className="w-full py-1.5 px-2 rounded-xl bg-zinc-800 hover:bg-zinc-700 text-white text-xs font-medium transition flex items-center justify-center gap-1.5"
                      >
                        {isAllSelected ? (
                          <CheckSquare className="w-3.5 h-3.5 text-amber-400" />
                        ) : (
                          <Square className="w-3.5 h-3.5 text-zinc-400" />
                        )}
                        <span>{isAllSelected ? '取消全选' : '全选对比项'}</span>
                      </button>

                      <button
                        onClick={handleBatchAdd}
                        disabled={selectedProducts.length === 0 || isBatchAdding}
                        className={`w-full py-2 px-3 rounded-xl text-xs font-bold transition flex items-center justify-center gap-1.5 shadow-md ${
                          selectedProducts.length === 0
                            ? 'bg-zinc-800 text-zinc-500 cursor-not-allowed'
                            : batchSuccess
                            ? 'bg-emerald-600 text-white'
                            : 'bg-amber-400 text-zinc-950 hover:bg-amber-300'
                        }`}
                      >
                        {batchSuccess ? (
                          <>
                            <Check className="w-4 h-4" />
                            <span>批量加购成功！</span>
                          </>
                        ) : (
                          <>
                            <ShoppingBag className="w-4 h-4" />
                            <span>
                              {selectedProducts.length > 0
                                ? `一键加购已选 (${selectedProducts.length}款)`
                                : '请勾选加购商品'}
                            </span>
                          </>
                        )}
                      </button>
                    </div>
                  </div>

                  {/* Columns 1, 2, 3: Per-product Bottom Action Card */}
                  {products.map((p) => {
                    const isSelected = !!selectedForBatch[p.id];
                    const finalPrice = getProductFinalPrice(p);
                    const effectiveVariants = getEffectiveVariants(p);
                    const isJustAdded = !!addedItemIds[p.id];

                    return (
                      <div
                        key={`bottom-action-${p.id}`}
                        className={`p-4 rounded-2xl border transition flex flex-col justify-between ${
                          isSelected
                            ? 'bg-amber-50/60 border-amber-300 shadow-xs'
                            : 'bg-zinc-50/70 border-zinc-200'
                        }`}
                      >
                        <div className="space-y-2.5">
                          {/* Selection Checkbox */}
                          <label className="flex items-center gap-2 cursor-pointer select-none">
                            <input
                              type="checkbox"
                              checked={isSelected}
                              onChange={() => handleToggleProductSelection(p.id)}
                              className="w-4 h-4 rounded border-zinc-300 text-zinc-900 focus:ring-zinc-900"
                            />
                            <span
                              className={`text-xs font-bold ${
                                isSelected ? 'text-amber-950' : 'text-zinc-700'
                              }`}
                            >
                              {isSelected ? '已包含在批量清单' : '未勾选此款'}
                            </span>
                          </label>

                          {/* Default/Selected specs label */}
                          {p.variants && p.variants.length > 0 ? (
                            <div className="p-2 rounded-xl bg-white/90 border border-zinc-200/80 text-[11px] space-y-1">
                              <span className="text-[10px] font-bold text-zinc-400 uppercase tracking-wider block">
                                默认/生效规格
                              </span>
                              <div className="text-zinc-800 font-medium leading-tight">
                                {Object.values(effectiveVariants).join(' · ')}
                              </div>
                            </div>
                          ) : (
                            <div className="p-2 rounded-xl bg-white/90 border border-zinc-200/80 text-[11px] text-zinc-500">
                              标准官方原装规格（单规格）
                            </div>
                          )}

                          {/* Subtotal */}
                          <div className="flex items-baseline justify-between text-xs pt-1">
                            <span className="text-zinc-500">结算单价:</span>
                            <span className="font-bold text-zinc-900 text-sm">
                              ¥{finalPrice}
                            </span>
                          </div>
                        </div>

                        {/* Individual add button */}
                        <button
                          onClick={() => handleSingleAddToCart(p)}
                          className={`mt-3 w-full py-1.5 px-2 rounded-xl text-xs font-semibold transition flex items-center justify-center gap-1.5 shadow-xs ${
                            isJustAdded
                              ? 'bg-emerald-600 text-white'
                              : 'bg-zinc-900 text-white hover:bg-zinc-800'
                          }`}
                        >
                          {isJustAdded ? (
                            <>
                              <Check className="w-3.5 h-3.5" />
                              <span>已加入购物车</span>
                            </>
                          ) : (
                            <>
                              <Plus className="w-3.5 h-3.5" />
                              <span>单款加购物车</span>
                            </>
                          )}
                        </button>
                      </div>
                    );
                  })}

                  {/* Empty Placeholders */}
                  {Array.from({ length: 3 - products.length }).map((_, i) => (
                    <div
                      key={`empty-bottom-${i}`}
                      className="p-4 rounded-2xl border border-dashed border-zinc-200 text-zinc-300 text-xs text-center flex items-center justify-center bg-zinc-50/40"
                    >
                      待对比位
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Modal Fixed Footer with Batch Add to Cart Bar */}
        <div className="p-4 sm:p-5 border-t border-zinc-200 bg-zinc-50/95 backdrop-blur-xs flex flex-wrap items-center justify-between gap-4">
          {/* Left summary */}
          <div className="flex items-center gap-3 sm:gap-4 flex-wrap">
            {products.length > 0 && (
              <button
                onClick={() => handleToggleSelectAll(!isAllSelected)}
                className="flex items-center gap-1.5 text-xs font-semibold text-zinc-700 hover:text-zinc-950 transition"
              >
                {isAllSelected ? (
                  <CheckSquare className="w-4 h-4 text-zinc-900" />
                ) : (
                  <Square className="w-4 h-4 text-zinc-400" />
                )}
                <span>全选对比商品</span>
              </button>
            )}

            <div className="h-4 w-px bg-zinc-300 hidden sm:block" />

            <div className="text-xs text-zinc-600 flex items-center gap-2">
              <span>
                已勾选{' '}
                <strong className="text-zinc-900 font-bold">
                  {selectedProducts.length}
                </strong>{' '}
                / {products.length} 款
              </span>
              {selectedProducts.length > 0 && (
                <div className="flex items-baseline gap-1.5 bg-white px-2.5 py-1 rounded-lg border border-zinc-200 shadow-2xs">
                  <span className="text-zinc-500 text-[11px]">合计:</span>
                  <span className="text-amber-600 font-bold">¥</span>
                  <span className="text-base font-black text-zinc-900">
                    {totalSelectedPrice}
                  </span>
                  {totalSavedPrice > 0 && (
                    <span className="text-[10px] text-rose-600 font-bold ml-1">
                      (已省 ¥{totalSavedPrice})
                    </span>
                  )}
                </div>
              )}
            </div>
          </div>

          {/* Right action CTAs */}
          <div className="flex items-center gap-2.5">
            <button
              onClick={onClose}
              className="px-4 py-2 rounded-xl bg-white border border-zinc-200 text-zinc-700 text-xs font-semibold hover:bg-zinc-100 transition"
            >
              关闭
            </button>

            {products.length > 0 && (
              <button
                onClick={handleBatchAdd}
                disabled={selectedProducts.length === 0 || isBatchAdding}
                className={`px-5 py-2 rounded-xl text-xs font-bold transition flex items-center gap-2 shadow-md ${
                  selectedProducts.length === 0
                    ? 'bg-zinc-300 text-zinc-500 cursor-not-allowed'
                    : batchSuccess
                    ? 'bg-emerald-600 text-white'
                    : 'bg-zinc-900 text-white hover:bg-zinc-800 active:scale-98'
                }`}
              >
                {batchSuccess ? (
                  <>
                    <Check className="w-4 h-4 text-white" />
                    <span>已批量加入购物车！</span>
                  </>
                ) : (
                  <>
                    <ShoppingBag className="w-4 h-4 text-amber-400" />
                    <span>
                      批量加入购物车 ({selectedProducts.length}款)
                    </span>
                    <ArrowRight className="w-3.5 h-3.5 opacity-70" />
                  </>
                )}
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
