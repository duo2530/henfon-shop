import assert from 'node:assert/strict';
import {
  addPortalCartItem,
  createPortalAfterSale,
  createPortalOrder,
  createPortalPayment,
  fetchPortalPayment,
  fetchPortalProductDetail,
  fetchPortalProducts,
  fetchPortalProductsPage,
  hasPortalMemberSession,
  loginPortalMember,
} from '../src/api/portalApi';

class MemoryStorage implements Storage {
  private readonly values = new Map<string, string>();

  get length(): number { return this.values.size; }
  clear(): void { this.values.clear(); }
  getItem(key: string): string | null { return this.values.get(key) ?? null; }
  key(index: number): string | null { return Array.from(this.values.keys())[index] ?? null; }
  removeItem(key: string): void { this.values.delete(key); }
  setItem(key: string, value: string): void { this.values.set(key, String(value)); }
}

const storage = new MemoryStorage();
Object.defineProperty(globalThis, 'localStorage', { value: storage, configurable: true });
Object.defineProperty(globalThis, 'navigator', { value: { onLine: true }, configurable: true });
Object.defineProperty(globalThis, 'window', {
  value: { setTimeout, dispatchEvent: () => true },
  configurable: true,
});

const jsonResponse = (data: unknown, status = 200) => ({
  status,
  ok: status >= 200 && status < 300,
  json: async () => ({ code: '0', message: 'ok', data }),
});

globalThis.fetch = (async (input: RequestInfo | URL) => {
  const url = String(input);
  if (url.includes('/api/portal/auth/login')) {
    return jsonResponse({ accessToken: 'access-token', refreshToken: 'refresh-token', expiresInSeconds: 1800, memberId: 1, username: 'demo', nickname: '测试会员', memberLevel: '普通会员', points: 10, balance: 0 });
  }
  // 详情分支必须排在列表判断之前：两者路径前缀相同，先命中列表分支就拿不到 media 了。
  if (/\/api\/portal\/catalog\/products\/\d+$/.test(url)) {
    return jsonResponse({
      product: {
        id: 7, productName: '三模机械键盘 基础款', productCode: 'PROD-SEED-003',
        price: 399, marketPrice: 499, currentStock: 20, mainImageUrl: 'https://example.com/keyboard.jpg',
      },
      media: [
        { mediaType: 'IMAGE', mediaUrl: 'https://example.com/keyboard-2.jpg', isCover: 1 },
        { mediaType: 'VIDEO', mediaUrl: 'https://example.com/keyboard-demo.mp4', mediaTitle: '商品动态演示' },
      ],
    });
  }
  if (url.includes('/api/portal/catalog/products')) {
    const current = url.includes('current=2') ? 2 : 1;
    return jsonResponse({ records: [{ id: 1001, productName: '测试商品', productCode: 'SKU-1001', categoryName: '影音', price: 199, marketPrice: 299, currentStock: 20, salesCount: 3, shortDescription: '冒烟测试商品' }], total: 41, size: url.includes('size=1') ? 1 : 20, current, pages: url.includes('size=1') ? 41 : 3 });
  }
  if (url.endsWith('/api/portal/trade/cart/items')) return jsonResponse(9001);
  if (url.includes('/api/portal/trade/orders') && !url.includes('/payment')) {
    return jsonResponse({ id: 3001, orderNo: 'HS202608310001', status: 10, payableAmount: 199 });
  }
  if (url.includes('/api/portal/payment/orders/') && !url.includes('/close')) {
    return jsonResponse({ id: 5001, paymentNo: 'PAY-5001', orderId: 3001, orderNo: 'HS202608310001', channel: 'MOCK', status: url.includes('memberId=1') && url.includes('PAY-5001') ? 2 : 1, amount: 199, createdAt: '2026-08-31T12:00:00' });
  }
  if (url.includes('/api/portal/trade/after-sales')) {
    return jsonResponse({ id: 7001, afterSaleNo: 'AS-7001', orderId: 3001, status: 10, afterSaleType: 1, reason: '不需要了', refundAmount: 199, createdAt: '2026-08-31T12:00:00', updatedAt: '2026-08-31T12:00:00' });
  }
  throw new Error(`未覆盖的冒烟请求：${url}`);
}) as typeof fetch;

const auth = await loginPortalMember('demo', 'password');
assert.equal(auth.memberId, 1);
assert.equal(hasPortalMemberSession(), true);

const products = await fetchPortalProducts({ keyword: '测试' });
assert.equal(products[0]?.id, 'prod-1001');
assert.equal(products[0]?.stock, 20);
const productPage = await fetchPortalProductsPage({ current: 2, size: 1 });
assert.equal(productPage.current, 2);
assert.equal(productPage.pages, 41);
assert.equal(productPage.total, 41);

// 详情接口返回的视频要落到 videoUrl（弹层播放器只认这个字段），且不能混进图片列表被 <img> 加载。
const productDetail = await fetchPortalProductDetail('prod-7');
assert.equal(productDetail?.videoUrl, 'https://example.com/keyboard-demo.mp4');
assert.deepEqual(productDetail?.images, ['https://example.com/keyboard-2.jpg', 'https://example.com/keyboard.jpg']);
assert.ok(!productDetail?.images.some((url) => url.endsWith('.mp4')), '视频不应出现在图片列表里');

const cartItemId = await addPortalCartItem(1, 1001, 1, 2001);
assert.equal(cartItemId, 9001);

const order = await createPortalOrder({
  memberId: 1,
  idempotencyKey: 'flow-smoke-1',
  items: [{ productId: 1001, skuId: 2001, productName: '测试商品', unitPrice: 199, quantity: 1 }],
  receiverName: '测试会员',
  receiverPhone: '138****0000',
  receiverAddress: '测试地址',
  subtotalAmount: 199,
  discountAmount: 0,
  freightAmount: 0,
  payableAmount: 199,
});
assert.equal(order.id, 3001);

const payment = await createPortalPayment(1, 3001, 'MOCK');
assert.equal(payment.paymentNo, 'PAY-5001');
const paid = await fetchPortalPayment(1, payment.paymentNo);
assert.equal(paid.status, 2);

const afterSale = await createPortalAfterSale(1, 3001, { afterSaleType: 1, reason: '不需要了', refundAmount: 199 });
assert.equal(afterSale.id, 7001);

console.log('shop-portal 关键流程冒烟测试通过：登录 → 商品 → 购物车 → 下单 → 支付查询 → 售后');
