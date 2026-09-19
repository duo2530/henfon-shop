import { Coupon, Product, ProductSku, ProductVariant } from '../types/ecommerce';

// 在 Vite 浏览器环境读取环境变量；SSR/Node 冒烟测试中 import.meta.env 可能不存在。
const API_BASE_URL = ((import.meta.env?.VITE_API_BASE_URL as string | undefined) || 'http://localhost:8080').replace(/\/$/, '');
const MEMBER_TOKEN_KEY = 'henfon_shop_member_token';
const MEMBER_REFRESH_TOKEN_KEY = 'henfon_shop_member_refresh_token';
const MEMBER_SESSION_EXPIRED_EVENT = 'henfon:member-session-expired';
let memberRefreshPromise: Promise<MemberAuthResponse> | null = null;
let memberSessionExpiredNotified = false;

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
  /** 商品列表接口可选返回 SKU 摘要，用于门户端完整规格检索。 */
  skus?: CatalogSkuRecord[];
}

interface CatalogSkuRecord {
  id: number;
  skuCode: string;
  skuName: string;
  barcode?: string;
  attributesJson?: string | Record<string, string | number>;
  price: number;
  marketPrice?: number;
  stock?: number;
}

interface ProductPageResponse {
  records?: CatalogProductRecord[];
  total?: number;
  size?: number;
  current?: number;
  pages?: number;
}

