import assert from 'node:assert/strict';
import { renderToStaticMarkup } from 'react-dom/server';
import { ProductCard } from '../src/components/ProductCard';
import { ProductDetailPage, sanitizeProductRichText } from '../src/components/ProductDetailPage';
import { HeroBanner } from '../src/components/HeroBanner';
import { OrderTracking } from '../src/components/OrderTracking';
import { CategoryRail } from '../src/components/CategoryRail';
import { CouponCenter } from '../src/components/CouponCenter';
import { CouponCenterBanner } from '../src/components/CouponCenterBanner';
import { couponTagLabel } from '../src/utils/couponTag';
import { selectPurchasedCartItemIds } from '../src/utils/cartCleanup';
import { ORDER_STATUS_FILTERS, formatOrderTime, formatPaymentMethod, matchesOrderStatusFilter } from '../src/utils/orderDisplay';
import { OrdersPage } from '../src/components/OrdersPage';
import { WishlistPage } from '../src/components/WishlistPage';
import { ComparePage } from '../src/components/ComparePage';
import { UserProfilePage } from '../src/components/UserProfilePage';
import { buildPortalHash, parsePortalRoute } from '../src/utils/portalRoute';
import { isEmail, AuthModal } from '../src/components/AuthModal';
import { createPendingLogistics } from '../src/components/CheckoutModal';
import { OrderSuccessModal } from '../src/components/OrderSuccessModal';
import type { Product, Coupon, Order, UserProfile } from '../src/types/ecommerce';
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

// 商品带视频时，详情页的第一张媒体应渲染成可播放的 <video>，海报图回退到商品主图。
const videoProduct: Product = { ...baseProduct, videoUrl: 'https://example.com/demo-product.mp4' };
const videoMarkup = renderToStaticMarkup(
  <ProductDetailPage
    product={videoProduct}
    isWishlisted={false}
    onBackToHome={() => undefined}
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
  <ProductDetailPage
    product={baseProduct}
    isWishlisted={false}
    onBackToHome={() => undefined}
    onAddToCart={() => undefined}
    onDirectBuy={() => undefined}
    onToggleWishlist={() => undefined}
  />,
);
assert.doesNotMatch(noVideoMarkup, /<video/);

// 从轮播、Banner 直链进入时，商品不一定落在当前分页的商品列表里，App 会先挂一个只有 ID 的占位对象：
// 详情回来之前只能看到加载提示，不能把空标题、¥0、空媒体位这些骨架铺满整页。
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
  <ProductDetailPage
    product={placeholderProduct}
    detailLoading
    isWishlisted={false}
    onBackToHome={() => undefined}
    onAddToCart={() => undefined}
    onDirectBuy={() => undefined}
    onToggleWishlist={() => undefined}
  />,
);
assert.match(placeholderMarkup, /正在加载商品详情…/);
assert.doesNotMatch(placeholderMarkup, /¥/);
assert.doesNotMatch(placeholderMarkup, /<video/);
assert.doesNotMatch(placeholderMarkup, /测试降噪耳机/);

// 占位对象的详情接口失败时要给出错误和重试入口，而不是留一张空白页。
const placeholderErrorMarkup = renderToStaticMarkup(
  <ProductDetailPage
    product={placeholderProduct}
    detailError="商品详情不存在或已下架"
    onRetryDetail={() => undefined}
    isWishlisted={false}
    onBackToHome={() => undefined}
    onAddToCart={() => undefined}
    onDirectBuy={() => undefined}
    onToggleWishlist={() => undefined}
  />,
);
assert.match(placeholderErrorMarkup, /商品详情不存在或已下架/);
assert.match(placeholderErrorMarkup, /重新加载/);
// 整页视图没有「关闭」这个语义了，占位态给的是回首页的出口。
assert.match(placeholderErrorMarkup, /返回商城首页/);

