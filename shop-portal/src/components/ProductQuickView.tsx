import React, { useState, useRef, useEffect, useMemo } from 'react';
import { Product, ProductReview } from '../types/ecommerce';
import { MOCK_REVIEWS } from '../data/products';
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

interface ProductQuickViewProps {
  product: Product | null;
  isWishlisted: boolean;
  onClose: () => void;
  onAddToCart: (product: Product, variants: Record<string, string>, quantity: number) => void;
  onDirectBuy: (product: Product, variants: Record<string, string>, quantity: number) => void;
  onToggleWishlist: (productId: string) => void;
}

export const ProductQuickView: React.FC<ProductQuickViewProps> = ({
  product,
  isWishlisted,
  onClose,
  onAddToCart,
  onDirectBuy,
  onToggleWishlist,
}) => {
  if (!product) return null;

  const [activeMediaIndex, setActiveMediaIndex] = useState(0);
  const [activeTab, setActiveTab] = useState<'details' | 'specs' | 'reviews'>('details');
  const [quantity, setQuantity] = useState(1);

  // Reset state when a new product is selected
  useEffect(() => {
    setActiveMediaIndex(0);
    setQuantity(1);
    setIsPlaying(false);
    setCurrentTime(0);
    if (product.variants) {
      const initial: Record<string, string> = {};
      product.variants.forEach((v) => {
        initial[v.name] = v.options[0]?.label || '';
      });
      setSelectedVariants(initial);
    } else {
      setSelectedVariants({});
    }
  }, [product.id]);

  // Video playback & viewport states
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const stageRef = useRef<HTMLDivElement | null>(null);
  const [isInViewport, setIsInViewport] = useState(true);
  const [isPlaying, setIsPlaying] = useState(false);
  const [isMuted, setIsMuted] = useState(true);
  const [isHovered, setIsHovered] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);

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
        duration: product.videoDuration || '0:15',
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
    if (!isCurrentVideo && videoRef.current) {
      videoRef.current.pause();
      setIsPlaying(false);
    }
  }, [activeMediaIndex, isCurrentVideo]);

  // Video hover mouse enter: auto-play muted (only when in viewport)
  const handleMouseEnterStage = () => {
    setIsHovered(true);
    if (isCurrentVideo && isInViewport && videoRef.current) {
      videoRef.current.muted = isMuted;
      videoRef.current
        .play()
        .then(() => {
          setIsPlaying(true);
        })
        .catch((err) => {
          console.log('Video autoplay prevented:', err);
        });
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
    if (!videoRef.current || !isInViewport) return;

    if (isPlaying) {
      videoRef.current.pause();
      setIsPlaying(false);
    } else {
      videoRef.current.muted = isMuted;
      videoRef.current.play().then(() => setIsPlaying(true)).catch(console.error);
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
    setActiveMediaIndex((prev) => (prev > 0 ? prev - 1 : mediaList.length - 1));
  };

  const handleNextMedia = () => {
    setActiveMediaIndex((prev) => (prev < mediaList.length - 1 ? prev + 1 : 0));
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
  let currentPrice = product.price;
  if (product.variants) {
    product.variants.forEach((variant) => {
      const selectedOptionLabel = selectedVariants[variant.name];
      const foundOption = variant.options.find((opt) => opt.label === selectedOptionLabel);
      if (foundOption?.priceModifier) {
        currentPrice += foundOption.priceModifier;
      }
    });
  }

  const handleVariantSelect = (variantName: string, optionLabel: string) => {
    setSelectedVariants((prev) => ({
      ...prev,
      [variantName]: optionLabel,
    }));
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
        className="bg-white rounded-3xl border border-zinc-200 shadow-2xl max-w-4xl w-full overflow-hidden relative flex flex-col max-h-[92vh]"
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
                {isCurrentVideo ? (
                  /* Video Player Slide */
                  <div className="w-full h-full relative flex items-center justify-center bg-zinc-950">
                    <video
                      ref={videoRef}
                      src={currentMedia.url}
                      poster={currentMedia.poster}
                      playsInline
                      muted={isMuted}
                      loop
                      onPlay={() => setIsPlaying(true)}
                      onPause={() => setIsPlaying(false)}
                      onTimeUpdate={handleTimeUpdate}
                      onEnded={() => setIsPlaying(false)}
                      onError={(e) => {
                        console.warn('Video failed to load or play', e);
                        setIsPlaying(false);
                      }}
                      className="w-full h-full object-cover"
                    />

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
                    {!isPlaying && (
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
                    <img
                      src={currentMedia.url}
                      alt={product.title}
                      className="w-full h-full object-cover object-center"
                    />
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
                      {isCurrentVideo ? '▶ 当前为 0:15 视频演示模式' : `第 ${activeMediaIndex + 1} / ${mediaList.length} 张媒体图`}
                    </span>
                    <span className="text-[10px] text-zinc-400">支持键盘或左右箭头切换</span>
                  </div>
                </div>
              )}

              {/* Trust Badges */}
              <div className="grid grid-cols-3 gap-2 pt-1 text-[11px] text-zinc-500 font-medium">
                <div className="p-2.5 rounded-xl bg-zinc-50 border border-zinc-100 text-center flex flex-col items-center gap-1">
                  <Truck className="w-4 h-4 text-emerald-600" />
                  <span>顺丰极速直达</span>
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
                  <div className="flex items-center gap-1 text-amber-500 font-bold">
                    <Star className="w-4 h-4 fill-amber-400 text-amber-400" />
                    <span>{product.rating}</span>
                  </div>
                  <span>•</span>
                  <span>{product.reviewCount} 条用户真实评价</span>
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
                      ¥{product.originalPrice}
                    </span>
                  </div>
                </div>

                <div className="text-right">
                  <span className="inline-flex items-center gap-1 text-xs font-semibold px-2.5 py-1 rounded-md bg-emerald-50 text-emerald-700 border border-emerald-200/60">
                    <Check className="w-3.5 h-3.5" />
                    现货充足 ({product.stock}件)
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
                      return (
                        <button
                          key={opt.id}
                          onClick={() => handleVariantSelect(variant.name, opt.label)}
                          className={`px-3.5 py-2 rounded-xl text-xs font-medium border transition-all ${
                            isSelected
                              ? 'bg-zinc-900 text-white border-zinc-900 shadow-xs'
                              : 'bg-white text-zinc-700 border-zinc-200 hover:border-zinc-300'
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
                      max={product.stock}
                      value={quantity}
                      onChange={(e) => setQuantity(Math.max(1, Math.min(product.stock, Number(e.target.value) || 1)))}
                      className="w-12 text-center text-xs font-semibold text-zinc-900 focus:outline-none bg-transparent"
                    />
                    <button
                      onClick={() => setQuantity((q) => Math.min(product.stock, q + 1))}
                      disabled={quantity >= product.stock}
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
                  className="flex-1 py-3.5 px-4 rounded-2xl border-2 border-zinc-900 text-zinc-900 hover:bg-zinc-50 font-bold text-xs sm:text-sm transition flex items-center justify-center gap-2"
                >
                  <ShoppingBag className="w-4 h-4" />
                  加入购物车
                </button>

                <button
                  onClick={handleBuy}
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
                买家评价 ({product.reviewCount})
              </button>
            </div>

            {/* Tab: Details */}
            {activeTab === 'details' && (
              <div className="space-y-6 text-sm text-zinc-600 leading-relaxed">
                <p>{product.description}</p>
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
                {MOCK_REVIEWS.map((rev) => (
                  <div key={rev.id} className="p-4 rounded-2xl bg-zinc-50 border border-zinc-100 space-y-2">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2.5">
                        <img src={rev.userAvatar} alt={rev.userName} className="w-8 h-8 rounded-full object-cover" />
                        <div>
                          <div className="text-xs font-bold text-zinc-900">{rev.userName}</div>
                          <div className="text-[11px] text-zinc-400">已购规格：{rev.variantUsed}</div>
                        </div>
                      </div>

                      <div className="flex items-center gap-1 text-amber-500">
                        {Array.from({ length: rev.rating }).map((_, i) => (
                          <Star key={i} className="w-3.5 h-3.5 fill-amber-400 text-amber-400" />
                        ))}
                      </div>
                    </div>

                    <p className="text-xs sm:text-sm text-zinc-700 leading-relaxed pt-1">
                      {rev.comment}
                    </p>

                    <div className="flex items-center justify-between text-[11px] text-zinc-400 pt-1">
                      <span>评价时间：{rev.date}</span>
                      <span>赞同 ({rev.helpfulCount})</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