/** 门户商品分页结果，记录已转换为前端商品模型。 */
export interface PortalProductPage {
  records: Product[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

/** 门户启用类目记录，层级与父子关系由后端下发，门户据此还原类目树。 */
export interface PortalCategoryRecord {
  id: number;
  parentId?: number;
  categoryName: string;
  categoryCode?: string;
  levelNo?: number;
  sortNo?: number;
  status?: number;
  iconUrl?: string;
}

/** 门户导航使用的类目节点，children 为空表示叶子类目。 */
export interface PortalCategoryNode {
  id: string;
  name: string;
  code: string;
  level: number;
  iconUrl?: string;
  children: PortalCategoryNode[];
}

/** 全部商品伪类目，与后端类目 ID 不会冲突。 */
export const ALL_CATEGORY_ID = 'all';

/**
 * 把后端扁平类目列表还原成类目树。
 * 后端按 parentId、sortNo 排好序，这里保持其顺序，父节点缺失的类目挂到第一层，避免整枝丢失。
 */
export function buildCategoryTree(records: PortalCategoryRecord[]): PortalCategoryNode[] {
  const nodes = new Map<string, PortalCategoryNode>();
  records.forEach((record) => {
    nodes.set(String(record.id), {
      id: String(record.id),
      name: record.categoryName,
      code: record.categoryCode || '',
      level: record.levelNo || 1,
      iconUrl: record.iconUrl,
      children: [],
    });
  });
  const roots: PortalCategoryNode[] = [];
  records.forEach((record) => {
    const node = nodes.get(String(record.id));
    if (!node) return;
    const parent = record.parentId ? nodes.get(String(record.parentId)) : undefined;
    if (parent) parent.children.push(node);
    else roots.push(node);
  });
  return roots;
}

/**
 * 在类目树中查找节点，并返回从一级到命中的完整路径。
 * 门户用这条路径做面包屑，也用它判断当前筛选落在第几级。
 */
export function findCategoryPath(
  tree: PortalCategoryNode[],
  predicate: (node: PortalCategoryNode) => boolean
): PortalCategoryNode[] {
  for (const node of tree) {
    if (predicate(node)) return [node];
    const childPath = findCategoryPath(node.children, predicate);
    if (childPath.length > 0) return [node, ...childPath];
  }
  return [];
}

interface MarketingCouponRecord {
  id: number;
  couponCode: string;
  couponTitle: string;
  discountAmount: number;
  minSpend: number;
  categoryCode?: string;
  tag?: string;
  description?: string;
  endAt: string;
}

/** 门户进行中秒杀活动及活动商品库存摘要。 */
export interface PortalFlashSaleRecord {
  id: number;
  activityCode: string;
  activityName: string;
  startAt: string;
  endAt: string;
  limitPerMember: number;
  items: PortalFlashSaleItemRecord[];
}

/** 门户秒杀商品的活动价格、限购和剩余库存。 */
export interface PortalFlashSaleItemRecord {
  id: number;
  productId: number;
  skuId?: number;
  productName: string;
  skuName?: string;
  imageUrl?: string;
  originalPrice?: number;
  attributesJson?: string;
  activityPrice: number;
  totalStock: number;
  soldStock: number;
  remainingStock: number;
  limitPerMember: number;
}

export interface PortalBanner {
  id: number;
  bannerTitle: string;
  bannerTag?: string;
  subtitle?: string;
  imageUrl: string;
  /** Banner 跳转类型：商品、类目、优惠券、外部链接或无跳转。 */
  linkType?: 'PRODUCT' | 'CATEGORY' | 'COUPON' | 'URL' | 'NONE' | string;
  linkTarget?: string;
}

export interface PortalReviewRecord {
  id: number;
  productId: number;
  memberId?: number;
  memberName: string;
  memberAvatarUrl?: string;
  rating: number;
  reviewContent: string;
  variantSummary?: string;
  imageUrls?: string | string[];
  helpfulCount: number;
  status: number;
  reviewedAt?: string;
  createdAt?: string;
  replyContent?: string;
  repliedAt?: string;
  repliedBy?: string;
  /** 会员追评内容，未追评时为空。 */
  followupContent?: string;
  followupAt?: string;
}

export interface PortalReviewPage {
  records: PortalReviewRecord[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

/** 商品评价统计，仅统计审核通过的评价。 */
export interface PortalReviewSummary {
  productId: number;
  reviewCount: number;
  avgRating: number;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const method = (options.method || 'GET').toUpperCase();
  const retryable = method === 'GET' || method === 'HEAD';
  // 写请求仅在首次访问返回401且刷新令牌成功时重试一次，避免重复提交业务数据。
  const maxAttempts = retryable ? 3 : 2;
  const authEndpoint = path.startsWith('/api/portal/auth/');
  let refreshed = false;
  let lastError: unknown;

  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    try {
      const token = localStorage.getItem(MEMBER_TOKEN_KEY);
      const isFormData = typeof FormData !== 'undefined' && options.body instanceof FormData;
      const response = await fetch(`${API_BASE_URL}${path}`, {
        ...options,
        headers: {
          ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          ...(options.headers || {}),
        },
      });
      if (response.status === 401) {
        const refreshToken = localStorage.getItem(MEMBER_REFRESH_TOKEN_KEY);
        if (!authEndpoint && !refreshed && refreshToken) {
          try {
            // 多个接口同时返回401时共享一次刷新请求，避免刷新令牌轮换导致互相注销。
            if (!memberRefreshPromise) {
              memberRefreshPromise = refreshPortalMember(refreshToken).finally(() => {
                memberRefreshPromise = null;
              });
            }
            await memberRefreshPromise;
            refreshed = true;
            continue;
          } catch (refreshError) {
            clearPortalMemberToken();
            notifyMemberSessionExpired();
            throw refreshError;
          }
        }
        if (!authEndpoint) {
          clearPortalMemberToken();
          notifyMemberSessionExpired();
        }
        throw new Error('会员登录已过期，请重新登录');
      }
      // 仅对幂等查询的 5xx 响应重试，写请求始终由调用方显式处理。
      if (response.status >= 500 && retryable && attempt < maxAttempts - 1) {
        await delay(300 * 2 ** attempt);
        continue;
      }
      // 非 2xx 响应同样解析统一响应体，向结算页保留后端可读的业务错误原因。
      const result = await response.json().catch(() => null) as ApiResponse<T> | null;
      if (!response.ok) {
        throw new Error(result?.message || `门户接口请求失败：${response.status}`);
      }
      if (!result) {
        throw new Error('门户接口返回数据为空');
      }
      if (result.code !== '0') {
        throw new Error(result.message || '门户接口返回失败');
      }
      return result.data;
    } catch (error) {
      lastError = error;
      const transientError = error instanceof TypeError;
      if (!retryable || !transientError || attempt >= maxAttempts - 1) break;
      await delay(300 * 2 ** attempt);
    }
  }

  if (!navigator.onLine) {
    throw new Error('当前网络不可用，请检查网络连接后重试');
  }
  throw lastError instanceof Error ? lastError : new Error('门户接口请求失败，请稍后重试');
}

function notifyMemberSessionExpired(): void {
  if (typeof window !== 'undefined' && !memberSessionExpiredNotified) {
    memberSessionExpiredNotified = true;
    window.dispatchEvent(new Event(MEMBER_SESSION_EXPIRED_EVENT));
  }
}

function delay(milliseconds: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds));
}

/**
 * 把后端类目名归一化为门户前端的类目 slug。
 * 仅在商品记录缺少 categoryId 时作为兜底：门户导航已改为按类目 ID 选中，
 * 类目名（尤其是三级叶子名）无法稳定映射回某一个 slug。
 */
export function mapCategory(categoryName?: string): Product['category'] {
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
  const mappedSkus = (record.skus || []).map(mapSku);
  const product: Product = {
    id: `prod-${record.id}`,
    title: record.productName,
    subtitle: record.shortDescription || '',
    category: record.categoryId != null ? String(record.categoryId) : mapCategory(record.categoryName),
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
  // 列表接口返回 SKU 时直接挂载，保证编码、条码和规格属性均可参与筛选。
  if (mappedSkus.length > 0) {
    product.skus = mappedSkus;
    product.variants = buildSkuVariants(mappedSkus, product.price);
    product.stock = mappedSkus.reduce((total, sku) => total + Math.max(0, sku.stock), 0);
  }
  return product;
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
    barcode: record.barcode,
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

export async function fetchPortalProducts(options: {
  keyword?: string;
  categoryId?: number;
  minPrice?: number;
  maxPrice?: number;
  skuKeyword?: string;
  sortBy?: 'featured' | 'sales' | 'price-asc' | 'price-desc' | 'newest';
  current?: number;
  size?: number;
} = {}): Promise<Product[]> {
  const page = await fetchPortalProductsPage(options);
  return page.records;
}

/**
 * 分页查询门户商品。
 *
 * 服务端负责关键词、类目、价格区间和排序，页面使用返回的 total/pages 渲染分页控件。
 *
 * @param options 商品查询条件和分页参数
 * @return 商品分页结果
 */
export async function fetchPortalProductsPage(options: {
  keyword?: string;
  categoryId?: number;
  minPrice?: number;
  maxPrice?: number;
  skuKeyword?: string;
  sortBy?: 'featured' | 'sales' | 'price-asc' | 'price-desc' | 'newest';
  current?: number;
  size?: number;
} = {}): Promise<PortalProductPage> {
  const params = new URLSearchParams();
  if (options.keyword) params.set('keyword', options.keyword);
  if (options.categoryId !== undefined) params.set('categoryId', String(options.categoryId));
  if (options.minPrice !== undefined) params.set('minPrice', String(options.minPrice));
  if (options.maxPrice !== undefined) params.set('maxPrice', String(options.maxPrice));
  if (options.skuKeyword) params.set('skuKeyword', options.skuKeyword);
  if (options.sortBy) params.set('sortBy', options.sortBy);
  if (options.current !== undefined) params.set('current', String(options.current));
  if (options.size !== undefined) params.set('size', String(options.size));
  const query = params.toString() ? `?${params.toString()}` : '';
  const page = await request<ProductPageResponse>(`/api/portal/catalog/products${query}`);
  const records = (page.records || []).map(mapProduct);
  const size = Number(page.size || options.size || 20);
  const current = Number(page.current || options.current || 1);
  const total = Number(page.total || records.length);
  return {
    records,
    total,
    size,
    current,
    pages: Number(page.pages || Math.ceil(total / Math.max(size, 1))),
  };
}

/** 查询门户启用类目。 */
export async function fetchPortalCategories(): Promise<PortalCategoryRecord[]> {
  return request<PortalCategoryRecord[]>('/api/portal/catalog/categories');
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

/** 申请通过绑定邮箱发送会员密码重置链接。 */
export async function requestPortalPasswordReset(email: string): Promise<void> {
  await request<void>('/api/portal/auth/password-reset/request', {
    method: 'POST',
    body: JSON.stringify({ email }),
  });
}

/** 使用邮箱链接中的一次性令牌设置新的会员登录密码。 */
export async function confirmPortalPasswordReset(token: string, newPassword: string): Promise<void> {
  await request<void>('/api/portal/auth/password-reset/confirm', {
    method: 'POST',
    body: JSON.stringify({ token, newPassword }),
  });
}

function saveMemberTokens(response: MemberAuthResponse): void {
  memberSessionExpiredNotified = false;
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

/**
 * 更新当前会员公开资料。
 *
 * @param payload 资料更新内容
 * @return 服务端保存后的会员认证资料
 * @author Henfon
 * @date 2026-09-04
 */
export async function updatePortalMemberProfile(payload: {
  nickname?: string;
  phone?: string;
  email?: string;
  avatarUrl?: string;
}): Promise<MemberAuthResponse> {
  const member = await request<{
    id: number;
    username: string;
    nickname: string;
    phone?: string;
    email?: string;
    avatarUrl?: string;
    memberLevel: string;
    points: number;
    balance: number;
  }>('/api/portal/member/profile', {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
  // 资料接口不刷新令牌，沿用当前令牌并返回统一的认证资料结构。
  return {
    accessToken: localStorage.getItem(MEMBER_TOKEN_KEY) || '',
    refreshToken: localStorage.getItem(MEMBER_REFRESH_TOKEN_KEY) || '',
    expiresInSeconds: 0,
    memberId: member.id,
    username: member.username,
    nickname: member.nickname,
    memberLevel: member.memberLevel,
    points: Number(member.points || 0),
    balance: Number(member.balance || 0),
    phone: member.phone,
    email: member.email,
    avatarUrl: member.avatarUrl,
  };
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

/** 判断门户当前是否存在会员访问令牌。 */
export function hasPortalMemberSession(): boolean {
  return Boolean(localStorage.getItem(MEMBER_TOKEN_KEY));
}

export async function fetchPortalProductDetail(productId: string): Promise<Product | null> {
  const numericId = productId.replace(/^prod-/, '');
  const detail = await request<{
    product: CatalogProductRecord;
    features?: Array<{ featureText: string }>;
    specs?: Array<{ specName: string; specValue: string }>;
    skus?: CatalogSkuRecord[];
    media?: Array<{ mediaUrl?: string; mediaType?: string; mediaTitle?: string; isCover?: number }>;
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
  // 视频单独取出来交给商品详情页的播放器（ProductDetailPage 只认 videoUrl 这个字段）。
  // 不能混进 images：图片位是按 <img> 渲染的，把 mp4 塞进去只会得到一个加载失败的破图。
  const videoMedia = (detail.media || []).find(
    (item) => (item.mediaType || '').toLowerCase() === 'video' && Boolean(item.mediaUrl)
  );
  if (videoMedia?.mediaUrl) {
    product.videoUrl = videoMedia.mediaUrl;
  }
  const mediaUrls = (detail.media || [])
    .filter((item) => (item.mediaType || '').toLowerCase() !== 'video')
    .map((item) => item.mediaUrl)
    .filter((url): url is string => Boolean(url));
  product.images = Array.from(new Set([...mediaUrls, ...product.images]));
  // 后端可能返回富文本 HTML，空值或异常内容统一回退为可读提示，避免详情区域出现空白。
  if (!product.description.trim()) {
    product.description = '该商品暂未提供详细介绍，您可以查看规格参数或咨询在线客服。';
  }
  return product;
}

export async function fetchPortalCoupons(): Promise<Coupon[]> {
  const records = await request<MarketingCouponRecord[]>('/api/portal/marketing/coupons');
  return (records || []).map((coupon) => ({
    id: coupon.id,
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

/** 查询当前时间窗口内仍有库存的门户秒杀活动。 */
export async function fetchPortalFlashSales(): Promise<PortalFlashSaleRecord[]> {
  return request<PortalFlashSaleRecord[]>('/api/portal/marketing/flash-sales');
}

/** 领取门户优惠券并返回会员优惠券记录。 */
export async function claimPortalCoupon(couponId: number): Promise<unknown> {
  return request(`/api/portal/marketing/coupons/${couponId}/claim`, { method: 'POST' });
}

/** 核销门户优惠券。 */
export async function redeemPortalCoupon(couponId: number, orderId: number): Promise<unknown> {
  return request('/api/portal/marketing/coupons/redeem', {
    method: 'POST',
    body: JSON.stringify({ couponId, orderId }),
  });
}

/** 回滚订单取消后的优惠券核销。 */
export async function rollbackPortalCoupon(orderId: number): Promise<unknown> {
  return request('/api/portal/marketing/coupons/rollback', {
    method: 'POST',
    body: JSON.stringify({ orderId }),
  });
}

export async function fetchPortalBanners(): Promise<PortalBanner[]> {
  return request<PortalBanner[]>('/api/portal/content/banners');
}

/** 查询门户商品评价分页数据。 */
export async function fetchPortalProductReviews(productId: string, current = 1, size = 10): Promise<PortalReviewPage> {
  const numericId = productId.replace(/^prod-/, '');
  return request<PortalReviewPage>(`/api/portal/content/products/${numericId}/reviews/page?current=${current}&size=${size}`);
}

/**
 * 批量查询商品评价统计（条数与平均分）。
 *
 * 商品列表一次展示多款商品，逐款查询评价会触发大量请求，这里一次拿回全部统计。
 */
export async function fetchPortalReviewSummary(productIds: number[]): Promise<PortalReviewSummary[]> {
  const ids = Array.from(new Set(productIds.filter((id) => Number.isFinite(id) && id > 0)));
  if (ids.length === 0) return [];
  return request<PortalReviewSummary[]>(`/api/portal/content/reviews/summary?productIds=${ids.join(',')}`);
}

/** 查询当前登录会员自己提交的评价，包含待审核与已隐藏的记录。 */
export async function fetchPortalMyReviews(current = 1, size = 10): Promise<PortalReviewPage> {
  return request<PortalReviewPage>(`/api/portal/content/reviews/mine?current=${current}&size=${size}`);
}

/** 对已审核通过的评价提交追评。 */
export async function submitPortalReviewFollowup(reviewId: number, content: string): Promise<PortalReviewRecord> {
  return request<PortalReviewRecord>(`/api/portal/content/reviews/${reviewId}/followup`, {
    method: 'POST',
    body: JSON.stringify({ content }),
  });
}

/**
 * 解析评价晒单图片字段。
 *
 * 后端历史数据里既有 JSON 数组字符串也有已经解析好的数组，这里统一成数组，避免逐处判断。
 */
export function parsePortalReviewImageUrls(value?: string | string[]): string[] {
  if (Array.isArray(value)) return value.filter(Boolean);
  if (!value) return [];
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed)
      ? parsed.filter((item): item is string => typeof item === 'string' && item.length > 0)
      : [];
  } catch {
    return value.split(',').map((item) => item.trim()).filter(Boolean);
  }
}

/** 提交门户商品评价，评价默认进入后台待审核状态。 */
export async function submitPortalProductReview(payload: {
  productId: number;
  rating: number;
  reviewContent: string;
  variantSummary?: string;
  imageUrls?: string[];
}): Promise<number> {
  return request<number>(`/api/portal/content/products/${payload.productId}/reviews`, {
    method: 'POST',
    body: JSON.stringify({
      rating: payload.rating,
      reviewContent: payload.reviewContent,
      variantSummary: payload.variantSummary,
      imageUrls: payload.imageUrls,
    }),
  });
}

/**
 * 上传门户会员评价图片。
 *
 * @param file 待上传图片
 * @return 文件对象及临时访问地址
 */
export async function uploadPortalMedia(file: File): Promise<{ objectKey: string; url: string; size: number; contentType: string }> {
  const formData = new FormData();
  formData.append('file', file);
  return request<{ objectKey: string; url: string; size: number; contentType: string }>('/api/portal/storage/upload', {
    method: 'POST',
    body: formData,
  });
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
  payableAmount: number;
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

export interface PortalInvoiceRecord {
  id: number;
  invoiceNo: string;
  orderId: number;
  orderNo: string;
  invoiceType: number;
  title: string;
  taxNo?: string;
  email?: string;
  amount: number;
  status: number;
  invoiceUrl?: string;
  failureReason?: string;
  requestedAt?: string;
  issuedAt?: string;
  createdAt?: string;
  updatedAt?: string;
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
  /** 微信 Native 支付二维码链接，仅创建支付单时返回。 */
  codeUrl?: string;
}

export async function fetchPortalCart(memberId: number): Promise<PortalCartRecord[]> {
  return request<PortalCartRecord[]>(`/api/portal/trade/cart?memberId=${memberId}`);
}

/** 主动清理当前会员购物车中的失效商品。 */
export async function cleanPortalInvalidCart(memberId: number): Promise<number> {
  return request<number>('/api/portal/trade/cart/clean-invalid', {
    method: 'POST',
  });
}

export async function addPortalCartItem(memberId: number, productId: number, quantity: number, skuId?: number) {
  return request<number>('/api/portal/trade/cart/items', {
    method: 'POST',
    body: JSON.stringify({ memberId, productId, skuId, quantity, selected: 1 }),
  });
}

/** 登录后一次性合并本地购物车，失效商品由服务端自动跳过。 */
export async function mergePortalCartItems(memberId: number, items: Array<{ productId: number; skuId?: number; quantity: number; selected?: number }>): Promise<number> {
  return request<number>('/api/portal/trade/cart/merge', {
    method: 'POST',
    body: JSON.stringify(items),
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

/**
 * 查询门户会员订单物流轨迹。
 *
 * @param orderId 订单ID
 * @return 物流轨迹列表
 */
export async function fetchPortalOrderLogistics(orderId: number): Promise<PortalOrderLogisticsRecord[]> {
  return request<PortalOrderLogisticsRecord[]>(`/api/portal/trade/orders/${orderId}/logistics`);
}

/** 查询会员订单发票申请状态。 */
export async function fetchPortalOrderInvoice(orderId: number): Promise<PortalInvoiceRecord | null> {
  return request<PortalInvoiceRecord | null>(`/api/portal/payment/invoices/orders/${orderId}`);
}

/** 为已支付订单提交发票申请，重复申请由服务端幂等返回原记录。 */
export async function applyPortalOrderInvoice(orderId: number, payload: {
  invoiceType: 1 | 2;
  title: string;
  taxNo?: string;
  email?: string;
}): Promise<PortalInvoiceRecord> {
  return request<PortalInvoiceRecord>(`/api/portal/payment/invoices/orders/${orderId}`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export interface PortalOrderCreatePayload {
  memberId: number;
  idempotencyKey?: string;
  /** 秒杀订单可选活动ID，普通订单不传。 */
  flashSaleId?: number;
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

/** 门户运费试算结果。 */
export interface PortalFreightQuote {
  templateId: number;
  templateName: string;
  carrierName: string;
  freightAmount: number;
  freeShippingThreshold: number;
  totalWeightGram: number;
  freeShipping: boolean;
  remoteArea: boolean;
}

/**
 * 根据购物车、收货地址和商品金额试算运费。
 *
 * @param payload 运费试算参数
 * @return 服务端运费结果
 */
export async function quotePortalFreight(payload: {
  items: Array<{ productId: number; skuId?: number; quantity: number }>;
  receiverProvince?: string;
  receiverCity?: string;
  receiverDistrict?: string;
  subtotalAmount: number;
  discountAmount: number;
}): Promise<PortalFreightQuote> {
  return request<PortalFreightQuote>('/api/portal/trade/freight/quote', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
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
  evidenceUrls?: string | string[];
}

export interface PortalAfterSaleCreatePayload {
  orderItemId?: number;
  afterSaleType: 1 | 2 | 3;
  refundAmount?: number;
  reason: string;
  evidenceUrls?: string[];
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

/** 查询当前会员的商品对比历史。 */
export async function fetchPortalCompareHistory(memberId: number): Promise<Array<{ id: number; comparedAt: string; productIds: number[] }>> {
  return request<Array<{ id: number; comparedAt: string; productIds: number[] }>>(`/api/portal/member/compare/history?memberId=${memberId}&limit=20`);
}

/** 保存当前会员的一次商品对比组合。 */
export async function savePortalCompareHistory(memberId: number, productIds: number[]): Promise<number> {
  return request<number>(`/api/portal/member/compare/history?memberId=${memberId}`, {
    method: 'POST',
    body: JSON.stringify(productIds),
  });
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