// 详情页是整页而不是浮层：不能再有遮罩与滚动锁留下的固定定位，得有面包屑与页面级 h1。
assert.match(videoMarkup, /aria-label="面包屑"/);
assert.match(videoMarkup, /<h1[^>]*>测试降噪耳机<\/h1>/);
assert.match(videoMarkup, /返回首页继续逛/);
assert.doesNotMatch(videoMarkup, /fixed inset-0/);

// 结算页不得自行编造物流：后台选「中通快递」发货时，本地编的顺丰承运商、顺丰单号与
// 「1-2 日顺丰送达」会一直盖住服务端真实值，订单卡片就与后台对不上。
const pendingLogistics = createPendingLogistics();
assert.equal(pendingLogistics.trackingNumber, '');
assert.doesNotMatch(JSON.stringify(pendingLogistics), /顺丰|中通|圆通|韵达/);

const draftOrder: Order = {
  id: 'ord-draft-1',
  orderNumber: 'AO20260919001',
  ...pendingLogistics,
  createdAt: '2026-09-19 15:32:00',
  status: 'placed',
  statusLabel: '待付款',
  items: [],
  subtotal: 0,
  discount: 0,
  shippingFee: 0,
  totalPaid: 0.02,
  shippingAddress: {
    id: 'addr-draft-1',
    receiverName: '清水师兄',
    phone: '186****3662',
    province: '广东省',
    city: '广州市',
    district: '白云区',
    detail: '金沙街道测试地址',
    isDefault: true,
  },
  paymentMethod: '微信支付',
};
const draftSuccessMarkup = renderToStaticMarkup(
  <OrderSuccessModal
    order={draftOrder}
    onClose={() => undefined}
    onViewAllOrders={() => undefined}
    onContinueShopping={() => undefined}
  />,
);
assert.doesNotMatch(draftSuccessMarkup, /顺丰/);
assert.match(draftSuccessMarkup, /运单号/);
assert.match(draftSuccessMarkup, /发货后由承运商分配/);

// 服务端购物车返回数字商品 ID、结算条目是 prod-* 字符串，匹配前必须归一化。
// 回归缺陷：两端口径不同导致已购商品从来删不掉，支付后购物车里仍然有它。
assert.deepEqual(
  selectPurchasedCartItemIds(
    [{ id: 29, productId: 65 }, { id: 30, productId: 125 }],
    [{ productId: 'prod-65' }],
  ),
  [29],
);
// 结算条目为空（正常流程不会出现，防误删）或服务端购物车为空时，都不得删除任何条目。
assert.deepEqual(selectPurchasedCartItemIds([{ id: 31, productId: 7 }], []), []);
assert.deepEqual(selectPurchasedCartItemIds([], [{ productId: 'prod-7' }]), []);
// 非法商品 ID（演示数据、秒杀占位）不参与匹配，避免把服务端购物车清空。
assert.deepEqual(selectPurchasedCartItemIds([{ id: 32, productId: 7 }], [{ productId: 'prod-NaN' }]), []);

// ===== 整页视图路由 =====

