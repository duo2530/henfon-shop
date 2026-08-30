import { Coupon, Product, ProductSku, ProductVariant } from '../types/ecommerce';

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const MEMBER_TOKEN_KEY = 'henfon_shop_member_token';
const MEMBER_REFRESH_TOKEN_KEY = 'henfon_shop_member_refresh_token';

interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
}

export interface MemberAuthResponse {
  accessToken: string;
  expiresInSeconds: number;
  refreshToken: string;
  memberId: number;
  username: string;
  nickname: string;
  memberLevel: string;
  points: number;
  balance: number;
  phone?: string;
  email?: string;
  avatarUrl?: string;
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

interface CatalogSkuRecord {
  id: number;
  skuCode: string;
  skuName: string;
  attributesJson?: string | Record<string, string | number>;
  price: number;
  marketPrice?: number;
  stock?: number;
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
  const token = localStorage.getItem(MEMBER_TOKEN_KEY);
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers || {}),
    },
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

function parseSkuAttributes(value?: string | Record<string, string | number>): Record<string, string> {
  if (!value) return {};
  const parsed = typeof value === 'string' ? (() => {
    try {
      return JSON.parse(value) as unknown;
    } catch {
      return {};
    }
  })() : value;
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {};
  return Object.fromEntries(Object.entries(parsed as Record<string, unknown>)
    .filter(([, item]) => item !== null && item !== undefined)
    .map(([key, item]) => [key, String(item)]));
}

function mapSku(record: CatalogSkuRecord): ProductSku {
  return {
    id: record.id,
    skuCode: record.skuCode,
    skuName: record.skuName,
    attributes: parseSkuAttributes(record.attributesJson),
    price: Number(record.price || 0),
    marketPrice: Number(record.marketPrice || record.price || 0),
    stock: Number(record.stock || 0),
  };
}

function buildSkuVariants(skus: ProductSku[], basePrice: number): ProductVariant[] {
  const values = new Map<string, Map<string, ProductSku>>();
  skus.forEach((sku) => Object.entries(sku.attributes).forEach(([name, label]) => {
    const options = values.get(name) || new Map<string, ProductSku>();
    if (!options.has(label)) options.set(label, sku);
    values.set(name, options);
  }));
  return Array.from(values.entries()).map(([name, options]) => ({
    name,
    options: Array.from(options.entries()).map(([label, sku]) => ({
      id: String(sku.id),
      label,
      priceModifier: Number((sku.price - basePrice).toFixed(2)),
    })),
  }));
}

export async function fetchPortalProducts(keyword?: string): Promise<Product[]> {
  const query = keyword ? `?keyword=${encodeURIComponent(keyword)}` : '';
  const page = await request<ProductPage>(`/api/portal/catalog/products${query}`);
  return (page.records || []).map(mapProduct);
}

export async function loginPortalMember(account: string, password: string): Promise<MemberAuthResponse> {
  const response = await request<MemberAuthResponse>('/api/portal/auth/login', {
    method: 'POST',
    body: JSON.stringify({ account, password }),
  });
  saveMemberTokens(response);
  return response;
}

