import React, { useEffect, useState } from 'react';
import { useBodyScrollLock } from '../hooks/useBodyScrollLock';
import { CartItem, Coupon } from '../types/ecommerce';
import { quotePortalFreight } from '../api/portalApi';
import {
  X,
  ShoppingBag,
  Trash2,
  Plus,
  Minus,
  ArrowRight,
  Sparkles,
  Truck,
  Tag,
  CheckCircle2,
  AlertCircle,
  ChevronDown,
  ChevronUp,
  Ticket,
  Check,
} from 'lucide-react';

interface CartDrawerProps {
  isOpen: boolean;
  cartItems: CartItem[];
  appliedCoupon: Coupon | null;
  claimedCoupons: Coupon[];
  onClose: () => void;
  onUpdateQuantity: (cartItemId: string, newQuantity: number) => void;
  onToggleSelectItem: (cartItemId: string) => void;
  onToggleSelectAll: (select: boolean) => void;
  onRemoveItem: (cartItemId: string) => void;
  onClearCart: () => void;
  onApplyCoupon: (couponCode: string) => boolean;
  onRemoveCoupon: () => void;
  onOpenCheckout: () => void;
  onOpenCouponCenter?: () => void;
  /** 清理已下架或无库存商品，返回清理结果供页面展示错误态。 */
  onClearInvalidItems?: () => Promise<{ removed: number; failed: number }>;
}

