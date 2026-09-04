import assert from 'node:assert/strict';
import { renderToStaticMarkup } from 'react-dom/server';
import { ProductCard } from '../src/components/ProductCard';
import { sanitizeProductRichText } from '../src/components/ProductQuickView';
import { HeroBanner } from '../src/components/HeroBanner';
import { normalizeSmsPhone } from '../src/components/AuthModal';
import type { Product } from '../src/types/ecommerce';

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
assert.match(remoteBannerMarkup, /立即查看/);

// 认证手机号输入应统一清理格式并拒绝超出 E.164 长度范围的异常号码。
assert.equal(normalizeSmsPhone('+86', ' 138-0013-8000 '), '+8613800138000');
assert.equal(normalizeSmsPhone('+86', '123'), null);
assert.equal(normalizeSmsPhone('+86', '1234567890123456'), null);

// 商品富文本应移除脚本和危险协议，同时保留常规排版标签。
const richText = sanitizeProductRichText('<p>安全介绍</p><script>alert(1)</script><a href="javascript:alert(1)" onclick="evil()">查看</a>');
assert.match(richText, /<p>安全介绍<\/p>/);
assert.doesNotMatch(richText, /script|onclick|javascript:/i);

console.log('shop-portal 组件冒烟测试通过：商品卡片、库存禁购、Banner 跳转数据和键盘语义正常');