assert.deepEqual(parsePortalRoute(''), { view: 'home' });
assert.deepEqual(parsePortalRoute('#/'), { view: 'home' });
assert.deepEqual(parsePortalRoute('#/orders'), { view: 'orders' });
// 页签与选中订单进查询串：跳去商品详情或评价页再返回，筛选与选中项还能对上。
assert.deepEqual(
  parsePortalRoute('#/orders?status=shipped&order=26'),
  { view: 'orders', status: 'shipped', order: '26' },
);
// `all` 是缺省页签，不进地址，同一个画面只留一种地址。
assert.deepEqual(parsePortalRoute('#/orders?status=all'), { view: 'orders' });
// 认不出的页签、超长或带尖括号的订单 ID 一律丢掉，不做「原样回读地址栏」。
assert.deepEqual(parsePortalRoute('#/orders?status=nope&order=%3Cscript%3E'), { view: 'orders' });
assert.deepEqual(
  parsePortalRoute(`#/orders?order=${'a'.repeat(41)}`),
  { view: 'orders' },
  '超长订单 ID 不应进路由',
);
assert.deepEqual(parsePortalRoute('#/wishlist'), { view: 'wishlist' });
assert.deepEqual(parsePortalRoute('#/compare'), { view: 'compare' });
// 个人中心三个子视图各有一条地址；资料页是缺省子视图，不带路径段。
assert.deepEqual(parsePortalRoute('#/account'), { view: 'account' });
assert.deepEqual(parsePortalRoute('#/account/profile'), { view: 'account' });
assert.deepEqual(parsePortalRoute('#/account/coupons'), { view: 'account', section: 'coupons' });
assert.deepEqual(parsePortalRoute('#/account/reviews'), { view: 'account', section: 'reviews' });
assert.deepEqual(
  parsePortalRoute('#/account/reviews?product=65'),
  { view: 'account', section: 'reviews', productId: 65 },
);
// 认不出的子视图落回资料页而不是首页：路径主体（个人中心）是认得的。
assert.deepEqual(parsePortalRoute('#/account/nope'), { view: 'account' });
// 高亮商品只在评价子视图下有意义，别把参数带到券包地址上。
assert.deepEqual(parsePortalRoute('#/account/coupons?product=65'), { view: 'account', section: 'coupons' });
assert.deepEqual(parsePortalRoute('#/account/reviews?product=abc'), { view: 'account', section: 'reviews' });
// 认不出的路由路径回首页：地址栏能表达的状态与实际画面必须一致。
assert.deepEqual(parsePortalRoute('#/not-a-view'), { view: 'home' });
// 正文锚点不是路由。这条是回归点：跳过导航链接写的是 #portal-main-content，
// 一旦被当成路由解析（旧实现只认 #/orders、其余全当首页），用户点一次跳过链接就会被打回首页。
assert.equal(parsePortalRoute('#portal-main-content'), null);
assert.equal(parsePortalRoute('#coupon-center-section'), null);

// 商品详情：商品 ID 与页内页签都进地址，刷新、分享链接才能落到同一个商品、同一个页签。
assert.deepEqual(parsePortalRoute('#/product/prod-7'), { view: 'product', productId: 'prod-7' });
// 订单明细、Banner 的 linkTarget 里可能是纯数字写法，归一化后同一商品只有一种地址。
assert.deepEqual(parsePortalRoute('#/product/7'), { view: 'product', productId: 'prod-7' });
assert.deepEqual(
  parsePortalRoute('#/product/prod-7?tab=reviews'),
  { view: 'product', productId: 'prod-7', tab: 'reviews' },
);
assert.deepEqual(
  parsePortalRoute('#/product/prod-7?tab=reviews&variant=%E9%BB%91%E8%89%B2'),
  { view: 'product', productId: 'prod-7', tab: 'reviews', variant: '黑色' },
);
// 页签取值非法时当作没带页签，不能把非法值透给页面。
assert.deepEqual(parsePortalRoute('#/product/prod-7?tab=nope'), { view: 'product', productId: 'prod-7' });
// 光有 #/product 而没有 ID 属于残缺地址，回首页，不停在一个没有商品的商品页上。
assert.deepEqual(parsePortalRoute('#/product'), { view: 'home' });
assert.deepEqual(parsePortalRoute('#/product/'), { view: 'home' });

