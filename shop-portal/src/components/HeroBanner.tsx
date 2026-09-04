import React, { useState, useEffect } from 'react';
import { Sparkles, ArrowRight, ShieldCheck, Zap, Truck, Headphones, ChevronLeft, ChevronRight } from 'lucide-react';

interface HeroBannerProps {
  onExploreCategory: (category: string) => void;
  onSelectProduct: (productId: string) => void;
  banners?: Array<{
    id: number;
    bannerTitle: string;
    bannerTag?: string;
    subtitle?: string;
    imageUrl: string;
    linkType?: string;
    linkTarget?: string;
  }>;
  /** 处理远程 Banner 的多种跳转目标。 */
  onNavigateBanner?: (linkType: string, linkTarget?: string) => void;
}

export const HeroBanner: React.FC<HeroBannerProps> = ({
  onExploreCategory,
  onSelectProduct,
  onNavigateBanner,
  banners = [],
}) => {
  const [currentSlide, setCurrentSlide] = useState(0);

  const fallbackSlides = [
    {
      id: 'slide-1',
      tag: '2026 年度旗舰特献',
      title: 'Studio Master 旗舰无线降噪耳机',
      subtitle: '45dB 智能深度降噪 / 60h 惊人续航 / 无损 Hi-Res 音质，限时直降 ¥400',
      productId: 'prod-1',
      linkType: 'PRODUCT',
      category: 'audio',
      ctaText: '立即查看特惠',
      bgGradient: 'from-zinc-900 via-zinc-800 to-zinc-950',
      image: 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=1200&q=80',
      price: '¥899',
      originalPrice: '¥1299',
    },
    {
      id: 'slide-2',
      tag: '桌面生产力美学',
      title: 'Minimalist 75% Gasket 机械键盘 Pro',
      subtitle: '五层消音垫片结构 / 彩色动态数显屏 / 8000mAh 续航，敲击如雨滴般治愈',
      productId: 'prod-2',
      linkType: 'PRODUCT',
      category: 'digital',
      ctaText: '抢购首发限定',
      bgGradient: 'from-slate-900 via-zinc-900 to-slate-950',
      image: 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=1200&q=80',
      price: '¥469',
      originalPrice: '¥599',
    },
    {
      id: 'slide-3',
      tag: '旅行出行新风尚',
      title: 'Voyage 航空级铝镁合金超轻登机箱',
      subtitle: '高强度加厚合金框 / TSA双海关密码锁 / 静音万向减震轮，说走就走的精致旅程',
      productId: 'prod-4',
      linkType: 'PRODUCT',
      category: 'outdoor',
      ctaText: '探索出行装备',
      bgGradient: 'from-stone-900 via-zinc-900 to-stone-950',
      image: 'https://images.unsplash.com/photo-1565026057447-bc90a3dceb87?auto=format&fit=crop&w=1200&q=80',
      price: '¥689',
      originalPrice: '¥899',
    },
  ];
  const slides = banners.length > 0
    ? banners.map((banner) => ({
        id: `remote-${banner.id}`,
        tag: banner.bannerTag || '精选好物推荐',
        title: banner.bannerTitle,
        subtitle: banner.subtitle || '',
        productId: banner.linkTarget || '',
        linkType: banner.linkType || 'PRODUCT',
        category: 'all',
        ctaText: '立即查看',
        bgGradient: 'from-zinc-900 via-zinc-800 to-zinc-950',
        image: banner.imageUrl,
        price: '',
        originalPrice: '',
      }))
    : fallbackSlides;

  useEffect(() => {
    const timer = setInterval(() => {
      setCurrentSlide((prev) => (prev + 1) % slides.length);
    }, 6000);
    return () => clearInterval(timer);
  }, [slides.length]);

  const slide = slides[currentSlide];

  return (
    <div className="mb-10 space-y-4">
      {/* Carousel Container */}
      <div className="relative rounded-3xl overflow-hidden shadow-xl bg-zinc-900 text-white min-h-[360px] md:min-h-[400px] flex items-center">
        {/* Background Image with Overlay */}
        <div className="absolute inset-0 z-0">
          <img
            src={slide.image}
            alt={slide.title}
            className="w-full h-full object-cover object-center opacity-30 transform scale-105 transition-all duration-1000 ease-out"
          />
          <div className="absolute inset-0 bg-gradient-to-r from-zinc-950 via-zinc-900/80 to-transparent"></div>
        </div>

        {/* Content Box */}
        <div className="relative z-10 max-w-7xl mx-auto px-6 sm:px-10 py-10 md:py-14 w-full flex flex-col md:flex-row items-start md:items-center justify-between gap-8">
          <div className="max-w-xl">
            <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-white/10 backdrop-blur-md text-amber-300 text-xs font-semibold mb-4 border border-white/10">
              <Sparkles className="w-3.5 h-3.5" />
              {slide.tag}
            </div>

            <h2 className="text-2xl sm:text-3xl md:text-4xl font-bold tracking-tight text-white leading-tight mb-3">
              {slide.title}
            </h2>

            <p className="text-sm md:text-base text-zinc-300 leading-relaxed mb-6 font-normal">
              {slide.subtitle}
            </p>

            <div className="flex items-center gap-4 flex-wrap">
              <button
                onClick={() => {
                  if (slide.id.startsWith('remote-') && onNavigateBanner) {
                    onNavigateBanner(slide.linkType || 'NONE', slide.productId);
                    return;
                  }
                  onSelectProduct(slide.productId);
                }}
                className="inline-flex items-center gap-2 px-6 py-3 rounded-xl bg-white text-zinc-950 font-semibold text-sm hover:bg-zinc-100 transition shadow-lg group"
              >
                <span>{slide.ctaText}</span>
                <ArrowRight className="w-4 h-4 group-hover:translate-x-0.5 transition-transform" />
              </button>

              <div className="flex items-baseline gap-2">
                <span className="text-2xl font-black text-amber-400">{slide.price}</span>
                <span className="text-xs text-zinc-400 line-through">{slide.originalPrice}</span>
              </div>
            </div>
          </div>

          {/* Quick Badges / Side preview */}
          <div className="hidden lg:flex flex-col gap-3 bg-white/5 backdrop-blur-md p-4 rounded-2xl border border-white/10 text-xs w-64">
            <div className="flex items-center gap-2 text-zinc-300 font-medium">
              <Zap className="w-4 h-4 text-amber-400" />
              <span>极速发货：当日 18:00 前拍下当日发</span>
            </div>
            <div className="flex items-center gap-2 text-zinc-300 font-medium">
              <Truck className="w-4 h-4 text-emerald-400" />
              <span>全场顺丰速运直达</span>
            </div>
            <div className="flex items-center gap-2 text-zinc-300 font-medium">
              <ShieldCheck className="w-4 h-4 text-sky-400" />
              <span>官方正品 · 两年全国联保</span>
            </div>
          </div>
        </div>

        {/* Carousel controls */}
        <div className="absolute bottom-4 right-6 z-20 flex items-center gap-2">
          {slides.map((_, idx) => (
            <button
              key={idx}
              onClick={() => setCurrentSlide(idx)}
              className={`h-2 rounded-full transition-all ${
                currentSlide === idx ? 'w-8 bg-white' : 'w-2 bg-white/40 hover:bg-white/70'
              }`}
              aria-label={`跳转至幻灯片 ${idx + 1}`}
            />
          ))}
        </div>

        {/* Arrows */}
        <button
          onClick={() => setCurrentSlide((prev) => (prev === 0 ? slides.length - 1 : prev - 1))}
          className="absolute left-4 top-1/2 -translate-y-1/2 p-2 rounded-full bg-black/30 hover:bg-black/60 text-white backdrop-blur-sm transition hidden md:block"
        >
          <ChevronLeft className="w-5 h-5" />
        </button>
        <button
          onClick={() => setCurrentSlide((prev) => (prev + 1) % slides.length)}
          className="absolute right-4 top-1/2 -translate-y-1/2 p-2 rounded-full bg-black/30 hover:bg-black/60 text-white backdrop-blur-sm transition hidden md:block"
        >
          <ChevronRight className="w-5 h-5" />
        </button>
      </div>

      {/* Value Proposition Highlights Bar */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <div className="p-3.5 rounded-2xl bg-white border border-zinc-200/80 flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center shrink-0">
            <Truck className="w-4 h-4" />
          </div>
          <div>
            <div className="text-xs font-bold text-zinc-900">顺丰空运速达</div>
            <div className="text-[11px] text-zinc-500">满 ¥99 即享免费包邮</div>
          </div>
        </div>

        <div className="p-3.5 rounded-2xl bg-white border border-zinc-200/80 flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0">
            <ShieldCheck className="w-4 h-4" />
          </div>
          <div>
            <div className="text-xs font-bold text-zinc-900">正品品质保障</div>
            <div className="text-[11px] text-zinc-500">严选大牌 · 原厂授权</div>
          </div>
        </div>

        <div className="p-3.5 rounded-2xl bg-white border border-zinc-200/80 flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-sky-50 text-sky-600 flex items-center justify-center shrink-0">
            <Zap className="w-4 h-4" />
          </div>
          <div>
            <div className="text-xs font-bold text-zinc-900">7天无理由退换</div>
            <div className="text-[11px] text-zinc-500">赠送退货运费险服务</div>
          </div>
        </div>

        <div className="p-3.5 rounded-2xl bg-white border border-zinc-200/80 flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-purple-50 text-purple-600 flex items-center justify-center shrink-0">
            <Headphones className="w-4 h-4" />
          </div>
          <div>
            <div className="text-xs font-bold text-zinc-900">专属管家客服</div>
            <div className="text-[11px] text-zinc-500">7x24小时一对一答疑</div>
          </div>
        </div>
      </div>
    </div>
  );
};
