import React from 'react';
import { Coupon } from '../types/ecommerce';
import { ChevronRight, Ticket } from 'lucide-react';

interface CouponCenterBannerProps {
  coupons: Coupon[];
  claimedCouponCodes: string[];
  /** 打开完整券墙弹层。 */
  onOpenAll: () => void;
  /** 一键领取全部可领券。 */
  onClaimAll: () => void;
}

/**
 * 首页领券位的常驻横幅：只占一行高。
 * 完整券墙在 `CouponCenterModal` 里，避免运营位把商品挤出首屏。
 */
export const CouponCenterBanner: React.FC<CouponCenterBannerProps> = ({
  coupons,
  claimedCouponCodes,
  onOpenAll,
  onClaimAll,
}) => {
  if (coupons.length === 0) return null;

  const claimedCount = coupons.filter((coupon) => claimedCouponCodes.includes(coupon.code)).length;
  const isAllClaimed = claimedCount === coupons.length;
  const remainingCount = coupons.length - claimedCount;
  const totalDiscount = coupons.reduce((sum, coupon) => sum + (coupon.discountAmount || 0), 0);
  // 拼成单条文案：React 会把相邻表达式拆成多个文本节点，散着写既不好读也不便断言。
  const summary = `已领 ${claimedCount}/${coupons.length} 张 · 最高可省 ¥${totalDiscount}`;

  return (
    <section id="coupon-center-section" aria-label="领券中心" className="mb-6 scroll-mt-24">
      <div className="flex flex-col gap-3 rounded-2xl border border-amber-200/80 bg-linear-to-r from-amber-500/10 via-orange-500/5 to-amber-500/15 px-4 py-3 sm:flex-row sm:items-center sm:justify-between sm:px-5">
        <div className="flex min-w-0 items-center gap-3">
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-linear-to-br from-amber-500 to-orange-600 text-white">
            <Ticket className="h-4 w-4" />
          </span>
          <div className="min-w-0">
            <p className="flex items-center gap-2 text-sm font-bold text-zinc-900">
              <span>领券中心</span>
              <span className="rounded-full border border-amber-300/60 bg-amber-100 px-2 py-0.5 text-[11px] font-semibold text-amber-900">
                {isAllClaimed ? '已全部领取' : `${remainingCount} 张可领`}
              </span>
            </p>
            <p className="truncate text-xs text-zinc-500">{summary}</p>
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-2">
          <button
            type="button"
            id="coupon-banner-claim-all"
            onClick={onClaimAll}
            disabled={isAllClaimed}
            className={`rounded-xl px-3.5 py-2 text-xs font-bold transition ${
              isAllClaimed
                ? 'cursor-default border border-zinc-200 bg-zinc-100 text-zinc-400'
                : 'bg-linear-to-r from-amber-600 to-orange-600 text-white shadow-xs hover:from-amber-700 hover:to-orange-700'
            }`}
          >
            {isAllClaimed ? '已全部领取' : '一键领取'}
          </button>
          <button
            type="button"
            onClick={onOpenAll}
            aria-label="查看全部优惠券"
            className="flex items-center gap-1 rounded-xl border border-amber-300/70 bg-white/80 px-3.5 py-2 text-xs font-bold text-amber-900 transition hover:bg-white"
          >
            <span>查看全部券</span>
            <ChevronRight className="h-3.5 w-3.5" />
          </button>
        </div>
      </div>
    </section>
  );
};