assert.equal(buildPortalHash({ view: 'home' }), '');
assert.equal(buildPortalHash({ view: 'orders' }), '#/orders');
assert.equal(buildPortalHash({ view: 'orders', status: 'shipped' }), '#/orders?status=shipped');
assert.equal(
  buildPortalHash({ view: 'orders', status: 'shipped', order: '26' }),
  '#/orders?status=shipped&order=26',
);
// `all` 与不写等价，生成端也不该把它写进地址。
assert.equal(buildPortalHash({ view: 'orders', status: 'all' }), '#/orders');
assert.equal(buildPortalHash({ view: 'wishlist' }), '#/wishlist');
assert.equal(buildPortalHash({ view: 'compare' }), '#/compare');
assert.equal(buildPortalHash({ view: 'account' }), '#/account');
// 资料页是缺省子视图：显式写 profile 与不写生成同一个地址。
assert.equal(buildPortalHash({ view: 'account', section: 'profile' }), '#/account');
assert.equal(buildPortalHash({ view: 'account', section: 'coupons' }), '#/account/coupons');
assert.equal(
  buildPortalHash({ view: 'account', section: 'reviews', productId: 65 }),
  '#/account/reviews?product=65',
);
// 高亮参数只在评价子视图下生成，否则同一屏会出现两个地址。
assert.equal(
  buildPortalHash({ view: 'account', section: 'coupons', productId: 65 }),
  '#/account/coupons',
);
assert.equal(buildPortalHash({ view: 'product', productId: 'prod-7' }), '#/product/prod-7');
assert.equal(
  buildPortalHash({ view: 'product', productId: 'prod-7', tab: 'reviews' }),
  '#/product/prod-7?tab=reviews',
);
// 生成与解析必须闭环，否则 pushState 写下的地址与后退时读到的视图会对不上。
(['home', 'orders', 'wishlist', 'compare', 'account'] as const).forEach((view) => {
  assert.deepEqual(parsePortalRoute(buildPortalHash({ view })), { view }, `${view} 的路由生成与解析应闭环`);
});
([
  { view: 'orders', status: 'shipped', order: '26' },
  { view: 'orders', order: 'ord-preset-001' },
  { view: 'account', section: 'coupons' },
  { view: 'account', section: 'reviews', productId: 65 },
] as const).forEach((route) => {
  assert.deepEqual(
    parsePortalRoute(buildPortalHash(route)),
    route,
    `带参数的整页路由 ${JSON.stringify(route)} 应闭环`,
  );
});
([
  { view: 'product', productId: 'prod-7' },
  { view: 'product', productId: 'prod-7', tab: 'reviews' },
  { view: 'product', productId: 'prod-7', tab: 'specs', variant: '黑色 / 大号' },
] as const).forEach((route) => {
  assert.deepEqual(
    parsePortalRoute(buildPortalHash(route)),
    route,
    `带参数的商品路由 ${JSON.stringify(route)} 应闭环`,
  );
});

// ===== 订单中心（整页）=====

// 页签口径按用户动作分组：服务端六态里 paid/processing 同为「待发货」、shipped/out_for_delivery 同为「待收货」。
const orderWithStatus = (status: string): Order => ({ ...draftOrder, id: `ord-${status}`, status });
assert.equal(matchesOrderStatusFilter(orderWithStatus('placed'), 'unpaid'), true);
assert.equal(matchesOrderStatusFilter(orderWithStatus('paid'), 'unshipped'), true);
assert.equal(matchesOrderStatusFilter(orderWithStatus('processing'), 'unshipped'), true);
assert.equal(matchesOrderStatusFilter(orderWithStatus('shipped'), 'shipped'), true);
assert.equal(matchesOrderStatusFilter(orderWithStatus('out_for_delivery'), 'shipped'), true);
assert.equal(matchesOrderStatusFilter(orderWithStatus('delivered'), 'finished'), true);
assert.equal(matchesOrderStatusFilter(orderWithStatus('refunding'), 'afterSale'), true);
assert.equal(matchesOrderStatusFilter(orderWithStatus('refunded'), 'afterSale'), true);
assert.equal(matchesOrderStatusFilter(orderWithStatus('cancelled'), 'cancelled'), true);
// 一单只能落进一个状态页签，否则计数会重复、页签之间会串单。
assert.equal(matchesOrderStatusFilter(orderWithStatus('delivered'), 'shipped'), false);
assert.equal(matchesOrderStatusFilter(orderWithStatus('shipped'), 'finished'), false);
// 后端将来新增的状态不得被塞进任何具名页签，只在「全部订单」里出现。
const unknownStatus = 'some_new_status';
assert.equal(matchesOrderStatusFilter(orderWithStatus(unknownStatus), 'all'), true);
ORDER_STATUS_FILTERS.filter(({ key }) => key !== 'all').forEach(({ key }) => {
  assert.equal(matchesOrderStatusFilter(orderWithStatus(unknownStatus), key), false, `${unknownStatus} 不应落进 ${key}`);
});

