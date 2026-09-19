import assert from 'node:assert/strict';
import { renderToStaticMarkup } from 'react-dom/server';
import { ProductCard } from '../src/components/ProductCard';
import { ProductQuickView, sanitizeProductRichText } from '../src/components/ProductQuickView';
import { HeroBanner } from '../src/components/HeroBanner';
import { OrderTracking } from '../src/components/OrderTracking';
import { CategoryRail } from '../src/components/CategoryRail';
import { CouponCenter } from '../src/components/CouponCenter';
import { CouponCenterBanner } from '../src/components/CouponCenterBanner';
import { couponTagLabel } from '../src/utils/couponTag';
import { isEmail, AuthModal } from '../src/components/AuthModal';
import type { Product, Coupon } from '../src/types/ecommerce';
import type { PortalCategoryNode } from '../src/api/portalApi';

const baseProduct: Product = {
  id: 'prod-1001',
  title: '测试降噪耳机',
  subtitle: '用于组件冒烟测试',
  category: 'audio',
  categoryLabel: '影音娱乐',
  brand: 'HENFON',
  price: 399,
  originalPrice: 499,
  rating: 4.8,
  reviewCount: 18,
  salesCount: 120,
  stock: 8,
  badge: '直降',
  images: ['https://example.com/headphones.webp'],
  features: [],
  specs: {},
  description: '测试商品描述',
  isFreeShipping: true,
  deliveryEstimate: '预计 2-3 天送达',
};

function renderCard(product: Product, viewMode: 'grid' | 'list' = 'grid') {
  return renderToStaticMarkup(
    <ProductCard
      product={product}
      viewMode={viewMode}
      isWishlisted={false}
      isCompareMode
      isCompared
      onQuickView={() => undefined}
      onAddToCart={() => undefined}
      onToggleWishlist={() => undefined}
      onToggleCompare={() => undefined}
    />,
  );
}

const gridMarkup = renderCard(baseProduct);
assert.match(gridMarkup, /alt="测试降噪耳机"/);
assert.match(gridMarkup, /aria-label="将 测试降噪耳机 加入购物车"/);
assert.match(gridMarkup, /aria-pressed="true"/);
assert.match(gridMarkup, /role="button"/);

const outOfStockMarkup = renderCard({ ...baseProduct, stock: 0 }, 'list');
assert.match(outOfStockMarkup, /disabled=""/);
assert.match(outOfStockMarkup, /测试降噪耳机 暂时缺货/);

const remoteBannerMarkup = renderToStaticMarkup(
  <HeroBanner
    banners={[{
      id: 99,
      bannerTitle: '夏日清凉专题',
      bannerTag: '限时活动',
      subtitle: '精选商品低至五折',
      imageUrl: 'https://example.com/summer.webp',
      linkType: 'CATEGORY',
      linkTarget: 'outdoor',
    }]}
    onExploreCategory={() => undefined}
    onSelectProduct={() => undefined}
    onNavigateBanner={() => undefined}
  />,
);
assert.match(remoteBannerMarkup, /夏日清凉专题/);
assert.match(remoteBannerMarkup, /限时活动/);
// linkType 为 CATEGORY，按钮文案应落在「逛逛该分类」而非商品跳转用的「立即查看」。
assert.match(remoteBannerMarkup, /逛逛该分类/);
assert.doesNotMatch(remoteBannerMarkup, /立即查看/);
// 轮播改成白底展台：图片整幅展示不压暗（原先 opacity-30 垫在深色底上），文案在左侧白底区用深色字。
assert.match(remoteBannerMarkup, /object-cover/);
assert.doesNotMatch(remoteBannerMarkup, /opacity-30/);
assert.match(remoteBannerMarkup, /alt="夏日清凉专题"/);
// 左右切换箭头原先是无名称的图标按钮，补上可访问名称。
assert.match(remoteBannerMarkup, /aria-label="上一张"/);
assert.match(remoteBannerMarkup, /aria-label="下一张"/);

// 注册邮箱是登录凭证与密码找回通道，格式校验需拒绝缺域名、缺 @ 等输入。
assert.equal(isEmail('buyer@example.com'), true);
assert.equal(isEmail(' buyer@example.com '), true);
assert.equal(isEmail('buyer@example'), false);
assert.equal(isEmail('13800138000'), false);

// 登录弹层只保留密码登录与注册两个入口，不再提供短信免密登录。
const loginMarkup = renderToStaticMarkup(
  <AuthModal isOpen onClose={() => undefined} onLoginSuccess={() => undefined} />,
);
assert.match(loginMarkup, /密码登录/);
assert.match(loginMarkup, /新客注册/);
assert.doesNotMatch(loginMarkup, /短信免密|短信验证码/);

// 注册表单必须收集邮箱（登录凭证与密码找回通道），手机号降为选填。
const registerMarkup = renderToStaticMarkup(
  <AuthModal isOpen initialMode="register" onClose={() => undefined} onLoginSuccess={() => undefined} />,
);
assert.match(registerMarkup, /type="email"/);
assert.match(registerMarkup, /邮箱/);
assert.match(registerMarkup, /手机号/);
assert.match(registerMarkup, /选填/);

