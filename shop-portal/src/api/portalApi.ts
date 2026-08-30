import { Coupon, Product } from '../types/ecommerce';

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');

interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
}

interface CatalogProductRecord {
  id: number;
  categoryId?: number;
  categoryName?: string;
  productName: string;
  productCode: string;
  brandName?: string;
  shortDescription?: string;
  description?: string;
  price: number;
  marketPrice?: number;
  currentStock?: number;
  salesCount?: number;
  mainImageUrl?: string;
  tagsCsv?: string;
}

interface ProductPage {
  records: CatalogProductRecord[];
}

interface MarketingCouponRecord {
  couponCode: string;
  couponTitle: string;
  discountAmount: number;
  minSpend: number;
  categoryCode?: string;
  tag?: string;
  description?: string;
  endAt: string;
}

export interface PortalBanner {
  id: number;
  bannerTitle: string;
  bannerTag?: string;
  subtitle?: string;
  imageUrl: string;
  linkTarget?: string;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
  });
  if (!response.ok) {
    throw new Error(`门户接口请求失败：${response.status}`);
  }
  const result = (await response.json()) as ApiResponse<T>;
  if (result.code !== '0') {
    throw new Error(result.message || '门户接口返回失败');
  }
  return result.data;
}

function mapCategory(categoryName?: string): Product['category'] {
  const value = categoryName || '';
  if (value.includes('数码') || value.includes('电子')) return 'digital';
  if (value.includes('家居')) return 'home';
  if (value.includes('服饰')) return 'fashion';
  if (value.includes('影音')) return 'audio';
  if (value.includes('户外')) return 'outdoor';
  return 'lifestyle';
}

function mapProduct(record: CatalogProductRecord): Product {
  const originalPrice = Number(record.marketPrice || record.price);
  const price = Number(record.price || 0);
  return {
    id: `prod-${record.id}`,
    title: record.productName,
    subtitle: record.shortDescription || '',
    category: mapCategory(record.categoryName),
    categoryLabel: record.categoryName || '精选商品',
    brand: record.brandName || 'HENFON',
    price,
    originalPrice,
    rating: 5,
    reviewCount: 0,
    salesCount: Number(record.salesCount || 0),
    stock: Number(record.currentStock || 0),
    badge: originalPrice > price ? '直降' : undefined,
    images: record.mainImageUrl ? [record.mainImageUrl] : [],
    features: [],
    specs: {},
    description: record.description || record.shortDescription || '',
    isFreeShipping: true,
    deliveryEstimate: '预计 2-3 天送达',
  };
}

export async function fetchPortalProducts(keyword?: string): Promise<Product[]> {
  const query = keyword ? `?keyword=${encodeURIComponent(keyword)}` : '';
  const page = await request<ProductPage>(`/api/portal/catalog/products${query}`);
  return (page.records || []).map(mapProduct);
}

export async function fetchPortalProductDetail(productId: string): Promise<Product | null> {
  const numericId = productId.replace(/^prod-/, '');
  const detail = await request<{
    product: CatalogProductRecord;
    features?: Array<{ featureText: string }>;
    specs?: Array<{ specName: string; specValue: string }>;
    media?: Array<{ mediaUrl?: string; isCover?: number }>;
  }>(`/api/portal/catalog/products/${numericId}`);
  if (!detail?.product) return null;
  const product = mapProduct(detail.product);
  product.features = (detail.features || []).map((item) => item.featureText);
  product.specs = Object.fromEntries((detail.specs || []).map((item) => [item.specName, item.specValue]));
  const mediaUrls = (detail.media || []).map((item) => item.mediaUrl).filter((url): url is string => Boolean(url));
  product.images = Array.from(new Set([...mediaUrls, ...product.images]));
  return product;
}

export async function fetchPortalCoupons(): Promise<Coupon[]> {
  const records = await request<MarketingCouponRecord[]>('/api/portal/marketing/coupons');
  return (records || []).map((coupon) => ({
    code: coupon.couponCode,
    title: coupon.couponTitle,
    discountAmount: Number(coupon.discountAmount || 0),
    minSpend: Number(coupon.minSpend || 0),
    expiresAt: coupon.endAt,
    description: coupon.description || '',
    tag: coupon.tag,
    category: coupon.categoryCode || 'all',
  }));
}

export async function fetchPortalBanners(): Promise<PortalBanner[]> {
  return request<PortalBanner[]>('/api/portal/content/banners');
}

export interface PortalCartRecord {
  id: number;
  productId: number;
  skuId?: number;
  quantity: number;
  selected: number;
}

