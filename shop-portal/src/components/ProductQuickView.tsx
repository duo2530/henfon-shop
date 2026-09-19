import React, { useState, useRef, useEffect, useMemo } from 'react';
import { useBodyScrollLock } from '../hooks/useBodyScrollLock';
import { Product, ProductReview } from '../types/ecommerce';
import { fetchPortalProductReviews, hasPortalMemberSession, submitPortalProductReview, uploadPortalMedia } from '../api/portalApi';
import {
  X,
  Star,
  ShoppingBag,
  Zap,
  Truck,
  ShieldCheck,
  RotateCcw,
  Check,
  Plus,
  Minus,
  Heart,
  Play,
  Pause,
  Volume2,
  VolumeX,
  Maximize2,
  ChevronLeft,
  ChevronRight,
  Film,
} from 'lucide-react';

function mapPortalReview(review: Awaited<ReturnType<typeof fetchPortalProductReviews>>['records'][number]): ProductReview {
  const imageUrls = Array.isArray(review.imageUrls)
    ? review.imageUrls
    : (() => {
      if (!review.imageUrls) return [];
      try {
        const parsed = JSON.parse(review.imageUrls);
        return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : [];
      } catch {
        return [];
      }
    })();
  return {
    id: String(review.id),
    userName: review.memberName,
    userAvatar: review.memberAvatarUrl || '',
    rating: review.rating,
    date: (review.reviewedAt || review.createdAt || '').slice(0, 10),
    comment: review.reviewContent,
    variantUsed: review.variantSummary,
    helpfulCount: review.helpfulCount,
    replyContent: review.replyContent,
    replyDate: review.repliedAt ? review.repliedAt.slice(0, 10) : undefined,
    imageUrls,
  };
}

/**
 * 清理商品富文本，仅保留安全的展示标签。
 * @author Henfon
 * @date 2026-09-04
 * @description 移除脚本、事件属性和危险协议，避免后台富文本影响门户页面安全。
 */
export function sanitizeProductRichText(value: string): string {
  if (!value) return '';
  return value
    .replace(/<\s*(script|style|iframe|object|embed)[^>]*>[\s\S]*?<\s*\/\s*\1\s*>/gi, '')
    .replace(/<\/?(script|style|iframe|object|embed)[^>]*>/gi, '')
    .replace(/<[^>]*\s+on[a-z-]+\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)/gi, '')
    .replace(/javascript\s*:/gi, '')
    .trim();
}

/**
 * 播放视频，并吞掉「播放被中断」这一类的正常拒绝。
 *
 * video.play() 返回的 Promise 在兑现前遇到 pause() 或元素卸载会抛 AbortError：
 * 悬停快速进出、切换媒体、关闭弹窗都会触发，属于预期行为，不该当作播放失败刷控制台。
 * 只有其它错误（如格式不支持）才值得告警。
 *
 * @param video 视频元素
 * @param onPlaying 开始播放后的回调
 */
function playVideoSafely(video: HTMLVideoElement, onPlaying?: () => void) {
  const playback = video.play();
  if (!playback || typeof playback.then !== 'function') {
    onPlaying?.();
    return;
  }
  playback
    .then(() => onPlaying?.())
    .catch((error: unknown) => {
      if (error instanceof DOMException && error.name === 'AbortError') return;
      console.warn('商品视频播放失败', error);
    });
}

interface ProductQuickViewProps {
  product: Product | null;
  /** 详情接口加载状态。 */
  detailLoading?: boolean;
  /** 详情接口最近一次错误信息。 */
  detailError?: string | null;
  /** 重新加载详情。 */
  onRetryDetail?: () => void;
  /** 打开弹窗时默认选中的页签，订单入口会直接定位到「买家评价」。 */
  initialTab?: 'details' | 'specs' | 'reviews';
  /** 每次请求切换页签时递增的标记，用于在同一商品上重复定位到评价页签。 */
  initialTabToken?: number;
  /** 已购规格，订单入口带入后作为评价表单的默认值。 */
  reviewVariantSeed?: string;
  /** 未登录时点击提交评价回调，用于拉起登录弹窗。 */
  onRequireLogin?: () => void;
  /** 评价提交成功回调，用于刷新商品评价统计。 */
  onReviewSubmitted?: (productId: string) => void;
  /** 当前会员是否已评价过该商品，已评价时不再展示发表表单。 */
  hasReviewed?: boolean;
  /** 已评价时点击「查看我的评价」，跳转到个人中心的我的评价。 */
  onViewMyReviews?: () => void;
  isWishlisted: boolean;
  onClose: () => void;
  onAddToCart: (product: Product, variants: Record<string, string>, quantity: number) => void;
  onDirectBuy: (product: Product, variants: Record<string, string>, quantity: number) => void;
  onToggleWishlist: (productId: string) => void;
}