export const CartDrawer: React.FC<CartDrawerProps> = ({
  isOpen,
  cartItems = [],
  appliedCoupon,
  claimedCoupons = [],
  onClose,
  onUpdateQuantity,
  onToggleSelectItem,
  onToggleSelectAll,
  onRemoveItem,
  onClearCart,
  onApplyCoupon,
  onRemoveCoupon,
  onOpenCheckout,
  onOpenCouponCenter,
  onClearInvalidItems,
}) => {
  useBodyScrollLock(isOpen);
  const [couponInput, setCouponInput] = useState('');
  const [couponError, setCouponError] = useState('');
  const [showCouponSelector, setShowCouponSelector] = useState(false);
  const [shippingFee, setShippingFee] = useState(0);
  const [freightQuoteLoading, setFreightQuoteLoading] = useState(false);
  const [freightQuoteError, setFreightQuoteError] = useState(false);
  const [freeShippingThreshold, setFreeShippingThreshold] = useState(99);
  const [quoteFreeShipping, setQuoteFreeShipping] = useState<boolean | null>(null);

  const safeCartItems = cartItems || [];
  const selectedItems = safeCartItems.filter((item) => item.selected);
  const isAllSelected = safeCartItems.length > 0 && selectedItems.length === safeCartItems.length;
  const cartQuantity = safeCartItems.reduce((sum, item) => sum + (item.quantity || 0), 0);
  const selectedQuantity = selectedItems.reduce((sum, item) => sum + (item.quantity || 0), 0);
  const [invalidClearing, setInvalidClearing] = useState(false);

  // 商品下架、SKU 删除或库存归零后，购物车仍可能保留历史条目，需要明确提示用户处理。
  const invalidItems = safeCartItems.filter((item) => {
    const sku = item.skuId !== undefined ? item.product.skus?.find((candidate) => candidate.id === item.skuId) : undefined;
    return (sku?.stock ?? item.product.stock ?? 0) <= 0;
  });

  const handleClearInvalidItems = async () => {
    if (!onClearInvalidItems || invalidItems.length === 0 || invalidClearing) return;
    setInvalidClearing(true);
    try {
      await onClearInvalidItems();
    } finally {
      setInvalidClearing(false);
    }
  };

  const rawSubtotal = selectedItems.reduce(
    (acc, item) => acc + (item.unitPrice || 0) * (item.quantity || 1),
    0
  );

  const couponDiscount = appliedCoupon ? (rawSubtotal >= appliedCoupon.minSpend ? appliedCoupon.discountAmount : 0) : 0;
  const isFreeShipping = quoteFreeShipping ?? rawSubtotal >= freeShippingThreshold;
  const freightItemsKey = selectedItems
    .map((item) => `${item.productId}:${item.skuId || ''}:${item.quantity}`)
    .join('|');

  useEffect(() => {
    if (!isOpen || selectedItems.length === 0 || rawSubtotal <= 0) {
      setShippingFee(0);
      setFreightQuoteLoading(false);
      setFreightQuoteError(false);
      setQuoteFreeShipping(null);
      return;
    }
    let active = true;
    setFreightQuoteLoading(true);
    setFreightQuoteError(false);
    quotePortalFreight({
      items: selectedItems.map((item) => ({
        productId: Number(item.productId.replace(/^prod-/, '')),
        skuId: item.skuId,
        quantity: item.quantity,
      })),
      subtotalAmount: rawSubtotal,
      discountAmount: couponDiscount,
    }).then((quote) => {
      if (active) {
        setShippingFee(Number(quote.freightAmount || 0));
        setFreeShippingThreshold(Number(quote.freeShippingThreshold || 99));
        setQuoteFreeShipping(Boolean(quote.freeShipping));
      }
    }).catch(() => {
      // 运费由服务端唯一计算，接口失败时不再展示写死的 15 元假数据。
      if (active) {
        setShippingFee(0);
        setFreightQuoteError(true);
        setQuoteFreeShipping(null);
      }
    }).finally(() => {
      if (active) setFreightQuoteLoading(false);
    });
    return () => {
      active = false;
    };
  }, [couponDiscount, freightItemsKey, isOpen, rawSubtotal]);

  const needForFreeShipping = Math.max(0, 99 - rawSubtotal);
  const totalPayable = Math.max(0, rawSubtotal - couponDiscount + shippingFee);

  if (!isOpen) return null;

  const handleApplyCoupon = (e: React.FormEvent) => {
    e.preventDefault();
    if (!couponInput.trim()) return;
    const success = onApplyCoupon(couponInput.trim());
    if (success) {
      setCouponError('');
      setCouponInput('');
    } else {
      setCouponError('优惠码无效或不满足最低使用金额');
    }
  };

  return (
    <div className="fixed inset-0 z-50 overflow-hidden bg-black/60 backdrop-blur-xs flex justify-end animate-in fade-in duration-200">
      <div 
        className="w-full max-w-md bg-white h-full shadow-2xl flex flex-col justify-between animate-in slide-in-from-right duration-300 relative"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="p-4 sm:p-5 border-b border-zinc-200 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-zinc-900 text-white flex items-center justify-center">
              <ShoppingBag className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-base font-bold text-zinc-900">购物车</h2>
              <p className="text-xs text-zinc-500">已选 {selectedItems.length} / 共 {cartItems.length} 种商品</p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {cartItems.length > 0 && (
              <button
                onClick={onClearCart}
                className="text-xs text-zinc-400 hover:text-rose-600 transition flex items-center gap-1 px-2 py-1 rounded"
              >
                <Trash2 className="w-3.5 h-3.5" />
                清空
              </button>
            )}
            <button
              onClick={onClose}
              className="p-1.5 rounded-lg hover:bg-zinc-100 text-zinc-500 hover:text-zinc-900 transition"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Free Shipping Progress Indicator */}
        <div className="bg-zinc-50 p-3.5 border-b border-zinc-200 text-xs">
          <div className="flex items-center justify-between font-medium mb-1.5">
            <span className="flex items-center gap-1.5 text-zinc-700">
              <Truck className="w-4 h-4 text-emerald-600" />
              {freightQuoteError ? (
                <span className="text-amber-700 font-semibold">运费将在结算页按收货地址重新试算</span>
              ) : isFreeShipping ? (
                <strong className="text-emerald-700 font-semibold">🎉 已满足包邮条件！</strong>
              ) : (
                <span>
                  还差 <strong className="text-zinc-900 font-bold">¥{needForFreeShipping}</strong> 即可享包邮
                </span>
              )}
            </span>
            <span className="text-zinc-400">门槛 ¥99</span>
          </div>
          <div className="w-full h-1.5 rounded-full bg-zinc-200 overflow-hidden">
            <div
              className="h-full bg-emerald-500 rounded-full transition-all duration-300"
              style={{ width: `${Math.min(100, (rawSubtotal / freeShippingThreshold) * 100)}%` }}
            />
          </div>
        </div>

        {/* Item List or Empty state */}
        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          {invalidItems.length > 0 && (
            <div className="rounded-xl border border-amber-200 bg-amber-50 px-3 py-2.5 text-xs text-amber-800">
              <div className="flex items-center justify-between gap-2">
                <span>有 {invalidItems.length} 件商品已下架或库存不足，无法结算</span>
                {onClearInvalidItems && (
                  <button
                    type="button"
                    onClick={handleClearInvalidItems}
                    disabled={invalidClearing}
                    className="shrink-0 rounded-lg bg-amber-600 px-2.5 py-1 text-[11px] font-semibold text-white transition hover:bg-amber-700 disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {invalidClearing ? '清理中…' : '一键清理'}
                  </button>
                )}
              </div>
            </div>
          )}
          {cartItems.length === 0 ? (
            <div className="h-full flex flex-col items-center justify-center text-center p-6 space-y-4 text-zinc-400">
              <div className="w-16 h-16 rounded-full bg-zinc-100 flex items-center justify-center text-zinc-400">
                <ShoppingBag className="w-8 h-8 stroke-1" />
              </div>
              <div>
                <h3 className="text-sm font-semibold text-zinc-800">购物车空空如也</h3>
                <p className="text-xs text-zinc-400 mt-1">快去挑选您心仪的商品吧</p>
              </div>
              <button
                onClick={onClose}
                className="px-5 py-2 rounded-xl bg-zinc-900 text-white text-xs font-semibold hover:bg-zinc-800 transition"
              >
                去逛逛
              </button>
            </div>
          ) : (
            <>
              {/* Select All Row */}
              <div className="flex items-center justify-between pb-2 border-b border-zinc-100 text-xs font-medium text-zinc-600">
                <label className="flex items-center gap-2 cursor-pointer select-none">
                  <input
                    type="checkbox"
                    checked={isAllSelected}
                    onChange={(e) => onToggleSelectAll(e.target.checked)}
                    className="w-4 h-4 rounded text-zinc-900 focus:ring-0 cursor-pointer accent-zinc-900"
                  />
                  <span>全选所有商品</span>
                </label>
                <span className="text-zinc-400">共 {cartQuantity} 件</span>
              </div>

              {/* Items */}
              {cartItems.map((item) => (
                <div
                  key={item.id}
                  className={`p-3 rounded-2xl border transition-all flex items-start gap-3 ${
                    item.selected ? 'bg-white border-zinc-300 shadow-xs' : 'bg-zinc-50/70 border-zinc-200/80 opacity-75'
                  }`}
                >
                  {/* Select Checkbox */}
                  <input
                    type="checkbox"
                    checked={item.selected}
                    onChange={() => onToggleSelectItem(item.id)}
                    className="mt-3.5 w-4 h-4 rounded text-zinc-900 focus:ring-0 cursor-pointer accent-zinc-900 shrink-0"
                  />

                  {/* Thumbnail */}
                  <img
                    src={item.product.images[0]}
                    alt={item.product.title}
                    className="w-18 h-18 rounded-xl object-cover bg-zinc-100 border border-zinc-200 shrink-0"
                  />

                  {/* Info */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-start justify-between gap-1">
                      <h4 className="text-xs font-semibold text-zinc-900 line-clamp-1">
                        {item.product.title}
                      </h4>
                      <button
                        onClick={() => onRemoveItem(item.id)}
                        className="text-zinc-400 hover:text-rose-500 transition p-1"
                        title="删除"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>

                    {/* Variant summary tags */}
                    {Object.keys(item.selectedVariants).length > 0 && (
                      <div className="flex flex-wrap gap-1 my-1">
                        {Object.entries(item.selectedVariants).map(([k, v]) => (
                          <span
                            key={k}
                            className="text-[10px] px-1.5 py-0.5 rounded bg-zinc-100 text-zinc-600 font-medium"
                          >
                            {k}: {v}
                          </span>
                        ))}
                      </div>
                    )}

                    {/* Price and Counter */}
                    <div className="flex items-center justify-between gap-2 mt-2">
                      <div className="flex items-baseline gap-1">
                        <span className="text-xs font-bold text-zinc-900">¥</span>
                        <span className="text-sm font-black text-zinc-900">
                          {item.unitPrice}
                        </span>
                      </div>

                      {/* Quantity Stepper */}
                      <div className="flex items-center border border-zinc-200 rounded-lg bg-white p-0.5">
                        <button
                          onClick={() => onUpdateQuantity(item.id, item.quantity - 1)}
                          disabled={item.quantity <= 1}
                          className="p-1 rounded hover:bg-zinc-100 text-zinc-600 disabled:opacity-30 transition"
                        >
                          <Minus className="w-3 h-3" />
                        </button>
                        <span className="w-7 text-center text-xs font-bold text-zinc-800">
                          {item.quantity}
                        </span>
                        <button
                          onClick={() => onUpdateQuantity(item.id, item.quantity + 1)}
                          disabled={item.quantity >= item.product.stock}
                          className="p-1 rounded hover:bg-zinc-100 text-zinc-600 disabled:opacity-30 transition"
                        >
                          <Plus className="w-3 h-3" />
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </>
          )}
        </div>

        {/* Footer with Voucher and Checkout */}
        {cartItems.length > 0 && (
          <div className="p-4 sm:p-5 border-t border-zinc-200 bg-white space-y-3 shadow-lg">
            {/* Claimed Coupons & Selector Area */}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <button
                  type="button"
                  onClick={() => setShowCouponSelector(!showCouponSelector)}
                  className="text-xs font-bold text-zinc-800 hover:text-amber-600 flex items-center gap-1.5 transition"
                >
                  <Ticket className="w-3.5 h-3.5 text-amber-600" />
                  <span>
                    优惠券抵扣
                    {claimedCoupons.length > 0 && (
                      <span className="ml-1 text-[11px] px-1.5 py-0.2 rounded-full bg-amber-100 text-amber-800 font-semibold">
                        已领 {claimedCoupons.length} 张
                      </span>
                    )}
                  </span>
                  {showCouponSelector ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
                </button>

                {appliedCoupon ? (
                  <span className="text-[11px] font-bold text-emerald-600 flex items-center gap-1">
                    <CheckCircle2 className="w-3 h-3" />
                    已自动关联 -¥{appliedCoupon.discountAmount}
                  </span>
                ) : (
                  <span className="text-[11px] text-zinc-400">暂无适用优惠券</span>
                )}
              </div>

              {/* Collapsible Claimed Coupons Drawer / Selector */}
              {showCouponSelector && (
                <div className="p-3 bg-zinc-50 rounded-2xl border border-zinc-200 space-y-2 text-xs max-h-48 overflow-y-auto animate-in fade-in duration-200">
                  <div className="text-[11px] font-semibold text-zinc-500 mb-1 flex items-center justify-between">
                    <span>我的账户优惠券：</span>
                    {onOpenCouponCenter && (
                      <button
                        onClick={() => {
                          onClose();
                          onOpenCouponCenter();
                        }}
                        className="text-amber-700 hover:underline font-bold"
                      >
                        前往领券中心 &gt;
                      </button>
                    )}
                  </div>

                  {claimedCoupons.length === 0 ? (
                    <div className="py-2 text-center text-zinc-400 text-xs">
                      暂无已领优惠券，可在首页领券中心一键免费领取
                    </div>
                  ) : (
                    <div className="space-y-1.5">
                      {claimedCoupons.map((c) => {
                        const isEligible = rawSubtotal >= c.minSpend;
                        const isSelected = appliedCoupon?.code === c.code;

                        return (
                          <div
                            key={c.code}
                            onClick={() => {
                              if (isEligible) {
                                if (isSelected) {
                                  onRemoveCoupon();
                                } else {
                                  onApplyCoupon(c.code);
                                }
                              }
                            }}
                            className={`p-2 rounded-xl border flex items-center justify-between cursor-pointer transition ${
                              isSelected
                                ? 'bg-emerald-50 border-emerald-300 text-emerald-900 font-semibold'
                                : isEligible
                                ? 'bg-white border-zinc-200 hover:border-amber-300 text-zinc-800'
                                : 'bg-zinc-100/60 border-zinc-200 text-zinc-400 cursor-not-allowed opacity-60'
                            }`}
                          >
                            <div className="flex items-center gap-2 min-w-0">
                              <div className={`w-4 h-4 rounded-full flex items-center justify-center text-[10px] shrink-0 ${isSelected ? 'bg-emerald-600 text-white' : 'border border-zinc-300'}`}>
                                {isSelected && <Check className="w-2.5 h-2.5 stroke-3" />}
                              </div>
                              <div className="truncate">
                                <span className="font-bold text-rose-600 mr-1">¥{c.discountAmount}</span>
                                <span>{c.title}</span>
                                <span className="text-[10px] text-zinc-400 ml-1.5">(满¥{c.minSpend})</span>
                              </div>
                            </div>
                            <div className="shrink-0">
                              {isSelected ? (
                                <span className="text-[10px] text-emerald-700 font-bold">使用中</span>
                              ) : isEligible ? (
                                <span className="text-[10px] text-amber-700 font-medium">可用</span>
                              ) : (
                                <span className="text-[10px] text-zinc-400">差¥{(c.minSpend - rawSubtotal).toFixed(0)}</span>
                              )}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}

                  {/* Manual Code Input */}
                  <form onSubmit={handleApplyCoupon} className="flex gap-2 pt-2 border-t border-zinc-200">
                    <input
                      type="text"
                      placeholder="手动输入券码 (例如 AURORA20)"
                      value={couponInput}
                      onChange={(e) => {
                        setCouponInput(e.target.value.toUpperCase());
                        setCouponError('');
                      }}
                      className="flex-1 py-1 px-2.5 rounded-lg border border-zinc-200 bg-white text-xs text-zinc-800 placeholder:text-zinc-400 focus:outline-none uppercase font-mono"
                    />
                    <button
                      type="submit"
                      className="px-2.5 py-1 rounded-lg bg-zinc-900 text-white text-xs font-semibold hover:bg-zinc-800 transition"
                    >
                      兑换
                    </button>
                  </form>
                  {couponError && (
                    <div className="text-[11px] text-rose-500 flex items-center gap-1">
                      <AlertCircle className="w-3 h-3" />
                      {couponError}
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* Price breakdown */}
            <div className="space-y-1.5 text-xs text-zinc-500 pt-1">
              <div className="flex justify-between">
                <span>商品总额</span>
                <span className="font-semibold text-zinc-900">¥{rawSubtotal.toFixed(2)}</span>
              </div>
              {couponDiscount > 0 && (
                <div className="flex justify-between text-emerald-600">
                  <span className="flex items-center gap-1">
                    <span>优惠券减免</span>
                    <span className="text-[10px] px-1.5 py-0.2 rounded bg-emerald-100 text-emerald-800 font-bold">
                      {appliedCoupon?.code}
                    </span>
                  </span>
                  <span className="font-semibold">-¥{couponDiscount.toFixed(2)}</span>
                </div>
              )}
              <div className="flex justify-between">
                <span>运费</span>
                <span className="font-semibold text-zinc-900">
                  {freightQuoteLoading ? '计算中…' : freightQuoteError ? '结算页试算' : shippingFee === 0 ? <strong className="text-emerald-600">包邮</strong> : `¥${shippingFee.toFixed(2)}`}
                </span>
              </div>
              <div className="flex justify-between pt-2 border-t border-zinc-100 text-sm font-bold text-zinc-900">
                <span>应付总额</span>
                <span className="text-xl font-black text-zinc-900">¥{totalPayable.toFixed(2)}</span>
              </div>
            </div>

            {/* Checkout Button */}
            <button
              onClick={onOpenCheckout}
              disabled={selectedItems.length === 0}
              className="w-full py-3.5 px-4 rounded-2xl bg-zinc-900 text-white font-bold text-sm hover:bg-zinc-800 disabled:opacity-40 disabled:cursor-not-allowed transition flex items-center justify-center gap-2 shadow-md"
            >
              <span>立即结算 ({selectedQuantity}件)</span>
              <ArrowRight className="w-4 h-4" />
            </button>
          </div>
        )}
      </div>
    </div>
  );
};