// 订单时间两个来源格式不同（服务端带毫秒、本地快照走 toLocaleString），列表里统一裁到分钟。
assert.equal(formatOrderTime('2026-09-19 17:51:13.061'), '2026-09-19 17:51');
assert.equal(formatOrderTime('2026/9/19 17:51:13'), '2026-09-19 17:51');
assert.equal(formatOrderTime(''), '-');
assert.equal(formatOrderTime('刚刚'), '刚刚');

// 支付方式在库里混存两种口径：支付单回写渠道码、结算页提交中文。渠道码不能直接上屏。
assert.equal(formatPaymentMethod('WECHAT_NATIVE'), '微信支付');
assert.equal(formatPaymentMethod('wechat_native'), '微信支付');
assert.equal(formatPaymentMethod('WECHAT'), '微信支付');
assert.equal(formatPaymentMethod('微信支付'), '微信支付');
assert.equal(formatPaymentMethod('支付宝'), '支付宝');
assert.equal(formatPaymentMethod(''), '在线支付');
assert.equal(formatPaymentMethod(undefined), '在线支付');
// 认不出的渠道码收起为通用文案，但已经是中文的原样保留，别把新渠道显示成英文串。
assert.equal(formatPaymentMethod('SOME_NEW_CHANNEL'), '在线支付');
assert.equal(formatPaymentMethod('云闪付'), '云闪付');

const shippedOrder: Order = {
  ...draftOrder,
  id: 'ord-shipped-1',
  orderNumber: 'AO20260919002',
  status: 'shipped',
  statusLabel: '运输中',
  carrier: '中通快递',
  trackingNumber: 'ZT99932232344',
  items: [
    { productId: 'prod-65', title: '无线蓝牙耳机 基础款', image: '', variantsSummary: '标准版', price: 0.01, quantity: 2 },
  ],
  // 同一屏里收货信息与物流卡片必须同口径：都给完整手机号等于把脱敏白做了。
  shippingAddress: { ...draftOrder.shippingAddress, phone: '18877777666', detail: '广州天安番禺节能科技园' },
  subtotal: 0.02,
  totalPaid: 0.02,
  paymentMethod: 'WECHAT_NATIVE',
};
const cancelledOrder: Order = {
  ...draftOrder,
  id: 'ord-cancelled-1',
  orderNumber: 'AO20260919003',
  status: 'cancelled',
  statusLabel: '已取消',
};