const ProductQuickViewContent: React.FC<Omit<ProductQuickViewProps, 'product'> & { product: Product }> = ({
  product,
  detailLoading = false,
  detailError,
  onRetryDetail,
  initialTab,
  initialTabToken,
  reviewVariantSeed,
  onRequireLogin,
  onReviewSubmitted,
  hasReviewed = false,
  onViewMyReviews,
  isWishlisted,
  onClose,
  onAddToCart,
  onDirectBuy,
  onToggleWishlist,
}) => {
  useBodyScrollLock();
  const [activeMediaIndex, setActiveMediaIndex] = useState(0);
  const [activeTab, setActiveTab] = useState<'details' | 'specs' | 'reviews'>('details');
  const [quantity, setQuantity] = useState(1);
  const [reviews, setReviews] = useState<ProductReview[]>([]);
  /** 服务端返回的评价总条数，未知时回退到商品上的统计值。 */
  const [reviewTotal, setReviewTotal] = useState<number | null>(null);
  const [reviewLoading, setReviewLoading] = useState(false);
  const [reviewLoadingMore, setReviewLoadingMore] = useState(false);
  const [reviewLoadError, setReviewLoadError] = useState<string | null>(null);
  const [reviewError, setReviewError] = useState<string | null>(null);
  const [reviewNotice, setReviewNotice] = useState<string | null>(null);
  const [reviewPageNo, setReviewPageNo] = useState(1);
  const [reviewHasMore, setReviewHasMore] = useState(false);
  const [reviewReloadKey, setReviewReloadKey] = useState(0);
  const [reviewRating, setReviewRating] = useState(5);
  const [reviewContent, setReviewContent] = useState('');
  const [reviewVariant, setReviewVariant] = useState('');
  const [reviewImages, setReviewImages] = useState<string[]>([]);
  const [reviewUploading, setReviewUploading] = useState(false);
  const [reviewSubmitting, setReviewSubmitting] = useState(false);

  // Reset state when a new product is selected
  useEffect(() => {
    setActiveMediaIndex(0);
    setQuantity(1);
    setIsPlaying(false);
    setCurrentTime(0);
    setReviewTotal(null);
    setReviewVariant(reviewVariantSeed || '');
    if (product.variants) {
      const initial: Record<string, string> = {};
      product.variants.forEach((v) => {
        initial[v.name] = v.options[0]?.label || '';
      });
      setSelectedVariants(initial);
    } else {
      setSelectedVariants({});
    }
  }, [product.id, product.variants, reviewVariantSeed]);

  // 从订单等外部入口打开时需要直接落在指定页签，同一商品重复请求靠递增标记触发。
  useEffect(() => {
    if (initialTab) {
      setActiveTab(initialTab);
    }
  }, [initialTab, initialTabToken, product.id]);

  // 评价页签打开后从内容中心分页加载真实评价，接口异常时展示明确错误并支持重试。
  useEffect(() => {
    if (activeTab !== 'reviews') return;
    let cancelled = false;
    setReviewLoading(true);
    setReviewLoadError(null);
    setReviewError(null);
    setReviewNotice(null);
    setReviews([]);
    setReviewPageNo(1);
    setReviewHasMore(false);
    fetchPortalProductReviews(product.id, 1, 10)
      .then((page) => {
        if (cancelled) return;
        setReviewPageNo(1);
        setReviewHasMore(page.current < page.pages);
        setReviewTotal(Number.isFinite(page.total) ? page.total : null);
        setReviews((page.records || []).map(mapPortalReview));
      })
      .catch((error) => {
        if (cancelled) return;
        setReviewLoadError(error instanceof Error ? error.message : '评价加载失败，请稍后重试');
      })
      .finally(() => { if (!cancelled) setReviewLoading(false); });
    return () => {
      cancelled = true;
    };
  }, [activeTab, product.id, reviewReloadKey]);

  const loadMoreReviews = async () => {
    if (reviewLoadingMore || !reviewHasMore) return;
    const nextPage = reviewPageNo + 1;
    setReviewLoadingMore(true);
    setReviewError(null);
    try {
      const page = await fetchPortalProductReviews(product.id, nextPage, 10);
      setReviews((current) => [...current, ...(page.records || []).map(mapPortalReview)]);
      setReviewPageNo(nextPage);
      setReviewHasMore(page.current < page.pages);
    } catch (error) {
      setReviewError(error instanceof Error ? error.message : '更多评价加载失败');
    } finally {
      setReviewLoadingMore(false);
    }
  };

  const submitReview = async () => {
    if (!hasPortalMemberSession()) {
      setReviewNotice(null);
      if (onRequireLogin) {
        // 未登录直接拉起登录弹窗，登录完成后仍停留在当前评价表单。
        setReviewError(null);
        onRequireLogin();
      } else {
        setReviewError('请先登录会员账号后再提交评价');
      }
      return;
    }
    if (!reviewContent.trim()) {
      setReviewError('请填写评价内容');
      setReviewNotice(null);
      return;
    }
    setReviewSubmitting(true);
    setReviewError(null);
    setReviewNotice(null);
    try {
      await submitPortalProductReview({
        productId: Number(product.id.replace(/^prod-/, '')),
        rating: reviewRating,
        reviewContent: reviewContent.trim(),
        variantSummary: reviewVariant.trim() || undefined,
        imageUrls: reviewImages,
      });
      setReviewContent('');
      setReviewVariant('');
      setReviewImages([]);
      setReviewNotice('评价已提交，审核通过后展示');
      onReviewSubmitted?.(product.id);
    } catch (error) {
      setReviewError(error instanceof Error ? error.message : '评价提交失败，请稍后重试');
    } finally {
      setReviewSubmitting(false);
    }
  };

  const handleReviewImagesChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files || []) as File[];
    event.target.value = '';
    if (!files.length) return;
    if (reviewImages.length + files.length > 9) {
      setReviewError('评价图片最多上传9张');
      return;
    }
    setReviewUploading(true);
    setReviewError(null);
    try {
      const uploaded: Array<{ url: string }> = await Promise.all(files.map((file) => uploadPortalMedia(file)));
      setReviewImages((current) => [...current, ...uploaded.map((item) => item.url)]);
    } catch (error) {
      setReviewError(error instanceof Error ? error.message : '评价图片上传失败，请稍后重试');
    } finally {
      setReviewUploading(false);
    }
  };

  const removeReviewImage = (url: string) => {
    setReviewImages((current) => current.filter((item) => item !== url));
  };

  // Video playback & viewport states
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const stageRef = useRef<HTMLDivElement | null>(null);
  const [isInViewport, setIsInViewport] = useState(true);
  const [isPlaying, setIsPlaying] = useState(false);
  const [isMuted, setIsMuted] = useState(true);
  const [isHovered, setIsHovered] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [mediaLoadError, setMediaLoadError] = useState(false);
  const [videoLoadError, setVideoLoadError] = useState(false);
  const [mediaRetryKey, setMediaRetryKey] = useState(0);

  // Build unified carousel media list (First slide is Video Demo if product has videoUrl)
  const mediaList = useMemo(() => {
    const items: Array<{
      type: 'video' | 'image';
      url: string;
      poster?: string;
      duration?: string;
      title?: string;
    }> = [];

    if (product.videoUrl) {
      items.push({
        type: 'video',
        url: product.videoUrl,
        poster: product.videoPoster || product.images[0],
        // 后端不提供时长，这里不填默认值：编出来的「0:15」会与实际视频对不上。
        duration: product.videoDuration,
        title: '商品动态演示',
      });
    }

    product.images.forEach((img, idx) => {
      items.push({
        type: 'image',
        url: img,
        title: `商品实拍图 ${idx + 1}`,
      });
    });

    return items;
  }, [product]);

  const currentMedia = mediaList[activeMediaIndex] || mediaList[0];
  const isCurrentVideo = currentMedia?.type === 'video';

  // Intersection Observer to ensure video only plays when in viewport
  useEffect(() => {
    if (!('IntersectionObserver' in window)) {
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          const visible = entry.isIntersecting && entry.intersectionRatio >= 0.2;
          setIsInViewport(visible);

          // Automatically pause playback if the component / media leaves the viewport
          if (!visible && videoRef.current) {
            videoRef.current.pause();
            setIsPlaying(false);
          }
        });
      },
      {
        threshold: [0, 0.2, 0.5, 0.8],
      }
    );

    const target = stageRef.current;
    if (target) {
      observer.observe(target);
    }

    return () => {
      if (target) {
        observer.unobserve(target);
      }
      observer.disconnect();
    };
  }, [product.id, isCurrentVideo]);

  // Pause video when switching away from video slide or when component unmounts
  useEffect(() => {
    setMediaLoadError(false);
    setVideoLoadError(false);
    if (!isCurrentVideo && videoRef.current) {
      videoRef.current.pause();
      setIsPlaying(false);
    }
  }, [activeMediaIndex, isCurrentVideo]);

  // Video hover mouse enter: auto-play muted (only when in viewport)
  const handleMouseEnterStage = () => {
    setIsHovered(true);
    if (isCurrentVideo && !videoLoadError && isInViewport && videoRef.current) {
      videoRef.current.muted = isMuted;
      playVideoSafely(videoRef.current, () => setIsPlaying(true));
    }
  };

  // Video hover mouse leave: pause playback
  const handleMouseLeaveStage = () => {
    setIsHovered(false);
    if (isCurrentVideo && videoRef.current) {
      videoRef.current.pause();
      setIsPlaying(false);
    }
  };

  // Toggle play/pause on click
  const handleTogglePlay = (e?: React.MouseEvent) => {
    e?.stopPropagation();
    if (!videoRef.current || videoLoadError || !isInViewport) return;

    if (isPlaying) {
      videoRef.current.pause();
      setIsPlaying(false);
    } else {
      videoRef.current.muted = isMuted;
      playVideoSafely(videoRef.current, () => setIsPlaying(true));
    }
  };

  // Toggle mute/unmute
  const handleToggleMute = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (!videoRef.current) return;
    const newMuted = !isMuted;
    videoRef.current.muted = newMuted;
    setIsMuted(newMuted);
  };

  // Handle Video Time Update
  const handleTimeUpdate = () => {
    if (videoRef.current) {
      setCurrentTime(videoRef.current.currentTime);
      setDuration(videoRef.current.duration || 0);
    }
  };

  // Seek bar click
  const handleSeek = (e: React.MouseEvent<HTMLDivElement>) => {
    e.stopPropagation();
    if (!videoRef.current || !duration) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const pos = (e.clientX - rect.left) / rect.width;
    videoRef.current.currentTime = pos * duration;
  };

  // Fullscreen video
  const handleFullscreen = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (!videoRef.current) return;
    if (videoRef.current.requestFullscreen) {
      videoRef.current.requestFullscreen();
    }
  };

  // Carousel navigation
  const handlePrevMedia = () => {
    if (mediaList.length === 0) return;
    setMediaLoadError(false);
    setVideoLoadError(false);
    setActiveMediaIndex((prev) => (prev > 0 ? prev - 1 : mediaList.length - 1));
  };

  const handleNextMedia = () => {
    if (mediaList.length === 0) return;
    setMediaLoadError(false);
    setVideoLoadError(false);
    setActiveMediaIndex((prev) => (prev < mediaList.length - 1 ? prev + 1 : 0));
  };

  // 支持键盘切换媒体和关闭弹窗，输入框聚焦时不拦截用户正常输入。
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      const tagName = target?.tagName?.toLowerCase();
      if (tagName === 'input' || tagName === 'textarea' || tagName === 'select' || target?.isContentEditable) return;
      if (event.key === 'ArrowLeft') {
        event.preventDefault();
        handlePrevMedia();
      } else if (event.key === 'ArrowRight') {
        event.preventDefault();
        handleNextMedia();
      } else if (event.key === 'Escape') {
        event.preventDefault();
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [mediaList.length, onClose]);

  const handleRetryMedia = () => {
    // 通过递增查询参数强制浏览器重新请求失败的媒体地址。
    setMediaRetryKey((current) => current + 1);
    setMediaLoadError(false);
    setVideoLoadError(false);
  };

  const withRetryKey = (url: string) => {
    if (!url || mediaRetryKey === 0) return url;
    return `${url}${url.includes('?') ? '&' : '?'}mediaRetry=${mediaRetryKey}`;
  };

  const formatTime = (secs: number) => {
    if (isNaN(secs)) return '00:00';
    const m = Math.floor(secs / 60);
    const s = Math.floor(secs % 60);
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  };

  // Initialize selected variants with first option of each
  const [selectedVariants, setSelectedVariants] = useState<Record<string, string>>(() => {
    const initial: Record<string, string> = {};
    if (product.variants) {
      product.variants.forEach((v) => {
        initial[v.name] = v.options[0]?.label || '';
      });
    }
    return initial;
  });

  // Calculate final unit price including variant price modifiers
  const selectedSku = product.skus?.find((sku) => {
    const attributes = Object.entries(sku.attributes);
    // 无属性 SKU 仅在商品只有一个 SKU 时匹配，避免错误命中第一个 SKU。
    return attributes.length === 0
      ? product.skus?.length === 1
      : attributes.every(([name, value]) => selectedVariants[name] === value);
  });
  let currentPrice = selectedSku?.price ?? product.price;
  if (!selectedSku && !product.skus?.length && product.variants) {
    product.variants.forEach((variant) => {
      const selectedOptionLabel = selectedVariants[variant.name];
      const foundOption = variant.options.find((opt) => opt.label === selectedOptionLabel);
      if (foundOption?.priceModifier) {
        currentPrice += foundOption.priceModifier;
      }
    });
  }
  const currentStock = Math.max(0, selectedSku?.stock ?? (product.skus?.length ? 0 : product.stock));
  const maxPurchaseQuantity = Math.min(10, currentStock);
  const currentOriginalPrice = selectedSku?.marketPrice ?? product.originalPrice;
  // 评价条数以服务端返回的总数为准，尚未加载时回退到商品列表上的统计值。
  const displayReviewCount = reviewTotal ?? product.reviewCount;
  const stockLabel = product.skus?.length && !selectedSku
    ? '该规格组合暂不可售'
    : currentStock > 0
      ? `现货充足 (${currentStock}件)`
      : '暂时缺货';

  useEffect(() => {
    // SKU 切换后把数量收敛到库存和单笔限购范围内，避免提交超卖数量。
    setQuantity((current) => Math.max(1, Math.min(current, maxPurchaseQuantity || 1)));
  }, [maxPurchaseQuantity]);

  const handleVariantSelect = (variantName: string, optionLabel: string) => {
    setSelectedVariants((prev) => ({
      ...prev,
      [variantName]: optionLabel,
    }));
  };

  const handleReviewRetry = () => {
    // 仅重新发起评价首屏查询，不保留上一次接口失败状态。
    setReviewReloadKey((current) => current + 1);
  };

  const handleAdd = () => {
    onAddToCart(product, selectedVariants, quantity);
  };

  const handleBuy = () => {
    onDirectBuy(product, selectedVariants, quantity);
  };

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-black/60 backdrop-blur-xs flex items-center justify-center p-3 sm:p-6 animate-in fade-in duration-200">
      <div 
        className="bg-white rounded-3xl border border-zinc-200 shadow-2xl w-full sm:w-4/5 max-w-none overflow-hidden relative flex flex-col max-h-[92vh]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Close Button */}
        <button
          onClick={onClose}
          className="absolute top-4 right-4 z-20 p-2 rounded-full bg-zinc-100/90 hover:bg-zinc-200 text-zinc-600 transition shadow-xs"
        >
          <X className="w-5 h-5" />
        </button>

        {/* Modal Main Scrollable Content */}
        <div className="overflow-y-auto p-6 sm:p-8 custom-scrollbar">
          {(detailLoading || detailError) && (
            <div className={`mb-5 flex items-center justify-between gap-3 rounded-xl border px-3 py-2 text-xs ${detailError ? 'border-rose-200 bg-rose-50 text-rose-700' : 'border-sky-200 bg-sky-50 text-sky-700'}`} role={detailError ? 'alert' : 'status'} aria-live="polite">
              <span>{detailError || '正在加载商品详情…'}</span>
              {detailError && onRetryDetail && (
                <button type="button" onClick={onRetryDetail} className="shrink-0 rounded-lg border border-current px-2.5 py-1 font-semibold hover:bg-white/70">
                  重新加载
                </button>
              )}
            </div>
          )}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-8 items-start mb-8">
            {/* Gallery Column */}
            <div className="space-y-4">
              {/* Main Media Stage (Video / Image) */}
              <div 
                ref={stageRef}
                className="relative aspect-square rounded-2xl overflow-hidden bg-zinc-950 border border-zinc-200 group select-none cursor-pointer"
                onMouseEnter={handleMouseEnterStage}
                onMouseLeave={handleMouseLeaveStage}
                onClick={isCurrentVideo ? () => handleTogglePlay() : undefined}
              >
                {!currentMedia ? (
                  <div className="w-full h-full flex flex-col items-center justify-center gap-2 bg-zinc-100 text-zinc-500 text-sm">
                    <span className="text-2xl" aria-hidden="true">▧</span>
                    <span>暂无可用商品媒体</span>
                  </div>
                ) : isCurrentVideo ? (
                  /* Video Player Slide */
                  <div className="w-full h-full relative flex items-center justify-center bg-zinc-950">
                    <video
                      ref={videoRef}
                      src={withRetryKey(currentMedia.url)}
                      poster={currentMedia.poster}
                      playsInline
                      muted={isMuted}
                      loop
                      onPlay={() => setIsPlaying(true)}
                      onPause={() => setIsPlaying(false)}
                      onTimeUpdate={handleTimeUpdate}
                      onEnded={() => setIsPlaying(false)}
                      onError={() => {
                        console.warn('商品视频加载失败', currentMedia.url);
                        setIsPlaying(false);
                        setVideoLoadError(true);
                      }}
                      className="w-full h-full object-cover"
                    />

                    {videoLoadError && (
                      <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-zinc-950/85 text-center text-sm text-zinc-200">
                        <Film className="w-6 h-6 text-amber-400" />
                        <span>视频暂时无法播放</span>
                        <span className="text-xs text-zinc-400">请查看下方商品图片</span>
                        <button type="button" onClick={handleRetryMedia} className="mt-1 rounded-lg border border-zinc-600 bg-zinc-800 px-3 py-1.5 text-xs font-semibold text-zinc-100 hover:bg-zinc-700">
                          重新加载视频
                        </button>
                      </div>
                    )}

                    {/* Top Badges: Video demo indicator & Product Badge */}
                    <div className="absolute top-3 left-3 right-3 flex items-center justify-between pointer-events-none z-10">
                      <div className="flex items-center gap-1.5">
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-zinc-900/85 backdrop-blur-md text-amber-300 text-xs font-bold border border-amber-400/30 shadow-md">
                          <Film className="w-3.5 h-3.5 text-amber-400" />
                          <span>视频演示</span>
                          {isPlaying && (
                            <span className="flex items-center gap-0.5 ml-1">
                              <span className="w-1.5 h-1.5 rounded-full bg-rose-500 animate-ping" />
                              <span className="w-1.5 h-1.5 rounded-full bg-rose-500" />
                            </span>
                          )}
                        </span>
                      </div>

                      {product.badge && (
                        <span className="px-3 py-1 rounded-lg bg-zinc-900/90 text-white text-xs font-bold tracking-wider shadow-md">
                          {product.badge}
                        </span>
                      )}
                    </div>

                    {/* Center Big Play Icon when paused or not hovering */}
                    {!isPlaying && !videoLoadError && (
                      <div className="absolute inset-0 flex flex-col items-center justify-center bg-black/30 backdrop-blur-[2px] transition-all group-hover:bg-black/20">
                        <button
                          type="button"
                          onClick={(e) => handleTogglePlay(e)}
                          className="w-16 h-16 rounded-full bg-white/95 text-zinc-950 flex items-center justify-center shadow-2xl transition hover:scale-110 active:scale-95 group-hover:shadow-amber-500/20"
                        >
                          <Play className="w-7 h-7 fill-zinc-950 ml-1" />
                        </button>
                        <span className="mt-3 px-3 py-1 rounded-full bg-black/60 backdrop-blur-md text-white text-xs font-medium tracking-wide">
                          鼠标悬停即可静音预览视频
                        </span>
                      </div>
                    )}

                    {/* Bottom Video Controls Overlay (Visible on Hover or Playing) */}
                    <div
                      className={`absolute bottom-0 inset-x-0 p-3 bg-linear-to-t from-black/85 via-black/40 to-transparent transition-opacity duration-200 z-10 ${
                        isHovered || isPlaying ? 'opacity-100' : 'opacity-0'
                      }`}
                      onClick={(e) => e.stopPropagation()}
                    >
                      {/* Video Scrubber / Progress Bar */}
                      <div
                        onClick={handleSeek}
                        className="w-full h-1.5 bg-white/30 rounded-full mb-2.5 cursor-pointer relative hover:h-2 transition-all group/scrub"
                      >
                        <div
                          className="h-full bg-amber-400 rounded-full relative"
                          style={{ width: `${duration ? (currentTime / duration) * 100 : 0}%` }}
                        >
                          <span className="absolute right-0 top-1/2 -translate-y-1/2 w-3 h-3 bg-white rounded-full shadow-md scale-0 group-hover/scrub:scale-100 transition-transform" />
                        </div>
                      </div>

                      <div className="flex items-center justify-between text-white text-xs">
                        <div className="flex items-center gap-2.5">
                          {/* Play / Pause Toggle */}
                          <button
                            type="button"
                            onClick={(e) => handleTogglePlay(e)}
                            className="p-1.5 rounded-lg hover:bg-white/20 transition"
                            title={isPlaying ? '暂停' : '播放'}
                          >
                            {isPlaying ? <Pause className="w-4 h-4" /> : <Play className="w-4 h-4 fill-white" />}
                          </button>

                          {/* Sound Mute / Unmute Toggle */}
                          <button
                            type="button"
                            onClick={handleToggleMute}
                            className="p-1.5 rounded-lg hover:bg-white/20 transition flex items-center gap-1"
                            title={isMuted ? '点击开启声音' : '静音'}
                          >
                            {isMuted ? (
                              <>
                                <VolumeX className="w-4 h-4 text-rose-300" />
                                <span className="text-[10px] text-zinc-300 hidden sm:inline">已静音</span>
                              </>
                            ) : (
                              <>
                                <Volume2 className="w-4 h-4 text-emerald-400" />
                                <span className="text-[10px] text-emerald-300 hidden sm:inline">声音开启</span>
                              </>
                            )}
                          </button>

                          {/* Timestamp */}
                          <span className="text-[11px] font-mono text-zinc-300">
                            {formatTime(currentTime)} / {formatTime(duration || 15)}
                          </span>
                        </div>

                        {/* Fullscreen button */}
                        <div className="flex items-center gap-2">
                          <button
                            type="button"
                            onClick={handleFullscreen}
                            className="p-1.5 rounded-lg hover:bg-white/20 transition"
                            title="全屏播放"
                          >
                            <Maximize2 className="w-4 h-4" />
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                ) : (
                  /* Standard Image Slide */
                  <div className="w-full h-full relative">
                    {mediaLoadError ? (
                      <div className="w-full h-full flex flex-col items-center justify-center gap-2 bg-zinc-100 text-zinc-500 text-sm text-center px-4">
                        <span className="text-2xl" aria-hidden="true">▧</span>
                        <span>商品图片暂时无法加载</span>
                        <span className="text-xs text-zinc-400">请切换其他媒体或稍后重试</span>
                        <button type="button" onClick={handleRetryMedia} className="mt-1 rounded-lg border border-zinc-300 bg-white px-3 py-1.5 text-xs font-semibold text-zinc-700 hover:bg-zinc-50">
                          重新加载图片
                        </button>
                      </div>
                    ) : (
                      <img
                        src={withRetryKey(currentMedia.url)}
                        alt={product.title}
                        onError={() => setMediaLoadError(true)}
                        className="w-full h-full object-cover object-center"
                      />
                    )}
                    {product.badge && (
                      <span className="absolute top-3 left-3 px-3 py-1 rounded-lg bg-zinc-900 text-white text-xs font-bold tracking-wider shadow-md">
                        {product.badge}
                      </span>
                    )}
                  </div>
                )}

                {/* Carousel Left / Right Quick Navigation Buttons */}
                {mediaList.length > 1 && (
                  <>
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        handlePrevMedia();
                      }}
                      className="absolute left-2.5 top-1/2 -translate-y-1/2 w-8 h-8 rounded-full bg-black/40 hover:bg-black/70 text-white backdrop-blur-xs flex items-center justify-center opacity-0 group-hover:opacity-100 transition shadow-md"
                      title="上一张"
                    >
                      <ChevronLeft className="w-4 h-4" />
                    </button>
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleNextMedia();
                      }}
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 w-8 h-8 rounded-full bg-black/40 hover:bg-black/70 text-white backdrop-blur-xs flex items-center justify-center opacity-0 group-hover:opacity-100 transition shadow-md"
                      title="下一张"
                    >
                      <ChevronRight className="w-4 h-4" />
                    </button>
                  </>
                )}
              </div>

              {/* Media Thumbnails Strip */}
              {mediaList.length > 1 && (
                <div className="space-y-1.5">
                  <div className="flex items-center gap-2.5 overflow-x-auto pb-1 custom-scrollbar">
                    {mediaList.map((media, idx) => {
                      const isActive = activeMediaIndex === idx;
                      const isVideo = media.type === 'video';

                      return (
                        <button
                          key={idx}
                          type="button"
                          onClick={() => setActiveMediaIndex(idx)}
                          className={`relative w-16 h-16 rounded-xl overflow-hidden bg-zinc-100 border-2 transition shrink-0 group/thumb ${
                            isActive
                              ? 'border-zinc-900 ring-2 ring-zinc-900/15 scale-102'
                              : 'border-transparent opacity-75 hover:opacity-100 hover:border-zinc-300'
                          }`}
                        >
                          <img
                            src={isVideo ? media.poster || media.url : media.url}
                            alt={media.title || '缩略图'}
                            className="w-full h-full object-cover"
                          />

                          {/* Video Thumbnail Special Badge Indicator */}
                          {isVideo && (
                            <div className="absolute inset-0 bg-black/35 flex flex-col items-center justify-center">
                              <div className="w-6 h-6 rounded-full bg-amber-500 text-zinc-950 flex items-center justify-center shadow-xs">
                                <Play className="w-3.5 h-3.5 fill-zinc-950 ml-0.5" />
                              </div>
                              <span className="text-[9px] font-bold text-white mt-0.5 tracking-tight px-1 py-0.2 rounded bg-black/60">
                                视频
                              </span>
                            </div>
                          )}
                        </button>
                      );
                    })}
                  </div>
                  <div className="text-[11px] text-zinc-400 flex items-center justify-between px-0.5">
                    <span>
                      {isCurrentVideo ? '▶ 当前为视频演示模式' : `第 ${activeMediaIndex + 1} / ${mediaList.length} 张媒体图`}
                    </span>
                    <span className="text-[10px] text-zinc-400">支持键盘或左右箭头切换</span>
                  </div>
                </div>
              )}

              {/* Trust Badges */}
              <div className="grid grid-cols-3 gap-2 pt-1 text-[11px] text-zinc-500 font-medium">
                <div className="p-2.5 rounded-xl bg-zinc-50 border border-zinc-100 text-center flex flex-col items-center gap-1">
                  <Truck className="w-4 h-4 text-emerald-600" />
                  <span>极速发货</span>
                </div>
                <div className="p-2.5 rounded-xl bg-zinc-50 border border-zinc-100 text-center flex flex-col items-center gap-1">
                  <ShieldCheck className="w-4 h-4 text-sky-600" />
                  <span>官方正品保证</span>
                </div>
                <div className="p-2.5 rounded-xl bg-zinc-50 border border-zinc-100 text-center flex flex-col items-center gap-1">
                  <RotateCcw className="w-4 h-4 text-amber-600" />
                  <span>7天无忧退换</span>
                </div>
              </div>
            </div>

            {/* Info & Variants Column */}
            <div className="space-y-6">
              <div>
                <div className="flex items-center justify-between gap-2 mb-1.5">
                  <span className="text-xs font-bold text-zinc-400 uppercase tracking-wider">
                    {product.brand}
                  </span>
                  <span className="text-xs px-2 py-0.5 rounded-md bg-zinc-100 text-zinc-600 font-medium">
                    {product.categoryLabel}
                  </span>
                </div>

                <h2 className="text-xl sm:text-2xl font-bold text-zinc-900 leading-snug mb-2">
                  {product.title}
                </h2>

                <p className="text-xs sm:text-sm text-zinc-500 leading-relaxed mb-4">
                  {product.subtitle}
                </p>

                {/* Rating & Reviews overview */}
                <div className="flex items-center gap-4 text-xs text-zinc-500 pb-4 border-b border-zinc-100">
                  {displayReviewCount > 0 ? (
                    <div className="flex items-center gap-1 text-amber-500 font-bold">
                      <Star className="w-4 h-4 fill-amber-400 text-amber-400" />
                      <span>{product.rating}</span>
                    </div>
                  ) : (
                    <span className="text-zinc-400">暂无评价</span>
                  )}
                  <span>•</span>
                  {displayReviewCount > 0
                    ? <span>{displayReviewCount} 条用户真实评价</span>
                    : <span>成为第一个评价的人</span>}
                  <span>•</span>
                  <span className="text-zinc-600 font-medium">累计热销 {product.salesCount}+ 件</span>
                </div>
              </div>

              {/* Price Row */}
              <div className="p-4 rounded-2xl bg-zinc-50 border border-zinc-200/80 flex items-baseline justify-between">
                <div>
                  <div className="text-[11px] text-zinc-400 mb-0.5">现享特惠价</div>
                  <div className="flex items-baseline gap-2">
                    <span className="text-base font-bold text-zinc-900">¥</span>
                    <span className="text-3xl font-black text-zinc-900 tracking-tight">
                      {currentPrice}
                    </span>
                    <span className="text-xs text-zinc-400 line-through">
                      ¥{currentOriginalPrice}
                    </span>
                  </div>
                </div>

                <div className="text-right">
                  <span className={`inline-flex items-center gap-1 text-xs font-semibold px-2.5 py-1 rounded-md border ${
                    currentStock > 0
                      ? 'bg-emerald-50 text-emerald-700 border-emerald-200/60'
                      : 'bg-rose-50 text-rose-700 border-rose-200/60'
                  }`}>
                    <Check className="w-3.5 h-3.5" aria-hidden="true" />
                    {stockLabel}
                  </span>
                </div>
              </div>

              {/* Variants Selector */}
              {product.variants && product.variants.map((variant) => (
                <div key={variant.name} className="space-y-2">
                  <label className="text-xs font-semibold text-zinc-900 flex items-center justify-between">
                    <span>选择{variant.name}</span>
                    <span className="text-zinc-500 font-normal">已选：{selectedVariants[variant.name]}</span>
                  </label>
                  <div className="flex flex-wrap gap-2">
                    {variant.options.map((opt) => {
                      const isSelected = selectedVariants[variant.name] === opt.label;
                      const isOptionAvailable = !product.skus?.length || product.skus.some((sku) => {
                        if (sku.stock <= 0) return false;
                        return Object.entries(sku.attributes).every(([name, value]) => (
                          name === variant.name
                            ? value === opt.label
                            : !selectedVariants[name] || selectedVariants[name] === value
                        ));
                      });
                      return (
                        <button
                          key={opt.id}
                          type="button"
                          disabled={!isOptionAvailable && !isSelected}
                          onClick={() => handleVariantSelect(variant.name, opt.label)}
                          className={`px-3.5 py-2 rounded-xl text-xs font-medium border transition-all ${
                            isSelected
                              ? 'bg-zinc-900 text-white border-zinc-900 shadow-xs'
                              : isOptionAvailable
                                ? 'bg-white text-zinc-700 border-zinc-200 hover:border-zinc-300'
                                : 'bg-zinc-100 text-zinc-400 border-zinc-100 cursor-not-allowed line-through'
                          }`}
                        >
                          {opt.label}
                          {opt.priceModifier ? ` (+¥${opt.priceModifier})` : ''}
                        </button>
                      );
                    })}
                  </div>
                </div>
              ))}

              {/* Quantity Stepper */}
              <div className="space-y-2">
                <label className="text-xs font-semibold text-zinc-900">购买数量</label>
                <div className="flex items-center gap-3">
                  <div className="flex items-center border border-zinc-200 rounded-xl bg-white p-1">
                    <button
                      onClick={() => setQuantity((q) => Math.max(1, q - 1))}
                      disabled={quantity <= 1}
                      className="p-1.5 rounded-lg hover:bg-zinc-100 text-zinc-600 disabled:opacity-30 transition"
                    >
                      <Minus className="w-3.5 h-3.5" />
                    </button>
                    <input
                      type="number"
                      min={1}
                      max={Math.max(1, maxPurchaseQuantity)}
                      value={quantity}
                      onChange={(e) => setQuantity(Math.max(1, Math.min(maxPurchaseQuantity || 1, Number(e.target.value) || 1)))}
                      className="w-12 text-center text-xs font-semibold text-zinc-900 focus:outline-none bg-transparent"
                    />
                    <button
                      onClick={() => setQuantity((q) => Math.min(maxPurchaseQuantity, q + 1))}
                      disabled={quantity >= maxPurchaseQuantity}
                      className="p-1.5 rounded-lg hover:bg-zinc-100 text-zinc-600 disabled:opacity-30 transition"
                    >
                      <Plus className="w-3.5 h-3.5" />
                    </button>
                  </div>
                  <span className="text-xs text-zinc-400">单笔订单限购 10 件</span>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="flex items-center gap-3 pt-4 border-t border-zinc-100">
                <button
                  onClick={() => onToggleWishlist(product.id)}
                  className={`p-3 rounded-2xl border transition ${
                    isWishlisted
                      ? 'bg-rose-50 border-rose-200 text-rose-500'
                      : 'border-zinc-200 text-zinc-600 hover:text-zinc-900 hover:bg-zinc-50'
                  }`}
                  title={isWishlisted ? '取消收藏' : '加入收藏'}
                >
                  <Heart className={`w-5 h-5 ${isWishlisted ? 'fill-rose-500' : ''}`} />
                </button>

                <button
                  onClick={handleAdd}
                  disabled={currentStock <= 0}
                  className="flex-1 py-3.5 px-4 rounded-2xl border-2 border-zinc-900 text-zinc-900 hover:bg-zinc-50 font-bold text-xs sm:text-sm transition flex items-center justify-center gap-2"
                >
                  <ShoppingBag className="w-4 h-4" />
                  加入购物车
                </button>

                <button
                  onClick={handleBuy}
                  disabled={currentStock <= 0}
                  className="flex-1 py-3.5 px-4 rounded-2xl bg-zinc-900 text-white hover:bg-zinc-800 font-bold text-xs sm:text-sm transition flex items-center justify-center gap-2 shadow-md"
                >
                  <Zap className="w-4 h-4 text-amber-400" />
                  立即购买
                </button>
              </div>
            </div>
          </div>

          {/* Bottom Tabs: Description / Specs / Reviews */}
          <div className="border-t border-zinc-200 pt-6">
            <div className="flex items-center gap-3 border-b border-zinc-200 mb-6">
              <button
                onClick={() => setActiveTab('details')}
                className={`pb-3 text-sm font-semibold transition relative ${
                  activeTab === 'details'
                    ? 'text-zinc-900 after:absolute after:bottom-0 after:left-0 after:right-0 after:h-0.5 after:bg-zinc-900'
                    : 'text-zinc-400 hover:text-zinc-600'
                }`}
              >
                商品详情
              </button>
              <button
                onClick={() => setActiveTab('specs')}
                className={`pb-3 text-sm font-semibold transition relative ${
                  activeTab === 'specs'
                    ? 'text-zinc-900 after:absolute after:bottom-0 after:left-0 after:right-0 after:h-0.5 after:bg-zinc-900'
                    : 'text-zinc-400 hover:text-zinc-600'
                }`}
              >
                规格参数
              </button>
              <button
                onClick={() => setActiveTab('reviews')}
                className={`pb-3 text-sm font-semibold transition relative ${
                  activeTab === 'reviews'
                    ? 'text-zinc-900 after:absolute after:bottom-0 after:left-0 after:right-0 after:h-0.5 after:bg-zinc-900'
                    : 'text-zinc-400 hover:text-zinc-600'
                }`}
              >
                买家评价 ({displayReviewCount})
              </button>
            </div>

            {/* Tab: Details */}
            {activeTab === 'details' && (
              <div className="space-y-6 text-sm text-zinc-600 leading-relaxed">
                {product.description?.trim() ? (
                  <div
                    className="prose prose-sm max-w-none text-zinc-600 [&_img]:max-w-full [&_img]:rounded-xl [&_p]:mb-3 [&_ul]:list-disc [&_ul]:pl-5 [&_ol]:list-decimal [&_ol]:pl-5"
                    dangerouslySetInnerHTML={{ __html: sanitizeProductRichText(product.description) }}
                  />
                ) : (
                  <div className="rounded-xl border border-dashed border-zinc-200 bg-zinc-50 px-4 py-5 text-center text-xs text-zinc-500">
                    该商品暂未提供详细介绍，您可以查看规格参数或咨询在线客服。
                  </div>
                )}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  {product.features.map((feat, idx) => (
                    <div key={idx} className="flex items-start gap-2.5 p-3 rounded-xl bg-zinc-50 border border-zinc-100">
                      <div className="w-5 h-5 rounded-full bg-emerald-100 text-emerald-700 flex items-center justify-center shrink-0 mt-0.5 text-xs font-bold">
                        ✓
                      </div>
                      <span className="font-medium text-zinc-800">{feat}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Tab: Specs */}
            {activeTab === 'specs' && (
              <div className="border border-zinc-200 rounded-2xl overflow-hidden">
                <table className="w-full text-xs sm:text-sm text-left">
                  <tbody>
                    {Object.entries(product.specs).map(([k, v], idx) => (
                      <tr key={k} className={idx % 2 === 0 ? 'bg-zinc-50/70' : 'bg-white'}>
                        <td className="py-3 px-4 font-semibold text-zinc-500 w-1/3 border-r border-zinc-100">
                          {k}
                        </td>
                        <td className="py-3 px-4 text-zinc-800 font-medium">{v}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {/* Tab: Reviews */}
            {activeTab === 'reviews' && (
              <div className="space-y-4">
                {reviewLoading && <div className="text-sm text-zinc-500 py-6 text-center">正在加载评价…</div>}
                {!reviewLoading && reviewLoadError && reviews.length === 0 && (
                  <div className="rounded-2xl border border-rose-100 bg-rose-50 px-4 py-5 text-center">
                    <div className="text-sm font-semibold text-rose-700">评价暂时无法加载</div>
                    <div className="mt-1 text-xs text-rose-600">{reviewLoadError}</div>
                    <button
                      type="button"
                      onClick={handleReviewRetry}
                      className="mt-3 rounded-lg border border-rose-200 bg-white px-3 py-1.5 text-xs font-semibold text-rose-700 hover:bg-rose-100"
                    >
                      重新加载
                    </button>
                  </div>
                )}
                {!reviewLoading && !reviewLoadError && reviews.length === 0 && <div className="text-sm text-zinc-500 py-6 text-center">暂时还没有公开评价</div>}
                {!reviewLoading && reviewError && <div className="text-xs text-amber-600 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2">{reviewError}</div>}
                {!reviewLoading && reviews.map((rev) => (
                  <div key={rev.id} className="p-4 rounded-2xl bg-zinc-50 border border-zinc-100 space-y-2">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2.5">
                        {rev.userAvatar ? <img src={rev.userAvatar} alt={rev.userName} className="w-8 h-8 rounded-full object-cover" /> : <div className="w-8 h-8 rounded-full bg-zinc-200 flex items-center justify-center text-xs font-bold text-zinc-500">{rev.userName.slice(0, 1)}</div>}
                        <div>
                          <div className="text-xs font-bold text-zinc-900">{rev.userName}</div>
                          <div className="text-[11px] text-zinc-400">已购规格：{rev.variantUsed || '默认规格'}</div>
                        </div>
                      </div>
                      <div className="flex items-center gap-1 text-amber-500">{Array.from({ length: rev.rating }).map((_, i) => <Star key={i} className="w-3.5 h-3.5 fill-amber-400 text-amber-400" />)}</div>
                    </div>
                    <p className="text-xs sm:text-sm text-zinc-700 leading-relaxed pt-1">{rev.comment}</p>
                    {rev.imageUrls && rev.imageUrls.length > 0 && (
                      <div className="flex flex-wrap gap-2 pt-1">
                        {rev.imageUrls.map((url) => (
                          <a key={url} href={url} target="_blank" rel="noreferrer" className="block">
                            <img src={url} alt="评价图片" className="w-16 h-16 rounded-lg object-cover border border-zinc-200" loading="lazy" />
                          </a>
                        ))}
                      </div>
                    )}
                    {rev.replyContent && <div className="text-xs text-zinc-600 bg-white border border-zinc-100 rounded-lg px-3 py-2"><span className="font-bold text-zinc-800">商家回复：</span>{rev.replyContent}{rev.replyDate && <span className="ml-2 text-[11px] text-zinc-400">{rev.replyDate}</span>}</div>}
                    <div className="flex items-center justify-between text-[11px] text-zinc-400 pt-1"><span>评价时间：{rev.date || '—'}</span><span>赞同 ({rev.helpfulCount})</span></div>
                  </div>
                ))}
                {!reviewLoading && reviewHasMore && (
                  <button
                    type="button"
                    disabled={reviewLoadingMore}
                    onClick={() => void loadMoreReviews()}
                    className="w-full py-2 text-xs font-bold text-zinc-600 border border-zinc-200 rounded-lg hover:bg-zinc-50 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {reviewLoadingMore ? '正在加载…' : '加载更多评价'}
                  </button>
                )}
                {reviewNotice && <div className="text-xs text-emerald-700 bg-emerald-50 border border-emerald-100 rounded-lg px-3 py-2">{reviewNotice}</div>}
                {hasReviewed ? (
                  <div className="border border-zinc-200 rounded-2xl p-4 text-xs text-zinc-500 space-y-2">
                    <div className="text-sm font-bold text-zinc-900">你已评价过该商品</div>
                    <p>评价提交后不可重复发表；审核通过后可在「个人中心 · 我的评价」里补充追评。</p>
                    {onViewMyReviews && (
                      <button
                        type="button"
                        onClick={onViewMyReviews}
                        className="rounded-lg border border-sky-200 bg-sky-50 px-3 py-1.5 text-xs font-semibold text-sky-700 transition hover:bg-sky-100"
                      >
                        查看我的评价
                      </button>
                    )}
                  </div>
                ) : (
                <div className="border border-zinc-200 rounded-2xl p-4 space-y-3">
                  <div className="text-sm font-bold text-zinc-900">发表评价</div>
                  <div className="flex items-center gap-2"><span className="text-xs text-zinc-500">评分</span>{[1, 2, 3, 4, 5].map((score) => <button type="button" key={score} onClick={() => setReviewRating(score)} className="p-0.5"><Star className={`w-4 h-4 ${score <= reviewRating ? 'fill-amber-400 text-amber-400' : 'text-zinc-300'}`} /></button>)}</div>
                  <input value={reviewVariant} onChange={(event) => setReviewVariant(event.target.value)} placeholder="购买规格（可选）" maxLength={500} className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-xs outline-none focus:border-zinc-400" />
                  <textarea value={reviewContent} onChange={(event) => setReviewContent(event.target.value)} placeholder="分享你的使用体验…" maxLength={2000} rows={3} className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-xs outline-none focus:border-zinc-400 resize-none" />
                  <div className="space-y-2">
                    <label className="inline-flex cursor-pointer items-center rounded-lg border border-zinc-200 bg-white px-3 py-2 text-xs font-semibold text-zinc-600 hover:bg-zinc-50">
                      <input type="file" accept="image/jpeg,image/png,image/webp,image/gif" multiple className="hidden" disabled={reviewUploading || reviewSubmitting} onChange={(event) => void handleReviewImagesChange(event)} />
                      {reviewUploading ? '图片上传中…' : `上传图片（${reviewImages.length}/9）`}
                    </label>
                    {reviewImages.length > 0 && (
                      <div className="flex flex-wrap gap-2">
                        {reviewImages.map((url) => (
                          <button type="button" key={url} onClick={() => removeReviewImage(url)} className="relative group" title="移除图片">
                            <img src={url} alt="待提交评价图片" className="w-14 h-14 rounded-lg object-cover border border-zinc-200" />
                            <span className="absolute inset-0 hidden items-center justify-center rounded-lg bg-black/55 text-[10px] text-white group-hover:flex">移除</span>
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                  <button type="button" disabled={reviewSubmitting || reviewUploading} onClick={() => void submitReview()} className="rounded-lg bg-zinc-900 px-4 py-2 text-xs font-bold text-white disabled:opacity-50">{reviewSubmitting ? '提交中…' : '提交评价'}</button>
                </div>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

/**
 * 只有商品 ID、详情还没到手时的过渡态。
 *
 * 列表里没有该商品（轮播直链进入）时会先挂只带 ID 的占位对象，
 * 这时不能直接把空标题、0 元价格的骨架铺满弹窗，只渲染加载或失败提示。
 */
const ProductQuickViewPlaceholder: React.FC<{
  error?: string | null;
  onRetry?: () => void;
  onClose: () => void;
}> = ({ error, onRetry, onClose }) => {
  useBodyScrollLock();
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-3 backdrop-blur-xs sm:p-6 animate-in fade-in duration-200"
      onClick={onClose}
    >
      <div
        className="flex items-center gap-3 rounded-2xl bg-white px-6 py-4 text-sm font-medium text-zinc-700 shadow-2xl"
        onClick={(event) => event.stopPropagation()}
        role={error ? 'alert' : 'status'}
        aria-live="polite"
      >
        {error ? (
          <>
            <span className="text-rose-600">{error}</span>
            {onRetry && (
              <button
                type="button"
                onClick={onRetry}
                className="rounded-lg border border-zinc-300 px-3 py-1.5 text-xs font-semibold text-zinc-700 hover:bg-zinc-100"
              >
                重新加载
              </button>
            )}
          </>
        ) : (
          <>
            <span
              className="h-4 w-4 animate-spin rounded-full border-2 border-zinc-300 border-t-zinc-900"
              aria-hidden="true"
            />
            正在加载商品详情…
          </>
        )}
        <button
          type="button"
          onClick={onClose}
          aria-label="关闭商品详情"
          className="rounded-full p-1.5 text-zinc-500 hover:bg-zinc-100"
        >
          <X className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
};

export const ProductQuickView: React.FC<ProductQuickViewProps> = (props) => {
  // 标题为空说明手里只有一个占位对象，详情还没回来（或已失败）。
  if (props.product && !props.product.title) {
    return (
      <ProductQuickViewPlaceholder
        error={props.detailError}
        onRetry={props.onRetryDetail}
        onClose={props.onClose}
      />
    );
  }
  // 空产品时不挂载详情内容，避免弹窗关闭/打开过程中 Hooks 数量发生变化。
  return props.product ? <ProductQuickViewContent {...props} product={props.product} /> : null;
};