export interface PortalOrderRecord {
  id: number;
  orderNo: string;
  orderStatus: number;
  paymentMethod?: string;
  subtotalAmount: number;
  discountAmount: number;
  freightAmount: number;
  paidAmount: number;
  receiverName: string;
  receiverPhone: string;
  receiverProvince?: string;
  receiverCity?: string;
  receiverDistrict?: string;
  receiverAddress: string;
  logisticsCompany?: string;
  trackingNo?: string;
  createdAt: string;
}

export async function fetchPortalCart(memberId: number): Promise<PortalCartRecord[]> {
  return request<PortalCartRecord[]>(`/api/portal/trade/cart?memberId=${memberId}`);
}

export async function addPortalCartItem(memberId: number, productId: number, quantity: number, skuId?: number) {
  return request<number>('/api/portal/trade/cart/items', {
    method: 'POST',
    body: JSON.stringify({ memberId, productId, skuId, quantity, selected: 1 }),
  });
}

export async function deletePortalCartItem(id: number) {
  return request<void>(`/api/portal/trade/cart/items/${id}`, { method: 'DELETE' });
}

export async function updatePortalCartItem(id: number, quantity?: number, selected?: boolean) {
  return request<void>(`/api/portal/trade/cart/items/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ quantity, selected: selected === undefined ? undefined : selected ? 1 : 0 }),
  });
}

export async function fetchPortalOrders(memberId: number): Promise<PortalOrderRecord[]> {
  return request<PortalOrderRecord[]>(`/api/portal/trade/orders?memberId=${memberId}`);
}

export interface PortalOrderCreatePayload {
  memberId: number;
  items: Array<{
    productId?: number;
    skuId?: number;
    productName: string;
    skuName?: string;
    skuCode?: string;
    imageUrl?: string;
    unitPrice: number;
    quantity: number;
  }>;
  receiverName: string;
  receiverPhone: string;
  receiverProvince?: string;
  receiverCity?: string;
  receiverDistrict?: string;
  receiverAddress: string;
  paymentMethod?: string;
  subtotalAmount: number;
  discountAmount: number;
  freightAmount: number;
  payableAmount: number;
}

export async function createPortalOrder(payload: PortalOrderCreatePayload): Promise<PortalOrderRecord> {
  return request<PortalOrderRecord>('/api/portal/trade/orders', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export async function cancelPortalOrder(memberId: number, orderId: number, reason?: string): Promise<void> {
  return request<void>(`/api/portal/trade/orders/${orderId}/cancel?memberId=${memberId}`, {
    method: 'PUT',
    body: JSON.stringify({ reason }),
  });
}

export async function confirmPortalOrder(memberId: number, orderId: number): Promise<void> {
  return request<void>(`/api/portal/trade/orders/${orderId}/confirm?memberId=${memberId}`, {
    method: 'PUT',
  });
}

export async function fetchPortalFavorites(memberId: number): Promise<Array<{ productId: number }>> {
  return request<Array<{ productId: number }>>(`/api/portal/member/favorites?memberId=${memberId}`);
}

export async function togglePortalFavorite(memberId: number, productId: number): Promise<boolean> {
  return request<boolean>(`/api/portal/member/favorites/${productId}/toggle?memberId=${memberId}`, { method: 'POST' });
}

export interface PortalAddressRecord {
  id: number;
  receiverName: string;
  receiverPhone: string;
  province: string;
  city: string;
  district: string;
  detailAddress: string;
  addressTag?: string;
  isDefault: number;
}

export async function fetchPortalAddresses(memberId: number): Promise<PortalAddressRecord[]> {
  return request<PortalAddressRecord[]>(`/api/portal/member/addresses?memberId=${memberId}`);
}

export async function savePortalAddress(payload: {
  id?: number;
  memberId: number;
  receiverName: string;
  receiverPhone: string;
  province: string;
  city: string;
  district: string;
  detailAddress: string;
  addressTag?: string;
  isDefault?: number;
}) {
  return request<number>('/api/portal/member/addresses', { method: 'POST', body: JSON.stringify(payload) });
}

export async function updatePortalAddress(payload: {
  id: number;
  memberId: number;
  receiverName: string;
  receiverPhone: string;
  province: string;
  city: string;
  district: string;
  detailAddress: string;
  addressTag?: string;
  isDefault?: number;
}) {
  return request<void>(`/api/portal/member/addresses/${payload.id}`, { method: 'PUT', body: JSON.stringify(payload) });
}

export async function deletePortalAddress(memberId: number, addressId: number) {
  return request<void>(`/api/portal/member/addresses/${addressId}?memberId=${memberId}`, { method: 'DELETE' });
}

export async function setDefaultPortalAddress(memberId: number, addressId: number) {
  return request<void>(`/api/portal/member/addresses/${addressId}/default?memberId=${memberId}`, { method: 'PUT' });
}