// 商品富文本应移除脚本和危险协议，同时保留常规排版标签。
const richText = sanitizeProductRichText('<p>安全介绍</p><script>alert(1)</script><a href="javascript:alert(1)" onclick="evil()">查看</a>');
assert.match(richText, /<p>安全介绍<\/p>/);
assert.doesNotMatch(richText, /script|onclick|javascript:/i);

// 物流进度必须跟后台真实状态走：已取消订单不套用物流里程碑，缺承运商与运单号时不得回落成写死的顺丰值。
const closedTrackingMarkup = renderToStaticMarkup(<OrderTracking status="cancelled" />);
assert.match(closedTrackingMarkup, /订单已取消/);
assert.doesNotMatch(closedTrackingMarkup, /顺丰/);
assert.doesNotMatch(closedTrackingMarkup, /SF19837482910/);

const pendingTrackingMarkup = renderToStaticMarkup(<OrderTracking status="processing" />);
assert.match(pendingTrackingMarkup, /承运商待分配/);
assert.doesNotMatch(pendingTrackingMarkup, /顺丰/);
assert.doesNotMatch(pendingTrackingMarkup, /SF19837482910/);

// 类目树常驻左侧：一级平铺可见，二三级默认收起，避免导航一多就在顶部看不全。
const categoryTree: PortalCategoryNode[] = [
  {
    id: '1',
    name: '数码电子',
    code: 'ELECTRONICS',
    level: 1,
    children: [
      {
        id: '11',
        name: '影音娱乐',
        code: 'ELECTRONICS_AUDIO',
        level: 2,
        children: [{ id: '111', name: '降噪耳机', code: 'ELECTRONICS_AUDIO_ANC', level: 3, children: [] }],
      },
    ],
  },
  { id: '2', name: '家居生活', code: 'HOME', level: 1, children: [] },
];

const railMarkup = renderToStaticMarkup(
  <CategoryRail categoryTree={categoryTree} selectedCategory="all" onSelectCategory={() => undefined} />,
);
assert.match(railMarkup, /aria-label="商品分类导航"/);
assert.match(railMarkup, /全部商品/);
assert.match(railMarkup, /数码电子/);
assert.match(railMarkup, /家居生活/);
assert.doesNotMatch(railMarkup, /影音娱乐/);
assert.doesNotMatch(railMarkup, /降噪耳机/);
assert.match(railMarkup, /aria-label="展开数码电子的下级分类"/);

// 桌面左栏走京东、淘宝那套：静态只渲染一级，下级靠 hover 弹出浮层，所以标记里既没有
// 内联展开按钮，也不该出现二三级名字；可点击性仍要靠 aria-expanded 暴露给读屏。
const flyoutMarkup = renderToStaticMarkup(
  <CategoryRail
    categoryTree={categoryTree}
    selectedCategory="all"
    onSelectCategory={() => undefined}
    variant="flyout"
  />,
);
assert.match(flyoutMarkup, /aria-label="商品分类导航"/);
assert.match(flyoutMarkup, /全部商品/);
assert.match(flyoutMarkup, /数码电子/);
assert.doesNotMatch(flyoutMarkup, /影音娱乐/);
assert.doesNotMatch(flyoutMarkup, /降噪耳机/);
assert.doesNotMatch(flyoutMarkup, /aria-label="展开数码电子的下级分类"/);
assert.match(flyoutMarkup, /aria-expanded="false"/);
assert.match(flyoutMarkup, /aria-controls="portal-category-flyout"/);

// 领券中心：首页只留一行横幅（锚点也挂在横幅上），完整券墙搬进弹层。
const couponFixtures: Coupon[] = [
  { code: 'C1', title: '满 199 减 50', discountAmount: 50, minSpend: 199, expiresAt: '2026-10-05', description: '全场通用', tag: 'cash', category: 'all', stockPercent: 60, highlight: true },
  { code: 'C2', title: '全场包邮券', discountAmount: 20, minSpend: 99, expiresAt: '2026-11-04', description: '包邮立减', tag: 'shipping', category: 'all', stockPercent: 30, highlight: false },
];

const couponBannerMarkup = renderToStaticMarkup(
  <CouponCenterBanner
    coupons={couponFixtures}
    claimedCouponCodes={['C1']}
    onOpenAll={() => undefined}
    onClaimAll={() => undefined}
  />,
);
assert.match(couponBannerMarkup, /id="coupon-center-section"/);
assert.match(couponBannerMarkup, /aria-label="领券中心"/);
assert.match(couponBannerMarkup, /1 张可领/);
assert.match(couponBannerMarkup, /已领 1\/2 张 · 最高可省 ¥70/);
assert.match(couponBannerMarkup, /aria-label="查看全部优惠券"/);
// 横幅只报统计数字，不铺券面：券标题只应出现在券墙（弹层）里。
assert.doesNotMatch(couponBannerMarkup, /满 199 减 50/);

