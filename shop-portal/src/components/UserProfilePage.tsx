import React, { useEffect, useRef, useState } from 'react';
import { couponTagLabel } from '../utils/couponTag';
import {
  User,
  Crown,
  Sparkles,
  Ticket,
  Package,
  Heart,
  ShieldCheck,
  LogOut,
  Edit3,
  Check,
  Smartphone,
  Mail,
  Calendar,
  Layers,
  Star,
} from 'lucide-react';
import { UserProfile, MemberLevel, Coupon, Order, Product } from '../types/ecommerce';
import type { AccountSection } from '../utils/portalRoute';
import {
  PortalReviewRecord,
  fetchPortalMyReviews,
  submitPortalReviewFollowup,
  parsePortalReviewImageUrls,
  fetchPortalProductDetail,
} from '../api/portalApi';

interface UserProfilePageProps {
  user: UserProfile;
  claimedCoupons?: Coupon[];
  /** 当前会员订单快照，用于展示消费统计和快捷查看订单明细。 */
  orders?: Order[];
  /** 已加载的商品，用于把评价里的商品ID还原成商品名。 */
  products?: Product[];
  /**
   * 子视图，由地址承载（`#/account`、`#/account/coupons`、`#/account/reviews`）。
   * 刷新、分享链接、前进后退都能落回同一屏，不再靠组件内开关。
   */
  section?: AccountSection;
  /** 需要高亮定位的评价所属商品ID，订单与商品详情的「查看评价」入口带进来。 */
  highlightProductId?: number | null;
  onSelectSection: (section: AccountSection) => void;
  /** 到订单中心与收藏页的快捷入口，和顶栏走同一套路由。 */
  onOpenOrders: () => void;
  onOpenWishlist: () => void;
  /** 返回商城首页。整页视图不占侧边导航，靠这条回主页。 */
  onBackToHome: () => void;
  onUpdateUser: (updatedUser: UserProfile) => void;
  onLogout: () => void;
  onOpenCouponCenter?: () => void;
}

/** 从门户商品 ID 中解析后端商品主键。 */
function toCatalogProductId(productId: string): number {
  return Number(productId.replace(/^prod-/, ''));
}

/** 评价审核状态对应的展示文案与配色。 */
function reviewStatusMeta(status: number): { label: string; className: string } {
  if (status === 1) return { label: '已通过', className: 'border-emerald-200 bg-emerald-50 text-emerald-700' };
  if (status === 2) return { label: '已隐藏', className: 'border-zinc-200 bg-zinc-100 text-zinc-500' };
  return { label: '待审核', className: 'border-amber-200 bg-amber-50 text-amber-700' };
}

const AVATAR_OPTIONS = [
  'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&auto=format&fit=crop&q=80',
  'https://images.unsplash.com/photo-1580489944761-15a19d654956?w=150&auto=format&fit=crop&q=80',
  'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150&auto=format&fit=crop&q=80',
  'https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=150&auto=format&fit=crop&q=80',
  'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150&auto=format&fit=crop&q=80',
  'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&auto=format&fit=crop&q=80',
];

/** 个人中心的三个子视图，与地址里的路径段一一对应。 */
const SECTIONS: { key: AccountSection; label: string }[] = [
  { key: 'profile', label: '资料与特权' },
  { key: 'coupons', label: '我的券包' },
  { key: 'reviews', label: '我的评价' },
];

/**
 * 门户「个人中心」整页。
 *
 * 与旧弹层的区别：三个子视图（资料 / 券包 / 我的评价）从组件内开关改成路由段，
 * 券包可以直接刷新与分享，评价定位也不再依赖「递增标记」这类仅存在于内存的传递方式；
 * 壳上补面包屑与页面级 h1，去掉遮罩、滚动锁与「关闭」语义（退出登录直接回首页）。
 */