const ordersPageMarkup = renderToStaticMarkup(
  <OrdersPage
    orders={[shippedOrder, cancelledOrder]}
    onBackToHome={() => undefined}
    onApplyAfterSale={() => undefined}
  />,
);
// 整页形态：不再是覆盖全屏的弹层。
assert.doesNotMatch(ordersPageMarkup, /backdrop-blur|fixed inset-0/);
// 页签把订单按状态分流，计数与筛选结果一致（两家订单各占一个页签）。
assert.match(ordersPageMarkup, /全部订单<span[^>]*>2<\/span>/);
assert.match(ordersPageMarkup, /待收货<span[^>]*>1<\/span>/);
assert.match(ordersPageMarkup, /已取消<span[^>]*>1<\/span>/);
assert.match(ordersPageMarkup, /待付款<span[^>]*>0<\/span>/);
// 左列表两个订单都在，右详情默认落在列表首单上。
assert.match(ordersPageMarkup, /AO20260919002/);
assert.match(ordersPageMarkup, /AO20260919003/);
assert.match(ordersPageMarkup, /无线蓝牙耳机 基础款/);
assert.match(ordersPageMarkup, /金额明细/);
assert.match(ordersPageMarkup, /收货信息/);
assert.match(ordersPageMarkup, /实付款/);
// 支付方式必须上屏中文：详情区不能出现 WECHAT_NATIVE 这类渠道码。
assert.match(ordersPageMarkup, /微信支付/);
assert.doesNotMatch(ordersPageMarkup, /WECHAT_NATIVE/);
// 收货信息与物流卡片同口径脱敏，整页不得出现完整手机号与完整详细地址。
assert.match(ordersPageMarkup, /188\*\*\*\*7666/);
assert.doesNotMatch(ordersPageMarkup, /18877777666/);
assert.doesNotMatch(ordersPageMarkup, /广州天安番禺节能科技园/);
// 未选中订单时窄屏停在列表（左栏可见），URL 带了 order= 才停在详情。
assert.doesNotMatch(ordersPageMarkup, /lg:overflow-y-auto hidden lg:block/);

// 页签由地址承载：带 status=cancelled 进来时列表只剩已取消那一单。
const filteredOrdersMarkup = renderToStaticMarkup(
  <OrdersPage
    orders={[shippedOrder, cancelledOrder]}
    onBackToHome={() => undefined}
    statusFilter="cancelled"
  />,
);
assert.match(filteredOrdersMarkup, /AO20260919003/);
assert.doesNotMatch(filteredOrdersMarkup, /AO20260919002/);
assert.match(filteredOrdersMarkup, /已取消<span[^>]*>1<\/span>/);

// 带 order= 进来时窄屏直接停在详情：左栏收起（刷新后仍停在原单，不用再点一次）。
const selectedOrderMarkup = renderToStaticMarkup(
  <OrdersPage
    orders={[shippedOrder, cancelledOrder]}
    onBackToHome={() => undefined}
    statusFilter="cancelled"
    selectedOrderId="ord-cancelled-1"
  />,
);
assert.match(selectedOrderMarkup, /lg:overflow-y-auto hidden lg:block/);
assert.match(selectedOrderMarkup, /返回订单列表/);

// ===== 收藏页（整页）=====

const wishlistPageMarkup = renderToStaticMarkup(
  <WishlistPage
    products={[baseProduct, { ...baseProduct, id: 'prod-1002', title: '机械键盘', brand: 'HENFON', images: [] }]}
    onBackToHome={() => undefined}
    onAddToCart={() => undefined}
    onRemoveWishlist={() => undefined}
    onQuickView={() => undefined}
  />,
);
// 整页形态：弹窗外壳与滚动锁都应当去掉。
assert.doesNotMatch(wishlistPageMarkup, /backdrop-blur|fixed inset-0/);
assert.match(wishlistPageMarkup, /<h1[^>]*>我的心愿收藏<\/h1>/);
assert.match(wishlistPageMarkup, /共收藏 2 件心仪好物/);
// 商品逐件成卡，价格与两个操作都在。
assert.match(wishlistPageMarkup, /测试降噪耳机/);
assert.match(wishlistPageMarkup, /机械键盘/);
assert.match(wishlistPageMarkup, /¥399/);
assert.match(wishlistPageMarkup, /移入购物车/);
assert.match(wishlistPageMarkup, /aria-label="移除收藏 测试降噪耳机"/);
// 没有主图时给占位块，不能渲染成空 src 的破图。
assert.doesNotMatch(wishlistPageMarkup, /src=""/);

