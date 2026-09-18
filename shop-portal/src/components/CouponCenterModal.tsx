import React, { useEffect } from 'react';
import { Coupon } from '../types/ecommerce';
import { X } from 'lucide-react';
import { CouponCenter } from './CouponCenter';

interface CouponCenterModalProps {
  open: boolean;
  onClose: () => void;
  coupons: Coupon[];
  claimedCouponCodes: string[];
  appliedCoupon: Coupon | null;
  onClaimCoupon: (coupon: Coupon) => void;
  onClaimAllCoupons: () => void;
  onUseCoupon?: (coupon: Coupon) => void;
}

/**
 * 领券中心的弹层外壳。
 * 券墙本体复用 `CouponCenter`（它自带琥珀色容器与水印），所以这里只负责遮罩、定位、关闭与 Escape。
 * 背景滚动锁由调用方（App）用 `useBodyScrollLock` 统一处理。
 */
export const CouponCenterModal: React.FC<CouponCenterModalProps> = ({
  open,
  onClose,
  coupons,
  claimedCouponCodes,
  appliedCoupon,
  onClaimCoupon,
  onClaimAllCoupons,
  onUseCoupon,
}) => {
  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-[60] flex items-start justify-center overflow-y-auto overscroll-contain bg-black/60 p-3 backdrop-blur-xs sm:p-6"
      role="presentation"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label="领券中心"
        // 必须给不透明底色：CouponCenter 的容器是 5%~10% 的琥珀半透明底，
        // 直接落在 bg-black/60 的遮罩上会让深色文字糊成一片。
        className="relative my-2 w-full max-w-4xl rounded-3xl bg-white shadow-2xl"
        onClick={(event) => event.stopPropagation()}
      >
        {/* 关闭按钮浮在券墙右上角外侧：券墙 header 右侧已是一键领取按钮，压在容器内会互相遮挡。 */}
        <button
          type="button"
          onClick={onClose}
          aria-label="关闭领券中心"
          className="absolute -right-1.5 -top-1.5 z-10 rounded-full bg-white p-2 text-zinc-900 shadow-lg ring-1 ring-zinc-900/10 transition hover:bg-zinc-100 sm:-right-2 sm:-top-2"
        >
          <X className="h-4 w-4" />
        </button>
        <CouponCenter
          coupons={coupons}
          claimedCouponCodes={claimedCouponCodes}
          appliedCoupon={appliedCoupon}
          onClaimCoupon={onClaimCoupon}
          onClaimAllCoupons={onClaimAllCoupons}
          onUseCoupon={onUseCoupon}
        />
      </div>
    </div>
  );
};