// 全部领完时一键领取置灰，文案换成「已全部领取」。
const couponBannerAllClaimed = renderToStaticMarkup(
  <CouponCenterBanner
    coupons={couponFixtures}
    claimedCouponCodes={['C1', 'C2']}
    onOpenAll={() => undefined}
    onClaimAll={() => undefined}
  />,
);
assert.match(couponBannerAllClaimed, /已全部领取/);
assert.match(couponBannerAllClaimed, /disabled/);

// 券墙本体只作弹层内容：不再自带 section 锚点与页面级间距。
const couponWallMarkup = renderToStaticMarkup(
  <CouponCenter
    coupons={couponFixtures}
    claimedCouponCodes={['C1']}
    onClaimCoupon={() => undefined}
    onClaimAllCoupons={() => undefined}
  />,
);
assert.doesNotMatch(couponWallMarkup, /coupon-center-section/);
assert.match(couponWallMarkup, /满 199 减 50/);

// 券型在库里是 cash / shipping 这类标识，券卡上必须显示中文。
assert.match(couponWallMarkup, /tracking-wider[^>]*>满减</);
assert.match(couponWallMarkup, /tracking-wider[^>]*>包邮</);
assert.doesNotMatch(couponWallMarkup, />cash</);
assert.doesNotMatch(couponWallMarkup, />shipping</);

// 映射本身：大小写不敏感、未知值与中文自定义文案原样返回。
assert.equal(couponTagLabel('cash'), '满减');
assert.equal(couponTagLabel('SHIPPING'), '包邮');
assert.equal(couponTagLabel('新人立减'), '新人立减');
assert.equal(couponTagLabel(undefined), '');

// 商品带视频时，详情弹层的第一张媒体应渲染成可播放的 <video>，海报图回退到商品主图。
const videoProduct: Product = { ...baseProduct, videoUrl: 'https://example.com/demo-product.mp4' };
const videoMarkup = renderToStaticMarkup(
  <ProductQuickView
    product={videoProduct}
    isWishlisted={false}
    onClose={() => undefined}
    onAddToCart={() => undefined}
    onDirectBuy={() => undefined}
    onToggleWishlist={() => undefined}
  />,
);
assert.match(videoMarkup, /<video/);
assert.match(videoMarkup, /src="https:\/\/example\.com\/demo-product\.mp4"/);
assert.match(videoMarkup, /poster="https:\/\/example\.com\/headphones\.webp"/);
assert.match(videoMarkup, /视频演示/);

// 没有视频的商品不该出现空的视频位。
const noVideoMarkup = renderToStaticMarkup(
  <ProductQuickView
    product={baseProduct}
    isWishlisted={false}
    onClose={() => undefined}
    onAddToCart={() => undefined}
    onDirectBuy={() => undefined}
    onToggleWishlist={() => undefined}
  />,
);
assert.doesNotMatch(noVideoMarkup, /<video/);

// 从轮播直链进入时，商品不一定落在当前分页的商品列表里，App 会先挂一个只有 ID 的占位对象：
// 详情回来之前只能看到加载提示，不能把空标题、¥0、空媒体位这些骨架铺满弹窗。
const placeholderProduct: Product = {
  ...baseProduct,
  id: 'prod-7',
  title: '',
  subtitle: '',
  price: 0,
  originalPrice: 0,
  badge: undefined,
  images: [],
};
const placeholderMarkup = renderToStaticMarkup(
  <ProductQuickView
    product={placeholderProduct}
    detailLoading
    isWishlisted={false}
    onClose={() => undefined}
    onAddToCart={() => undefined}
    onDirectBuy={() => undefined}
    onToggleWishlist={() => undefined}
  />,
);
assert.match(placeholderMarkup, /正在加载商品详情…/);
assert.doesNotMatch(placeholderMarkup, /¥/);
assert.doesNotMatch(placeholderMarkup, /<video/);
assert.doesNotMatch(placeholderMarkup, /测试降噪耳机/);

// 占位对象的详情接口失败时要给出错误和重试入口，而不是留一张空白弹窗。
const placeholderErrorMarkup = renderToStaticMarkup(
  <ProductQuickView
    product={placeholderProduct}
    detailError="商品详情不存在或已下架"
    onRetryDetail={() => undefined}
    isWishlisted={false}
    onClose={() => undefined}
    onAddToCart={() => undefined}
    onDirectBuy={() => undefined}
    onToggleWishlist={() => undefined}
  />,
);
assert.match(placeholderErrorMarkup, /商品详情不存在或已下架/);
assert.match(placeholderErrorMarkup, /重新加载/);
assert.match(placeholderErrorMarkup, /aria-label="关闭商品详情"/);

console.log('shop-portal 组件冒烟测试通过：商品卡片、库存禁购、Banner 跳转数据、物流进度取数、左侧类目树（浮层与展开两态）、领券横幅与券墙（含券型中文标签）、商品详情视频位与占位加载态语义正常');