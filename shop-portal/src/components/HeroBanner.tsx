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

/**
 * 按跳转类型推导按钮文案。
 * 返回 null 表示这个 Banner 没有可执行的跳转（无跳转或缺少目标），此时不渲染按钮。
 */
function bannerActionText(linkType?: string, linkTarget?: string): string | null {
  const type = (linkType || 'NONE').toUpperCase();
  if (type === 'NONE' || !(linkTarget || '').trim()) return null;
  if (type === 'PRODUCT') return '立即查看';
  if (type === 'CATEGORY') return '逛逛该分类';
  if (type === 'COUPON') return '去领券';
  if (type === 'URL') return '了解详情';
  return null;
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
        ctaText: bannerActionText(banner.linkType, banner.linkTarget),
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
      {/* 轮播：白底展台，文案在左、商品图在右。
          运营图是浅底商品实拍（灰白底手表、白底 iMac），原先压到 30% 不透明度垫在 zinc-900 上
          只剩一团灰，图与字互相拖累。现在图整幅展示不压暗，文案改到左侧白底区用深色字。
          高度 300→360→400→460 按断点放，xl 起 460px 是主视觉；圆角从 rounded-3xl 降到
          rounded-xl，是为了贴近淘宝京东那种小圆角展台（相邻的权益卡仍是 rounded-2xl，
          这次没动——只改 hero 会和紧挨着的卡片看出两套风格，等整体视觉定方向再统一）。 */}
      <div className="relative flex h-[300px] overflow-hidden rounded-xl border border-zinc-200 bg-white shadow-xs sm:h-[360px] lg:h-[400px] xl:h-[460px]">
        {/* 文案区与图片分列，不能靠遮罩兜：横向渐隐盖不住正文——副标题是块级元素，
            实测会铺到 hero 宽度的 94%，那里渐隐只剩四成白，深色字压在深色素材上读不出来。
            分栏从 sm 起生效（640 时文案区 46% 约 272px，放得下 24px 标题）；
            sm 以下文案占满整行，只能用纵向遮罩把垂直居中那一段压成近白。
            宽度取 46% 而非均分：最长那条标题「新人专享 满 199 减 50」在 36px 下约需 352px，
            再窄一点就会把「50」挤成孤字换行（实测 xl 下可用 356px，单行刚好）。 */}
        <div className="relative z-10 flex w-full flex-col justify-center px-6 py-8 sm:w-[46%] sm:px-8 xl:px-10">
          <div className="mb-4 inline-flex w-fit items-center gap-1.5 rounded-full border border-amber-200 bg-amber-50 px-3 py-1 text-xs font-semibold text-amber-700">
            <Sparkles className="w-3.5 h-3.5" />
            {slide.tag}
          </div>

          <h2 className="text-2xl sm:text-3xl xl:text-4xl font-bold tracking-tight text-zinc-900 leading-tight mb-3">
            {slide.title}
          </h2>

          <p className="text-sm md:text-base text-zinc-500 leading-relaxed mb-6 font-normal">
            {slide.subtitle}
          </p>

          <div className="flex items-center gap-4 flex-wrap">
            {slide.ctaText && (
              <button
                onClick={() => {
                  if (slide.id.startsWith('remote-') && onNavigateBanner) {
                    onNavigateBanner(slide.linkType || 'NONE', slide.productId);
                    return;
                  }
                  onSelectProduct(slide.productId);
                }}
                className="inline-flex items-center gap-2 px-6 py-3 rounded-xl bg-orange-500 text-white font-semibold text-sm hover:bg-orange-600 transition shadow-xs group"
              >
                <span>{slide.ctaText}</span>
                <ArrowRight className="w-4 h-4 group-hover:translate-x-0.5 transition-transform" />
              </button>
            )}

            {(slide.price || slide.originalPrice) && (
              <div className="flex items-baseline gap-2">
                <span className="text-2xl font-black text-orange-600">{slide.price}</span>
                <span className="text-xs text-zinc-400 line-through">{slide.originalPrice}</span>
              </div>
            )}
          </div>

          {/* 服务承诺原先贴在深色卡右侧的玻璃面板里，改白底后并入文案区，
              免得和右侧商品图抢位置；四条权益卡就在下方，这里只留一句短提示。 */}
          <div className="mt-6 hidden flex-wrap items-center gap-x-5 gap-y-1.5 xl:flex">
            <span className="inline-flex items-center gap-1.5 text-[11px] font-medium text-zinc-500">
              <Zap className="h-3.5 w-3.5 text-amber-500" />
              当日 18:00 前拍下当日发
            </span>
            <span className="inline-flex items-center gap-1.5 text-[11px] font-medium text-zinc-500">
              <Truck className="h-3.5 w-3.5 text-emerald-500" />
              全场包邮直达
            </span>
            <span className="inline-flex items-center gap-1.5 text-[11px] font-medium text-zinc-500">
              <ShieldCheck className="h-3.5 w-3.5 text-sky-500" />
              官方正品 · 两年全国联保
            </span>
          </div>
        </div>

        {/* 图片区：sm 起独立成列（占 54%）；sm 以下退化为整块底图，压一层纵向白罩兜住正文。 */}
        <div className="absolute inset-0 sm:static sm:inset-auto sm:min-w-0 sm:flex-1">
          <img
            src={slide.image}
            alt={slide.title}
            className="w-full h-full object-cover object-center"
          />
          {/* 素材深浅混用（白底商品图与深色 SALE 海报并存），手机端文案垂直居中，
              所以改用「下白上透明」的纵向罩，让居中那一段稳在近白底上。 */}
          <div
            className="absolute inset-0 bg-gradient-to-t from-white via-white/92 to-white/35 sm:hidden"
            aria-hidden="true"
          />
        </div>

        {/* 圆点与箭头都压在素材上，而素材是深浅混用的（白底手表图与深色 SALE 海报并存），
            所以不能用固定色：圆点垫一层半透明深色胶囊底，浅底深底都读得出来；
            箭头改实心白底 + zinc-300 描边，落在左侧白色文案区时才有边界。 */}
        <div className="absolute bottom-4 right-5 z-20 flex items-center gap-2 rounded-full bg-zinc-900/25 px-2.5 py-1.5 backdrop-blur-sm">
          {slides.map((_, idx) => (
            <button
              key={idx}
              onClick={() => setCurrentSlide(idx)}
              className={`h-2 rounded-full transition-all ${
                currentSlide === idx ? 'w-7 bg-white' : 'w-2 bg-white/60 hover:bg-white/90'
              }`}
              aria-label={`跳转至幻灯片 ${idx + 1}`}
            />
          ))}
        </div>

        {/* 左箭头贴着图片区左边缘（文案区 46% 之后），否则它会压在标题和正文上。
            md 以下不显示：那时图片列太窄，两个圆钮会把图夹掉大半。 */}
        <button
          onClick={() => setCurrentSlide((prev) => (prev === 0 ? slides.length - 1 : prev - 1))}
          aria-label="上一张"
          className="absolute left-[calc(46%+0.75rem)] top-1/2 -translate-y-1/2 z-20 hidden rounded-full border border-zinc-300 bg-white p-2 text-zinc-700 shadow-xs transition hover:bg-zinc-50 md:block"
        >
          <ChevronLeft className="w-5 h-5" />
        </button>
        <button
          onClick={() => setCurrentSlide((prev) => (prev + 1) % slides.length)}
          aria-label="下一张"
          className="absolute right-4 top-1/2 -translate-y-1/2 z-20 hidden rounded-full border border-zinc-300 bg-white p-2 text-zinc-700 shadow-xs transition hover:bg-zinc-50 md:block"
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
            <div className="text-xs font-bold text-zinc-900">空运速达</div>
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