export async function registerPortalMember(payload: {
  username: string;
  password: string;
  nickname: string;
  phone?: string;
  email?: string;
}): Promise<MemberAuthResponse> {
  const response = await request<MemberAuthResponse>('/api/portal/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
  saveMemberTokens(response);
  return response;
}

function saveMemberTokens(response: MemberAuthResponse): void {
  localStorage.setItem(MEMBER_TOKEN_KEY, response.accessToken);
  if (response.refreshToken) {
    localStorage.setItem(MEMBER_REFRESH_TOKEN_KEY, response.refreshToken);
  }
}

export async function refreshPortalMember(refreshToken?: string): Promise<MemberAuthResponse> {
  const token = refreshToken || localStorage.getItem(MEMBER_REFRESH_TOKEN_KEY) || '';
  const response = await request<MemberAuthResponse>('/api/portal/auth/refresh', {
    method: 'POST',
    body: JSON.stringify({ refreshToken: token }),
  });
  saveMemberTokens(response);
  return response;
}

export async function logoutPortalMember(refreshToken?: string): Promise<void> {
  const token = refreshToken || localStorage.getItem(MEMBER_REFRESH_TOKEN_KEY);
  await request<void>('/api/portal/auth/logout', {
    method: 'POST',
    body: JSON.stringify(token ? { refreshToken: token } : {}),
  });
}

export function clearPortalMemberToken(): void {
  localStorage.removeItem(MEMBER_TOKEN_KEY);
  localStorage.removeItem(MEMBER_REFRESH_TOKEN_KEY);
}

export async function fetchPortalProductDetail(productId: string): Promise<Product | null> {
  const numericId = productId.replace(/^prod-/, '');
  const detail = await request<{
    product: CatalogProductRecord;
    features?: Array<{ featureText: string }>;
    specs?: Array<{ specName: string; specValue: string }>;
    skus?: CatalogSkuRecord[];
    media?: Array<{ mediaUrl?: string; isCover?: number }>;
  }>(`/api/portal/catalog/products/${numericId}`);
  if (!detail?.product) return null;
  const product = mapProduct(detail.product);
  const skus = (detail.skus || []).map(mapSku);
  if (skus.length > 0) {
    product.skus = skus;
    product.variants = buildSkuVariants(skus, product.price);
    if (skus.length === 1) {
      product.price = skus[0].price;
      product.originalPrice = skus[0].marketPrice;
      product.stock = skus[0].stock;
    }
  }
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

export interface PortalOrderItemRecord {
  id: number;
  orderId: number;
  productId?: number;
  skuId?: number;
  productName: string;
  skuName?: string;
  skuCode?: string;
  imageUrl?: string;
  unitPrice: number;
  quantity: number;
  itemAmount?: number;
}

export interface PortalOrderLogisticsRecord {
  id: number;
  orderId: number;
  trackingNo?: string;
  logisticsCompany?: string;
  logisticsStatus?: string;
  eventTime?: string;
  eventDescription?: string;
  eventLocation?: string;
  sortNo?: number;
}

export interface PortalOrderDetailRecord {
  order: PortalOrderRecord;
  items: PortalOrderItemRecord[];
  logistics: PortalOrderLogisticsRecord[];
}

export interface PortalPaymentOrderRecord {
  id: number;
  paymentNo: string;
  orderId: number;
  orderNo: string;
  channel: string;
  status: number;
  amount: number;
  transactionNo?: string;
  paidAt?: string;
  expireAt?: string;
  createdAt: string;
  updatedAt: string;
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

/**
 * 查询门户订单详情、商品明细及物流轨迹。
 *
 * @param orderId 订单ID
 * @return 订单聚合详情
 */
export async function fetchPortalOrderDetail(orderId: number): Promise<PortalOrderDetailRecord> {
  return request<PortalOrderDetailRecord>(`/api/portal/trade/orders/${orderId}`);
}

export interface PortalOrderCreatePayload {
  memberId: number;
  idempotencyKey?: string;
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

/**
 * 创建订单支付单。
 *
 * @param memberId 会员ID
 * @param orderId 订单ID
 * @param channel 支付渠道
 * @return 支付单
 */
export async function createPortalPayment(memberId: number, orderId: number, channel: string): Promise<PortalPaymentOrderRecord> {
  return request<PortalPaymentOrderRecord>(`/api/portal/payment/orders/${orderId}?memberId=${memberId}`, {
    method: 'POST',
    body: JSON.stringify({ channel }),
  });
}

/**
 * 查询支付单状态。
 *
 * @param memberId 会员ID
 * @param paymentNo 支付单号
 * @return 支付单
 */
export async function fetchPortalPayment(memberId: number, paymentNo: string): Promise<PortalPaymentOrderRecord> {
  return request<PortalPaymentOrderRecord>(`/api/portal/payment/orders/${encodeURIComponent(paymentNo)}?memberId=${memberId}`);
}

/**
 * 关闭未完成支付单。
 *
 * @param memberId 会员ID
 * @param paymentNo 支付单号
 */
export async function closePortalPayment(memberId: number, paymentNo: string): Promise<void> {
  return request<void>(`/api/portal/payment/orders/${encodeURIComponent(paymentNo)}/close?memberId=${memberId}`, {
    method: 'PUT',
  });
}

export interface PortalAfterSaleRecord {
  id: number;
  afterSaleNo: string;
  orderId: number;
  orderItemId?: number;
  afterSaleType: number;
  status: number;
  reason: string;
  refundAmount: number;
  createdAt: string;
  updatedAt: string;
}

export interface PortalAfterSaleCreatePayload {
  orderItemId?: number;
  afterSaleType: 1 | 2 | 3;
  refundAmount?: number;
  reason: string;
}

/**
 * 查询会员售后申请。
 *
 * @param memberId 会员ID
 * @return 售后申请列表
 */
export async function fetchPortalAfterSales(memberId: number): Promise<PortalAfterSaleRecord[]> {
  return request<PortalAfterSaleRecord[]>(`/api/portal/trade/after-sales?memberId=${memberId}`);
}

/**
 * 创建会员售后申请。
 *
 * @param memberId 会员ID
 * @param orderId 订单ID
 * @param payload 售后申请内容
 * @return 新建售后单
 */
export async function createPortalAfterSale(
  memberId: number,
  orderId: number,
  payload: PortalAfterSaleCreatePayload,
): Promise<PortalAfterSaleRecord> {
  return request<PortalAfterSaleRecord>(`/api/portal/trade/after-sales?memberId=${memberId}&orderId=${orderId}`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

/**
 * 取消会员待审核售后申请。
 *
 * @param memberId 会员ID
 * @param afterSaleId 售后单ID
 */
export async function cancelPortalAfterSale(memberId: number, afterSaleId: number): Promise<void> {
  return request<void>(`/api/portal/trade/after-sales/${afterSaleId}?memberId=${memberId}`, {
    method: 'DELETE',
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