const emptyWishlistMarkup = renderToStaticMarkup(
  <WishlistPage
    products={[]}
    onBackToHome={() => undefined}
    onAddToCart={() => undefined}
    onRemoveWishlist={() => undefined}
    onQuickView={() => undefined}
  />,
);
assert.match(emptyWishlistMarkup, /暂无收藏的商品/);
assert.doesNotMatch(emptyWishlistMarkup, /移入购物车/);

// ===== 参数对比页（整页）=====

const compareProductA: Product = {
  ...baseProduct,
  id: 'prod-2001',
  title: '测试降噪耳机 Pro',
  specs: { 续航: '40 小时', 降噪深度: '48dB', 重量: '58g' },
};
const compareProductB: Product = {
  ...baseProduct,
  id: 'prod-2002',
  title: '测试降噪耳机 Lite',
  specs: { 续航: '24 小时', 降噪深度: '48dB', 重量: '46g' },
};

const comparePageMarkup = renderToStaticMarkup(
  <ComparePage
    products={[compareProductA, compareProductB]}
    onBackToHome={() => undefined}
    onRemoveProduct={() => undefined}
    onAddToCart={() => undefined}
    onBatchAddToCart={() => undefined}
    onQuickView={() => undefined}
    onClearAll={() => undefined}
  />,
);
// 整页形态：弹窗外壳与滚动锁都应当去掉。
assert.doesNotMatch(comparePageMarkup, /fixed inset-0/);
assert.match(comparePageMarkup, /<h1[^>]*>商品参数多维深度对比<\/h1>/);
assert.match(comparePageMarkup, /对比控制台/);
assert.match(comparePageMarkup, /已选 2 \/ 3 款商品/);
// 两栏视图切换与「仅看参数差异」都要留着 —— 它们是这个页面的主要操作。
assert.match(comparePageMarkup, /详细参数表格/);
assert.match(comparePageMarkup, /可视化图表/);
assert.match(comparePageMarkup, /仅看参数差异/);
// 对比商品逐列成卡，差异行按参数原样上屏。
assert.match(comparePageMarkup, /测试降噪耳机 Pro/);
assert.match(comparePageMarkup, /测试降噪耳机 Lite/);
assert.match(comparePageMarkup, /48dB/);
assert.match(comparePageMarkup, /40 小时/);
// 批量加购与单款加购都在。静态渲染下勾选态由 effect 初始化、跑不到，
// 所以只断言控件与文案，不断言「已勾选 N 款」这类派生数字。
assert.match(comparePageMarkup, /全选对比商品/);
assert.match(comparePageMarkup, /批量加入购物车 \(/);
assert.match(comparePageMarkup, /加购物车 \(默认规格\)/);
assert.match(comparePageMarkup, /从对比中移除/);

// 对比位空着时给空态，不再自动弹回首页 —— 地址栏停在 #/compare 就得有对应画面。
const emptyCompareMarkup = renderToStaticMarkup(
  <ComparePage
    products={[]}
    onBackToHome={() => undefined}
    onRemoveProduct={() => undefined}
    onAddToCart={() => undefined}
    onQuickView={() => undefined}
    onClearAll={() => undefined}
  />,
);
assert.match(emptyCompareMarkup, /暂未选择任何对比商品/);
assert.match(emptyCompareMarkup, /返回商品列表选择/);
// 没有对比商品时不出现批量加购按钮（页头副标题里的「批量加入购物车」是另一处文案，用括号区分）。
assert.doesNotMatch(emptyCompareMarkup, /批量加入购物车 \(/);

// ===== 个人中心页（整页）=====

const profileUser: UserProfile = {
  id: 'member-6',
  username: 'henfon_buyer',
  nickname: '清水师兄',
  email: 'buyer@example.com',
  phone: '186****3662',
  avatar: 'https://example.com/avatar.webp',
  memberLevel: '黄金VIP',
  points: 1280,
  balance: 88.5,
  couponsCount: 1,
  joinedDate: '2026-03-18',
};

const profilePageProps = {
  user: profileUser,
  claimedCoupons: couponFixtures,
  orders: [shippedOrder],
  products: [baseProduct],
  onSelectSection: () => undefined,
  onOpenOrders: () => undefined,
  onOpenWishlist: () => undefined,
  onBackToHome: () => undefined,
  onUpdateUser: () => undefined,
  onLogout: () => undefined,
  onOpenCouponCenter: () => undefined,
};

const profilePageMarkup = renderToStaticMarkup(<UserProfilePage {...profilePageProps} />);
// 整页形态：弹窗外壳、遮罩与滚动锁都应当去掉，改成面包屑 + 页面级 h1。
assert.doesNotMatch(profilePageMarkup, /backdrop-blur|fixed inset-0|aria-modal/);
assert.match(profilePageMarkup, /<h1[^>]*>个人中心<\/h1>/);
assert.match(profilePageMarkup, /aria-label="面包屑"/);
assert.match(profilePageMarkup, /清水师兄/);
assert.match(profilePageMarkup, /黄金VIP/);
// 三个子视图各有入口，且默认落在资料页。
assert.match(profilePageMarkup, /aria-label="个人中心分栏"/);
assert.match(profilePageMarkup, /资料与特权/);
assert.match(profilePageMarkup, /我的券包/);
assert.match(profilePageMarkup, /我的评价/);
assert.match(profilePageMarkup, /aria-current="page"/);
assert.match(profilePageMarkup, /当前享有 黄金VIP 特权/);
assert.match(profilePageMarkup, /累计消费/);
// 到订单中心、收藏页的快捷入口还在，退出登录与回首页都在页脚。
assert.match(profilePageMarkup, /我的订单/);
assert.match(profilePageMarkup, /心愿收藏/);
assert.match(profilePageMarkup, /退出登录/);
assert.match(profilePageMarkup, /返回商城首页/);
// 资料页不该顺带渲染券包内容，否则三个子视图会串在一起。
assert.doesNotMatch(profilePageMarkup, /我的优惠券包/);

const couponsPageMarkup = renderToStaticMarkup(<UserProfilePage {...profilePageProps} section="coupons" />);
assert.match(couponsPageMarkup, /我的优惠券包 \(2 张\)/);
assert.match(couponsPageMarkup, /满 ¥199 可用/);
// 券型在库里是 cash / shipping 这类标识，券卡上必须显示中文。
assert.match(couponsPageMarkup, /满减/);
assert.doesNotMatch(couponsPageMarkup, />cash</);
assert.match(couponsPageMarkup, /返回个人资料/);
assert.doesNotMatch(couponsPageMarkup, /最近订单明细/);

const reviewsPageMarkup = renderToStaticMarkup(<UserProfilePage {...profilePageProps} section="reviews" />);
assert.match(reviewsPageMarkup, /我的评价/);
// 静态渲染不跑 effect，评价列表停在未加载的空态文案上（真实取数在浏览器里验）。
assert.match(reviewsPageMarkup, /还没有提交过评价/);
assert.doesNotMatch(reviewsPageMarkup, /当前享有 黄金VIP 特权/);

console.log('shop-portal 组件冒烟测试通过：商品卡片、库存禁购、Banner 跳转数据、物流进度取数、左侧类目树（浮层与展开两态）、领券横幅与券墙（含券型中文标签）、商品详情整页（视频位、占位加载态、面包屑与页面 h1）、已购购物车条目清理匹配、整页视图路由（含正文锚点不当作路由、订单页签与选中订单、个人中心子视图、商品路由带参闭环）、订单中心整页（页签分流计数、列表↔详情双栏与 URL 承载的筛选/选中）、收藏页整页（卡片网格与空态）、参数对比页整页（多栏表格与空态）、个人中心整页（三个子视图与页脚出口）语义正常');