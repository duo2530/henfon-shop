import React, { useState } from 'react';
import { Coupon } from '../types/ecommerce';
import { AVAILABLE_COUPONS } from '../data/products';
import { couponTagLabel } from '../utils/couponTag';
import {
  Ticket,
  Sparkles,
  CheckCircle2,
  Clock,
  ArrowRight,
  Zap,
  Info,
  Gift,
  Check,
  ChevronRight,
} from 'lucide-react';

interface CouponCenterProps {
  coupons?: Coupon[];
  claimedCouponCodes?: string[];
  appliedCoupon?: Coupon | null;
  onClaimCoupon: (coupon: Coupon) => void;
  onClaimAllCoupons: () => void;
  onUseCoupon?: (coupon: Coupon) => void;
}

export const CouponCenter: React.FC<CouponCenterProps> = ({
  coupons = AVAILABLE_COUPONS,
  claimedCouponCodes = [],
  appliedCoupon = null,
  onClaimCoupon,
  onClaimAllCoupons,
  onUseCoupon,
}) => {
  const [selectedFilter, setSelectedFilter] = useState<'all' | 'universal' | 'big' | 'category'>('all');

  const safeCoupons = coupons || AVAILABLE_COUPONS;
  const safeClaimedCodes = claimedCouponCodes || [];

  const totalDiscount = safeCoupons.reduce((sum, c) => sum + (c.discountAmount || 0), 0);
  const claimedCount = safeCoupons.filter((c) => safeClaimedCodes.includes(c.code)).length;
  const isAllClaimed = safeCoupons.length > 0 && claimedCount === safeCoupons.length;

  const filteredCoupons = safeCoupons.filter((coupon) => {
    if (selectedFilter === 'universal') return coupon.category === 'all';
    if (selectedFilter === 'big') return coupon.discountAmount >= 50;
    if (selectedFilter === 'category') return coupon.category !== 'all';
    return true;
  });

  return (
    // 券墙本体，只作为 `CouponCenterModal` 的内容渲染（首页常驻的是 `CouponCenterBanner` 那一行横幅）。
    // 锚点 id 与 `mb-8` 已移到横幅上，这里不再自带页面级间距。
    <section className="relative">
      {/* Container Box */}
      <div className="rounded-3xl border border-amber-200/80 bg-linear-to-br from-amber-500/5 via-orange-500/5 to-amber-500/10 p-5 sm:p-7 shadow-xs overflow-hidden relative">
        {/* Background decorative watermark */}
        <div className="absolute -right-8 -bottom-10 opacity-[0.04] pointer-events-none select-none text-zinc-900">
          <Ticket className="w-64 h-64" />
        </div>

        {/* Top Header Bar */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-5 border-b border-amber-200/60">
          <div className="flex items-start sm:items-center gap-3.5">
            <div className="w-12 h-12 rounded-2xl bg-linear-to-br from-amber-500 to-orange-600 text-white flex items-center justify-center shadow-md shadow-amber-500/20 shrink-0">
              <Ticket className="w-6 h-6 animate-pulse" />
            </div>
            <div>
              <div className="flex items-center gap-2.5 flex-wrap">
                <h2 className="text-lg sm:text-xl font-black text-zinc-900 tracking-tight flex items-center gap-2">
                  <span>领券中心</span>
                  <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-amber-100 text-amber-900 border border-amber-300/60 flex items-center gap-1">
                    <Sparkles className="w-3 h-3 text-amber-600" />
                    限时补贴
                  </span>
                </h2>
                <span className="text-xs text-zinc-500 font-medium hidden sm:inline">
                  已领 {claimedCount}/{coupons.length} 张 · 最高可省 ¥{totalDiscount}
                </span>
              </div>
              <p className="text-xs text-zinc-600 mt-1 flex items-center gap-1.5">
                <span>领券后存入个人账户，下单结算时系统将</span>
                <strong className="text-amber-800 font-semibold underline decoration-amber-400">自动关联最划算优惠</strong>
              </p>
            </div>
          </div>

          {/* Right Action: Claim All Button */}
          <div className="flex items-center gap-2.5 self-start md:self-center">
            <button
              id="claim-all-coupons-btn"
              onClick={onClaimAllCoupons}
              disabled={isAllClaimed}
              className={`px-5 py-2.5 rounded-2xl text-xs font-bold transition-all duration-200 flex items-center gap-2 shadow-sm ${
                isAllClaimed
                  ? 'bg-zinc-100 text-zinc-400 border border-zinc-200 cursor-default'
                  : 'bg-linear-to-r from-amber-600 via-orange-600 to-amber-700 hover:from-amber-700 hover:to-orange-700 text-white shadow-amber-600/25 hover:shadow-md hover:scale-[1.02] active:scale-[0.98]'
              }`}
            >
              {isAllClaimed ? (
                <>
                  <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                  <span>全部优惠券已领入账户</span>
                </>
              ) : (
                <>
                  <Zap className="w-4 h-4 fill-amber-200 text-amber-200 animate-bounce" />
                  <span>一键领取全部神券 (可省¥{totalDiscount})</span>
                </>
              )}
            </button>
          </div>
        </div>

        {/* Filter Categories */}
        <div className="flex items-center gap-2 pt-4 pb-2 overflow-x-auto no-scrollbar">
          {[
            { id: 'all', label: '全部优惠券', count: coupons.length },
            { id: 'universal', label: '全场通用券', count: coupons.filter((c) => c.category === 'all').length },
            { id: 'big', label: '大额神券 (¥50+)', count: coupons.filter((c) => c.discountAmount >= 50).length },
            { id: 'category', label: '品类专享券', count: coupons.filter((c) => c.category !== 'all').length },
          ].map((tab) => (
            <button
              key={tab.id}
              onClick={() => setSelectedFilter(tab.id as any)}
              className={`px-3.5 py-1.5 rounded-xl text-xs font-semibold whitespace-nowrap transition ${
                selectedFilter === tab.id
                  ? 'bg-zinc-900 text-white shadow-xs'
                  : 'bg-white/80 text-zinc-600 hover:bg-white hover:text-zinc-900 border border-amber-200/50'
              }`}
            >
              {tab.label}
              <span className={`ml-1.5 text-[11px] ${selectedFilter === tab.id ? 'text-zinc-300' : 'text-zinc-500'}`}>
                {tab.count}
              </span>
            </button>
          ))}
        </div>

        {/* Coupons Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3.5 pt-3">
          {filteredCoupons.map((coupon) => {
            const isClaimed = claimedCouponCodes.includes(coupon.code);
            const isCurrentlyApplied = appliedCoupon?.code === coupon.code;
            const stockPct = coupon.stockPercent || 75;

            return (
              <div
                key={coupon.code}
                id={`coupon-card-${coupon.code}`}
                className={`group relative rounded-2xl border transition-all duration-200 overflow-hidden flex flex-col justify-between ${
                  isCurrentlyApplied
                    ? 'bg-emerald-50/90 border-emerald-300 ring-2 ring-emerald-500/20 shadow-md'
                    : isClaimed
                    ? 'bg-white border-zinc-200 shadow-xs'
                    : coupon.highlight
                    ? 'bg-linear-to-r from-amber-50 to-orange-50/80 border-amber-300 shadow-xs hover:shadow-md hover:border-amber-400'
                    : 'bg-white border-amber-200/80 shadow-xs hover:shadow-md hover:border-amber-300'
                }`}
              >
                {/* Perforated ticket edges (left & right semicircle notches) */}
                <div className="absolute top-[68px] -left-2 w-4 h-4 rounded-full bg-zinc-50 border-r border-zinc-200 z-10"></div>
                <div className="absolute top-[68px] -right-2 w-4 h-4 rounded-full bg-zinc-50 border-l border-zinc-200 z-10"></div>

                {/* Top Section: Price & Title */}
                <div className="p-4 sm:p-4.5 pb-3">
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex items-baseline gap-1">
                      <span className="text-sm font-black text-rose-600">¥</span>
                      <span className="text-3xl sm:text-4xl font-black text-rose-600 tracking-tight font-mono">
                        {coupon.discountAmount}
                      </span>
                      <span className="text-xs font-semibold text-zinc-500 ml-1">
                        满 ¥{coupon.minSpend} 可用
                      </span>
                    </div>

                    {/* Tag badge */}
                    {coupon.tag && (
                      <span
                        className={`text-[10px] px-2 py-0.5 rounded-full font-bold uppercase tracking-wider ${
                          coupon.highlight
                            ? 'bg-rose-500 text-white'
                            : 'bg-amber-100 text-amber-900 border border-amber-300/60'
                        }`}
                      >
                        {couponTagLabel(coupon.tag)}
                      </span>
                    )}
                  </div>

                  <h3 className="text-xs sm:text-sm font-bold text-zinc-900 mt-2 truncate">
                    {coupon.title}
                  </h3>
                  <p className="text-[11px] text-zinc-500 mt-0.5 line-clamp-1">
                    {coupon.description}
                  </p>
                </div>

                {/* Dashed perforated divider */}
                <div className="relative border-b border-dashed border-zinc-200 mx-3 my-0"></div>

                {/* Bottom Section: Quota & Action Button */}
                <div className="p-3 sm:px-4 bg-zinc-50/60 flex items-center justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between text-[11px] text-zinc-500 font-medium mb-1">
                      <span>已抢 {stockPct}%</span>
                      <span className="flex items-center gap-0.5">
                        <Clock className="w-2.5 h-2.5" />
                        至 {coupon.expiresAt}
                      </span>
                    </div>
                    {/* Progress bar */}
                    <div className="w-full h-1.5 rounded-full bg-zinc-200 overflow-hidden">
                      <div
                        className={`h-full rounded-full ${
                          stockPct > 80
                            ? 'bg-rose-500'
                            : stockPct > 50
                            ? 'bg-amber-500'
                            : 'bg-emerald-500'
                        }`}
                        style={{ width: `${stockPct}%` }}
                      ></div>
                    </div>
                  </div>

                  {/* Claim / Use Button */}
                  <div className="shrink-0">
                    {isCurrentlyApplied ? (
                      <button
                        onClick={() => onUseCoupon && onUseCoupon(coupon)}
                        className="px-3 py-1.5 rounded-xl bg-emerald-600 text-white text-xs font-bold flex items-center gap-1 shadow-xs hover:bg-emerald-700 transition"
                      >
                        <Check className="w-3.5 h-3.5" />
                        <span>已关联结算</span>
                      </button>
                    ) : isClaimed ? (
                      <button
                        onClick={() => onUseCoupon && onUseCoupon(coupon)}
                        className="px-3 py-1.5 rounded-xl bg-zinc-900 hover:bg-zinc-800 text-white text-xs font-bold flex items-center gap-1 transition shadow-xs group-hover:bg-amber-600"
                        title="已在您的账户中，点击去使用"
                      >
                        <span>去使用</span>
                        <ChevronRight className="w-3 h-3" />
                      </button>
                    ) : (
                      <button
                        id={`claim-btn-${coupon.code}`}
                        onClick={() => onClaimCoupon(coupon)}
                        className="px-3.5 py-1.5 rounded-xl bg-rose-600 hover:bg-rose-700 active:scale-95 text-white text-xs font-bold transition flex items-center gap-1 shadow-xs shadow-rose-500/20"
                      >
                        <span>立即领取</span>
                      </button>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        {/* Bottom Rules & Tips bar */}
        <div className="mt-4 pt-3 border-t border-amber-200/50 flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-[11px] text-zinc-500">
          <div className="flex items-center gap-1.5 text-zinc-600">
            <Info className="w-3.5 h-3.5 text-amber-600 shrink-0" />
            <span>提示：领取后的优惠券将永久保存在您的账户中，在购物车或收银台勾选结算时，系统将智能优选为您自动减免。</span>
          </div>
          <div className="flex items-center gap-3 shrink-0 text-amber-900 font-semibold">
            <span>✓ 全场正品保障</span>
            <span>✓ 极速包邮</span>
            <span>✓ 7天无理由退换</span>
          </div>
        </div>
      </div>
    </section>
  );
};