export const UserProfilePage: React.FC<UserProfilePageProps> = ({
  user,
  claimedCoupons = [],
  orders = [],
  products = [],
  section = 'profile',
  highlightProductId = null,
  onSelectSection,
  onOpenOrders,
  onOpenWishlist,
  onBackToHome,
  onUpdateUser,
  onLogout,
  onOpenCouponCenter,
}: UserProfilePageProps) => {
  const [isEditing, setIsEditing] = useState(false);
  const [nickname, setNickname] = useState(user.nickname || '');
  const [email, setEmail] = useState(user.email || '');
  const [phone, setPhone] = useState(user.phone || '');
  const [selectedAvatar, setSelectedAvatar] = useState(user.avatar || AVATAR_OPTIONS[0]);
  const [myReviews, setMyReviews] = useState<PortalReviewRecord[]>([]);
  const [myReviewsLoading, setMyReviewsLoading] = useState(false);
  const [myReviewsError, setMyReviewsError] = useState<string | null>(null);
  const [reviewProductTitles, setReviewProductTitles] = useState<Record<number, string>>({});
  const [followupDraft, setFollowupDraft] = useState<Record<number, string>>({});
  const [followupSubmittingId, setFollowupSubmittingId] = useState<number | null>(null);
  /** 高亮评价项的引用，用于从订单「查看」进入时滚动定位。 */
  const highlightReviewRef = useRef<HTMLDivElement | null>(null);

  // 进入「我的评价」后把目标商品的那条评价滚到视野内，避免评价较多时用户还要自己找。
  useEffect(() => {
    if (section !== 'reviews' || highlightProductId == null) return;
    const timer = window.setTimeout(() => {
      highlightReviewRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }, 150);
    return () => window.clearTimeout(timer);
  }, [section, highlightProductId, myReviews]);

  // 进页即加载自己的评价（资料页要用它显示条数），后端评价只存商品ID，这里补齐商品名。
  useEffect(() => {
    let active = true;
    setMyReviewsLoading(true);
    setMyReviewsError(null);
    fetchPortalMyReviews(1, 20)
      .then(async (page) => {
        if (!active) return;
        const records = page.records || [];
        setMyReviews(records);
        const titles = new Map(products.map((product) => [toCatalogProductId(product.id), product.title]));
        const missingIds = Array.from(new Set(
          records.map((record) => record.productId).filter((id) => !titles.has(id)),
        ));
        if (missingIds.length > 0) {
          const details = await Promise.all(
            missingIds.map((id) => fetchPortalProductDetail(`prod-${id}`).catch(() => null)),
          );
          if (!active) return;
          details.forEach((product) => {
            if (product) titles.set(toCatalogProductId(product.id), product.title);
          });
        }
        setReviewProductTitles(Object.fromEntries(titles));
      })
      .catch((error) => {
        if (active) setMyReviewsError(error instanceof Error ? error.message : '评价加载失败，请稍后重试');
      })
      .finally(() => {
        if (active) setMyReviewsLoading(false);
      });
    return () => {
      active = false;
    };
  }, [user, products]);

  /** 提交追评并就地更新当前列表。 */
  const submitFollowup = async (reviewId: number) => {
    const content = (followupDraft[reviewId] || '').trim();
    if (!content) return;
    setFollowupSubmittingId(reviewId);
    setMyReviewsError(null);
    try {
      const updated = await submitPortalReviewFollowup(reviewId, content);
      setMyReviews((current) => current.map((item) => (item.id === reviewId ? { ...item, ...updated } : item)));
      setFollowupDraft((current) => ({ ...current, [reviewId]: '' }));
    } catch (error) {
      setMyReviewsError(error instanceof Error ? error.message : '追评提交失败，请稍后重试');
    } finally {
      setFollowupSubmittingId(null);
    }
  };

  const handleSaveProfile = (e: React.FormEvent) => {
    e.preventDefault();
    onUpdateUser({
      ...user,
      nickname,
      email,
      phone,
      avatar: selectedAvatar,
    });
    setIsEditing(false);
  };

  const getMemberLevelColor = (level: MemberLevel) => {
    switch (level) {
      case '黑金SVIP':
        return {
          bg: 'bg-zinc-950 text-amber-300 border-amber-400/40',
          badge: '黑金SVIP · 专属9.2折',
          perks: ['全场自营享 9.2 折专享价', '每月赠送 3 张免邮券', '购物享 2 倍积分返还', '1对1专属私享管家'],
        };
      case '黄金VIP':
        return {
          bg: 'bg-amber-500/10 text-amber-900 border-amber-400/40',
          badge: '黄金VIP · 专属9.5折',
          perks: ['全场自营享 9.5 折专享价', '每月赠送 1 张免邮券', '购物享 1.5 倍积分返还', '优先极速退款通道'],
        };
      default:
        return {
          bg: 'bg-zinc-100 text-zinc-800 border-zinc-200',
          badge: '普通会员',
          perks: ['注册即享新人礼包', '实付满 ¥99 包邮', '购物按 1:1 累计积分', '7天无理由退换'],
        };
    }
  };

  const levelInfo = getMemberLevelColor(user.memberLevel);
  const paidOrders = orders.filter((order) => order.paymentState === 'succeeded' || order.status === 'paid' || order.status === 'processing' || order.status === 'shipped' || order.status === 'delivered');
  const totalSpent = paidOrders.reduce((sum, order) => sum + Number(order.totalPaid || 0), 0);

  return (
    <div className="space-y-5">
      <nav aria-label="面包屑" className="flex items-center gap-1.5 text-xs text-zinc-500">
        <button type="button" onClick={onBackToHome} className="transition hover:text-zinc-900">
          商城首页
        </button>
        <span className="text-zinc-300">/</span>
        <span className="font-semibold text-zinc-900">个人中心</span>
      </nav>

      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-zinc-900">个人中心</h1>
          <p className="mt-1 text-xs text-zinc-500">
            账号资料、{user.memberLevel} 特权、券包与我的评价
          </p>
        </div>
        <button
          type="button"
          onClick={onBackToHome}
          className="rounded-lg border border-zinc-200 bg-white px-3 py-2 text-xs font-semibold text-zinc-600 transition hover:border-zinc-300 hover:text-zinc-900"
        >
          返回首页继续逛
        </button>
      </div>

      {/* 子视图切换：三个子视图各有一条地址，刷新与分享链接都能落回同一屏。 */}
      <nav aria-label="个人中心分栏" className="flex flex-wrap gap-1.5 rounded-2xl border border-zinc-200/90 bg-white p-2 shadow-xs">
        {SECTIONS.map(({ key, label }) => {
          const isActive = section === key;
          return (
            <button
              key={key}
              type="button"
              aria-current={isActive ? 'page' : undefined}
              onClick={() => {
                setIsEditing(false);
                onSelectSection(key);
              }}
              className={`rounded-xl px-3 py-2 text-xs font-semibold transition ${
                isActive ? 'bg-zinc-900 text-white' : 'text-zinc-600 hover:bg-zinc-100 hover:text-zinc-900'
              }`}
            >
              {label}
              {key === 'reviews' && myReviews.length > 0 && (
                <span className={`ml-1.5 font-mono text-[11px] ${isActive ? 'text-zinc-300' : 'text-zinc-400'}`}>
                  {myReviews.length}
                </span>
              )}
            </button>
          );
        })}
      </nav>

      <div className="overflow-hidden rounded-3xl border border-zinc-200/90 bg-white shadow-xs">
        {/* Header with VIP Banner */}
        <div className="bg-zinc-900 text-white p-6 pb-8 relative overflow-hidden">
          <div className="absolute -right-12 -top-12 w-48 h-48 bg-amber-400/15 rounded-full blur-3xl pointer-events-none" />

          {/* User Info Header */}
          <div className="flex items-center gap-4">
            <div className="relative">
              <img
                src={isEditing ? selectedAvatar : user.avatar}
                alt={user.nickname}
                className="w-16 h-16 rounded-2xl object-cover ring-2 ring-amber-400/60 bg-zinc-800 shadow-md"
              />
              <div className="absolute -bottom-1 -right-1 p-1 rounded-full bg-amber-400 text-zinc-950 shadow-sm">
                <Crown className="w-3.5 h-3.5" />
              </div>
            </div>

            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <h2 className="text-lg font-black text-white truncate">{user.nickname}</h2>
                <span
                  className={`text-[10px] px-2 py-0.5 rounded-full font-bold border ${levelInfo.bg}`}
                >
                  {user.memberLevel}
                </span>
              </div>
              <p className="text-xs text-zinc-400 mt-0.5">账号: @{user.username}</p>
              <p className="text-[11px] text-zinc-500 flex items-center gap-1 mt-1">
                <Calendar className="w-3 h-3" />
                注册于 {user.joinedDate}
              </p>
            </div>

            {!isEditing && section === 'profile' && (
              <button
                onClick={() => {
                  setNickname(user.nickname);
                  setEmail(user.email);
                  setPhone(user.phone);
                  setSelectedAvatar(user.avatar);
                  setIsEditing(true);
                }}
                className="px-3 py-1.5 rounded-xl bg-zinc-800 hover:bg-zinc-700 text-zinc-200 text-xs font-semibold flex items-center gap-1.5 transition border border-zinc-700/80"
              >
                <Edit3 className="w-3.5 h-3.5" />
                <span>编辑资料</span>
              </button>
            )}
          </div>

          {/* Asset summary pill row */}
          <div className="grid grid-cols-3 gap-2 mt-5 bg-zinc-800/80 p-3 rounded-2xl border border-zinc-700/60">
            <div className="text-center">
              <span className="text-[10px] text-zinc-400 block font-medium">商城积分</span>
              <span className="text-sm font-black text-amber-400">{user.points.toLocaleString()}</span>
            </div>
            <div className="text-center border-x border-zinc-700/80">
              <span className="text-[10px] text-zinc-400 block font-medium">账户余额</span>
              <span className="text-sm font-black text-white">¥{user.balance.toFixed(2)}</span>
            </div>
            <button
              type="button"
              onClick={() => onSelectSection('coupons')}
              className="text-center hover:bg-zinc-700/50 rounded-xl p-0.5 transition cursor-pointer"
            >
              <span className="text-[10px] text-zinc-400 block font-medium">可用优惠券</span>
              <span className="text-sm font-black text-amber-300 flex items-center justify-center gap-1">
                <Ticket className="w-3.5 h-3.5" />
                {claimedCoupons.length || user.couponsCount} 张
              </span>
            </button>
          </div>
          <div className="grid grid-cols-2 gap-2 mt-2 text-center">
            <div className="rounded-xl bg-zinc-800/60 border border-zinc-700/60 px-2 py-2">
              <span className="text-[10px] text-zinc-400 block">累计消费</span>
              <span className="text-sm font-black text-emerald-300">¥{totalSpent.toFixed(2)}</span>
            </div>
            <div className="rounded-xl bg-zinc-800/60 border border-zinc-700/60 px-2 py-2">
              <span className="text-[10px] text-zinc-400 block">已完成订单</span>
              <span className="text-sm font-black text-sky-300">{paidOrders.length} 笔</span>
            </div>
          </div>
        </div>

        {/* Page Body */}
        <div className="p-6 space-y-5">
          {section === 'coupons' ? (
            /* My Claimed Coupons View */
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Ticket className="w-4 h-4 text-amber-600" />
                  <h2 className="text-sm font-bold text-zinc-900">我的优惠券包 ({claimedCoupons.length} 张)</h2>
                </div>
                {onOpenCouponCenter && (
                  <button
                    onClick={onOpenCouponCenter}
                    className="text-xs text-amber-700 hover:text-amber-900 font-bold underline"
                  >
                    领更多神券 &gt;
                  </button>
                )}
              </div>

              {claimedCoupons.length === 0 ? (
                <div className="py-8 text-center bg-zinc-50 rounded-2xl border border-dashed border-zinc-200 space-y-2">
                  <Ticket className="w-8 h-8 text-zinc-300 mx-auto" />
                  <p className="text-xs text-zinc-500 font-medium">暂无领取的优惠券</p>
                  {onOpenCouponCenter && (
                    <button
                      onClick={onOpenCouponCenter}
                      className="px-4 py-1.5 rounded-xl bg-zinc-900 text-white text-xs font-bold hover:bg-zinc-800 transition shadow-xs"
                    >
                      前往领券中心免费领取
                    </button>
                  )}
                </div>
              ) : (
                <div className="space-y-2.5 max-h-96 overflow-y-auto pr-1">
                  {claimedCoupons.map((c) => (
                    <div
                      key={c.code}
                      className="p-3 rounded-2xl border border-amber-200/80 bg-linear-to-r from-amber-50/70 to-white flex items-center justify-between gap-3 shadow-2xs"
                    >
                      <div className="flex items-center gap-3">
                        <div className="w-12 h-12 rounded-xl bg-amber-500/10 border border-amber-300/60 text-rose-600 flex flex-col items-center justify-center shrink-0">
                          <span className="text-[10px] font-bold leading-none">¥</span>
                          <span className="text-lg font-black leading-none font-mono">{c.discountAmount}</span>
                        </div>
                        <div>
                          <div className="flex items-center gap-1.5">
                            <h3 className="text-xs font-bold text-zinc-900">{c.title}</h3>
                            {c.tag && (
                              <span className="text-[9px] px-1.5 py-0.2 rounded bg-amber-100 text-amber-800 font-bold">
                                {couponTagLabel(c.tag)}
                              </span>
                            )}
                          </div>
                          <p className="text-[11px] text-zinc-500 mt-0.5">满 ¥{c.minSpend} 可用 · {c.description}</p>
                          <span className="text-[10px] text-zinc-400">有效期至 {c.expiresAt}</span>
                        </div>
                      </div>

                      <button
                        onClick={onBackToHome}
                        className="px-3 py-1.5 rounded-xl bg-zinc-900 text-white text-xs font-semibold hover:bg-zinc-800 transition shrink-0"
                      >
                        去使用
                      </button>
                    </div>
                  ))}
                </div>
              )}

              <button
                type="button"
                onClick={() => onSelectSection('profile')}
                className="w-full py-2 rounded-xl border border-zinc-200 text-xs font-semibold text-zinc-600 hover:bg-zinc-50 transition"
              >
                返回个人资料
              </button>
            </div>
          ) : section === 'reviews' ? (
            /* My Reviews View */
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Star className="w-4 h-4 text-amber-500" />
                  <h2 className="text-sm font-bold text-zinc-900">
                    我的评价
                    {myReviews.length > 0 && <span className="ml-1 text-[11px] font-bold text-zinc-400">({myReviews.length})</span>}
                  </h2>
                </div>
                <button
                  type="button"
                  onClick={() => onSelectSection('profile')}
                  className="text-[11px] font-bold text-sky-700 hover:text-sky-900"
                >
                  返回个人资料
                </button>
              </div>

              <div className="space-y-2.5">
                {myReviewsLoading && <p className="text-xs text-zinc-400 py-2">评价加载中…</p>}
                {!myReviewsLoading && myReviewsError && (
                  <p className="rounded-lg bg-rose-50 px-2.5 py-2 text-[11px] text-rose-600">{myReviewsError}</p>
                )}
                {!myReviewsLoading && myReviews.length === 0 && (
                  <p className="text-xs text-zinc-400 py-2">还没有提交过评价，可在我的订单中点击「评价」</p>
                )}
                {myReviews.map((review) => {
                  const statusMeta = reviewStatusMeta(review.status);
                  const reviewImages = parsePortalReviewImageUrls(review.imageUrls);
                  const isHighlighted = highlightProductId != null && review.productId === highlightProductId;
                  return (
                    <div
                      key={review.id}
                      ref={isHighlighted ? highlightReviewRef : undefined}
                      className={`rounded-xl border p-3 space-y-1.5 ${isHighlighted
                        ? 'border-sky-300 bg-sky-50/60 ring-1 ring-sky-200'
                        : 'border-zinc-100 bg-zinc-50/70'}`}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="truncate text-[11px] font-bold text-zinc-900">
                          {reviewProductTitles[review.productId] || `商品 #${review.productId}`}
                        </span>
                        <span className={`shrink-0 rounded-md border px-1.5 py-0.5 text-[10px] font-semibold ${statusMeta.className}`}>
                          {statusMeta.label}
                        </span>
                      </div>
                      <div className="flex items-center gap-0.5">
                        {[1, 2, 3, 4, 5].map((score) => (
                          <Star key={score} className={`h-3 w-3 ${score <= review.rating ? 'fill-amber-400 text-amber-400' : 'text-zinc-300'}`} />
                        ))}
                        <span className="ml-1.5 text-[10px] text-zinc-400">{review.variantSummary || '默认规格'}</span>
                      </div>
                      <p className="text-[11px] leading-relaxed text-zinc-700">{review.reviewContent}</p>
                      {reviewImages.length > 0 && (
                        <div className="flex flex-wrap gap-1.5">
                          {reviewImages.map((url) => (
                            <a key={url} href={url} target="_blank" rel="noreferrer">
                              <img src={url} alt="评价图片" className="h-10 w-10 rounded-md border border-zinc-200 object-cover" />
                            </a>
                          ))}
                        </div>
                      )}
                      {review.replyContent && (
                        <p className="rounded-md border border-zinc-100 bg-white px-2 py-1 text-[10px] text-zinc-600">
                          <span className="font-bold text-zinc-800">商家回复：</span>{review.replyContent}
                        </p>
                      )}
                      {review.followupContent ? (
                        <p className="rounded-md bg-sky-50 px-2 py-1 text-[10px] text-sky-800">
                          <span className="font-bold">我的追评：</span>{review.followupContent}
                        </p>
                      ) : review.status === 1 ? (
                        <div className="space-y-1.5 pt-0.5">
                          <textarea
                            rows={2}
                            maxLength={500}
                            value={followupDraft[review.id] || ''}
                            onChange={(event) => setFollowupDraft((current) => ({ ...current, [review.id]: event.target.value }))}
                            placeholder="补充使用体验（追评）"
                            className="w-full resize-none rounded-lg border border-zinc-200 px-2 py-1.5 text-[11px] outline-none focus:border-zinc-400"
                          />
                          <button
                            type="button"
                            disabled={followupSubmittingId === review.id || !(followupDraft[review.id] || '').trim()}
                            onClick={() => void submitFollowup(review.id)}
                            className="rounded-lg bg-zinc-900 px-2.5 py-1 text-[10px] font-bold text-white disabled:opacity-50"
                          >
                            {followupSubmittingId === review.id ? '提交中…' : '提交追评'}
                          </button>
                        </div>
                      ) : null}
                    </div>
                  );
                })}
              </div>
            </div>
          ) : isEditing ? (
            /* Edit form */
            <form onSubmit={handleSaveProfile} className="space-y-4">
              <div className="space-y-2">
                <label className="text-xs font-bold text-zinc-700 block">选择头像</label>
                <div className="flex items-center gap-2 overflow-x-auto pb-1">
                  {AVATAR_OPTIONS.map((imgUrl, idx) => (
                    <button
                      type="button"
                      key={idx}
                      onClick={() => setSelectedAvatar(imgUrl)}
                      className={`relative rounded-xl overflow-hidden shrink-0 transition ${
                        selectedAvatar === imgUrl
                          ? 'ring-2 ring-amber-500 scale-105 shadow-sm'
                          : 'opacity-70 hover:opacity-100'
                      }`}
                    >
                      <img src={imgUrl} alt={`avatar-${idx}`} className="w-11 h-11 object-cover" />
                      {selectedAvatar === imgUrl && (
                        <div className="absolute inset-0 bg-amber-500/20 flex items-center justify-center">
                          <Check className="w-4 h-4 text-zinc-900" />
                        </div>
                      )}
                    </button>
                  ))}
                </div>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-bold text-zinc-700 block">用户昵称</label>
                <div className="relative flex items-center">
                  <User className="w-4 h-4 text-zinc-400 absolute left-3 pointer-events-none" />
                  <input
                    type="text"
                    required
                    value={nickname}
                    onChange={(e) => setNickname(e.target.value)}
                    className="w-full py-2 pl-9 pr-3 bg-zinc-50 border border-zinc-200 rounded-xl text-xs text-zinc-900 focus:bg-white focus:border-zinc-900 focus:outline-none"
                  />
                </div>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-bold text-zinc-700 block">绑定邮箱</label>
                <div className="relative flex items-center">
                  <Mail className="w-4 h-4 text-zinc-400 absolute left-3 pointer-events-none" />
                  <input
                    type="email"
                    required
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    className="w-full py-2 pl-9 pr-3 bg-zinc-50 border border-zinc-200 rounded-xl text-xs text-zinc-900 focus:bg-white focus:border-zinc-900 focus:outline-none"
                  />
                </div>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-bold text-zinc-700 block">绑定手机</label>
                <div className="relative flex items-center">
                  <Smartphone className="w-4 h-4 text-zinc-400 absolute left-3 pointer-events-none" />
                  <input
                    type="tel"
                    required
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    className="w-full py-2 pl-9 pr-3 bg-zinc-50 border border-zinc-200 rounded-xl text-xs text-zinc-900 focus:bg-white focus:border-zinc-900 focus:outline-none"
                  />
                </div>
              </div>

              <div className="flex items-center gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setIsEditing(false)}
                  className="flex-1 py-2.5 rounded-xl border border-zinc-200 text-xs font-semibold text-zinc-600 hover:bg-zinc-50 transition"
                >
                  取消
                </button>
                <button
                  type="submit"
                  className="flex-1 py-2.5 rounded-xl bg-zinc-900 text-white text-xs font-bold hover:bg-zinc-800 transition shadow-sm"
                >
                  保存修改
                </button>
              </div>
            </form>
          ) : (
            /* Standard Profile details */
            <>
              {/* Member Privileges Card */}
              <div className="bg-amber-50/70 border border-amber-200/80 rounded-2xl p-4 space-y-2.5">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-black text-amber-950 flex items-center gap-1.5">
                    <Crown className="w-4 h-4 text-amber-600" />
                    当前享有 {user.memberLevel} 特权
                  </span>
                  <span className="text-[11px] text-amber-700 font-semibold">{levelInfo.badge}</span>
                </div>

                <div className="grid grid-cols-2 gap-2 text-[11px] text-amber-900/90 pt-1">
                  {levelInfo.perks.map((perk, i) => (
                    <div key={i} className="flex items-center gap-1.5">
                      <Sparkles className="w-3 h-3 text-amber-500 shrink-0" />
                      <span>{perk}</span>
                    </div>
                  ))}
                </div>
              </div>

              {/* Quick Navigation Action Grid */}
              <div className="grid grid-cols-2 gap-2.5">
                <button
                  onClick={onOpenOrders}
                  className="p-3 rounded-2xl bg-zinc-50 hover:bg-zinc-100 border border-zinc-200/70 text-left transition flex items-center justify-between group"
                >
                  <div className="flex items-center gap-2.5">
                    <div className="w-8 h-8 rounded-xl bg-zinc-900 text-white flex items-center justify-center">
                      <Package className="w-4 h-4" />
                    </div>
                    <div>
                      <span className="text-xs font-bold text-zinc-900 block">我的订单</span>
                      <span className="text-[10px] text-zinc-500">查看实时物流</span>
                    </div>
                  </div>
                </button>

                <button
                  onClick={onOpenWishlist}
                  className="p-3 rounded-2xl bg-zinc-50 hover:bg-zinc-100 border border-zinc-200/70 text-left transition flex items-center justify-between group"
                >
                  <div className="flex items-center gap-2.5">
                    <div className="w-8 h-8 rounded-xl bg-rose-500 text-white flex items-center justify-center">
                      <Heart className="w-4 h-4" />
                    </div>
                    <div>
                      <span className="text-xs font-bold text-zinc-900 block">心愿收藏</span>
                      <span className="text-[10px] text-zinc-500">降价实时提醒</span>
                    </div>
                  </div>
                </button>

                <button
                  onClick={() => onSelectSection('coupons')}
                  className="p-3 rounded-2xl bg-zinc-50 hover:bg-zinc-100 border border-zinc-200/70 text-left transition flex items-center justify-between group"
                >
                  <div className="flex items-center gap-2.5">
                    <div className="w-8 h-8 rounded-xl bg-amber-500 text-white flex items-center justify-center">
                      <Ticket className="w-4 h-4" />
                    </div>
                    <div>
                      <span className="text-xs font-bold text-zinc-900 block">我的券包</span>
                      <span className="text-[10px] text-zinc-500">{claimedCoupons.length || user.couponsCount} 张可用优惠券</span>
                    </div>
                  </div>
                </button>

                <button
                  onClick={() => onSelectSection('reviews')}
                  className="p-3 rounded-2xl bg-zinc-50 hover:bg-zinc-100 border border-zinc-200/70 text-left transition flex items-center justify-between group"
                >
                  <div className="flex items-center gap-2.5">
                    <div className="w-8 h-8 rounded-xl bg-amber-400 text-zinc-950 flex items-center justify-center">
                      <Star className="w-4 h-4" />
                    </div>
                    <div>
                      <span className="text-xs font-bold text-zinc-900 block">我的评价</span>
                      <span className="text-[10px] text-zinc-500">{myReviews.length} 条 · 审核后可追评</span>
                    </div>
                  </div>
                </button>
              </div>

              <div className="rounded-2xl border border-zinc-200 bg-white p-4">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs font-black text-zinc-900 flex items-center gap-1.5"><Layers className="w-4 h-4 text-sky-600" />最近订单明细</span>
                  <button type="button" onClick={onOpenOrders} className="text-[11px] font-bold text-sky-700 hover:text-sky-900">查看全部</button>
                </div>
                {orders.length === 0 ? <p className="text-xs text-zinc-400 py-2">暂无订单记录</p> : (
                  <div className="space-y-2">
                    {orders.slice(0, 3).map((order) => (
                      <div key={order.id} className="flex items-center justify-between text-[11px] border-b border-zinc-100 pb-2 last:border-0 last:pb-0">
                        <div className="min-w-0"><p className="font-semibold text-zinc-800 truncate">{order.orderNumber}</p><p className="text-zinc-400">{order.createdAt?.slice(0, 10) || '-'} · {order.items.length} 件</p></div>
                        <span className="font-bold text-zinc-900">¥{Number(order.totalPaid || 0).toFixed(2)}</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Security & Account Details List */}
              <div className="space-y-2 border-t border-zinc-100 pt-3">
                <div className="flex items-center justify-between text-xs py-1">
                  <span className="text-zinc-500">绑定手机</span>
                  <span className="font-semibold text-zinc-800">{user.phone}</span>
                </div>
                <div className="flex items-center justify-between text-xs py-1">
                  <span className="text-zinc-500">绑定邮箱</span>
                  <span className="font-semibold text-zinc-800">{user.email}</span>
                </div>
                <div className="flex items-center justify-between text-xs py-1">
                  <span className="text-zinc-500">账号安全级别</span>
                  <span className="font-bold text-emerald-600 flex items-center gap-1">
                    <ShieldCheck className="w-3.5 h-3.5" />
                    高 (已开通双重保护)
                  </span>
                </div>
              </div>
            </>
          )}
        </div>

        {/* Bottom Actions */}
        <div className="bg-zinc-50 border-t border-zinc-100 p-4 px-6 flex items-center justify-between">
          <button
            onClick={onLogout}
            className="px-3.5 py-2 rounded-xl text-xs font-semibold text-rose-600 hover:bg-rose-50 border border-rose-200/80 transition flex items-center gap-1.5"
          >
            <LogOut className="w-3.5 h-3.5" />
            <span>退出登录</span>
          </button>

          <button
            onClick={onBackToHome}
            className="px-5 py-2 rounded-xl bg-zinc-900 text-white text-xs font-bold hover:bg-zinc-800 transition"
          >
            返回商城首页
          </button>
        </div>
      </div>
    </div>
  );
};
