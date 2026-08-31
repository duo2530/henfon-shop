import assert from 'node:assert/strict';
import { renderToStaticMarkup } from 'react-dom/server';
import { ProductCard } from '../src/components/ProductCard';
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

console.log('shop-portal 组件冒烟测试通过：商品卡片、库存禁购、键盘语义和标签正常');
