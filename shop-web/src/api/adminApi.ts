export interface AdminUser {
  userId: number;
  tenantId: number;
  username: string;
  realName?: string;
  avatarUrl?: string;
  permissions: string[];
}

export interface BackendMenu {
  id: number;
  parentId: number;
  menuName: string;
  menuType: 'DIRECTORY' | 'MENU' | 'BUTTON';
  routePath?: string;
  component?: string;
  icon?: string;
  permissionCode?: string;
  sortNo: number;
  visible: number;
  status: number;
  children?: BackendMenu[];
}

export interface BackendPage<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

export interface BackendSystemUser {
  id: number;
  tenantId?: number;
  username: string;
  realName: string;
  nickname?: string;
  phone?: string;
  email?: string;
  avatarUrl?: string;
  deptId?: number;
  status: number;
  lastLoginAt?: string;
  lastLoginIp?: string;
  createdAt?: string;
  remark?: string;
}

export interface BackendMemberUser {
  id: number;
  memberNo: string;
  username: string;
  nickname: string;
  phone?: string;
  email?: string;
  avatarUrl?: string;
  memberLevel: string;
  points: number;
  balance: number;
  status: number;
  registeredAt?: string;
  lastLoginAt?: string;
  remark?: string;
  tagsCsv?: string;
  totalSpent?: number;
  orderCount?: number;
  lastOrderAt?: string;
}

export interface BackendMemberTag {
  id: number;
  tagName: string;
  sortNo: number;
  status: number;
  /** 该标签当前绑定的会员人数，由服务端统计，用于画像筛选栏展示。 */
  memberCount?: number;
}

export interface BackendSystemConfig {
  storeName: string;
  storeContactPhone: string;
  storeContactEmail: string;
  lowStockThreshold: number;
  autoNotifyEmail: boolean;
  autoTrackingSync: boolean;
  enableWechatPay: boolean;
  version?: number;
}

export interface BackendLogisticsCarrier {
  code: string;
  name: string;
  sortNo: number;
}

export interface BackendDepartment {
  id: number;
  parentId?: number;
  tenantId?: number;
  deptName: string;
  deptCode: string;
  leaderUserId?: number;
  sortNo: number;
  status: number;
}

export interface BackendRole {
  id: number;
  tenantId?: number;
  roleKey: string;
  roleName: string;
  roleSort: number;
  status: number;
  dataScope: string;
  description?: string;
  createdAt?: string;
}

export interface BackendDataRule {
  id: number;
  tenantId?: number;
  ruleName: string;
  moduleKey: string;
  scopeType: string;
  customDeptIds?: string;
  fieldMasks?: string;
  filterExpression?: string;
  status: number;
  updatedAt?: string;
}

export interface SaveRoleRequest {
  id?: number;
  tenantId?: number;
  roleKey: string;
  roleName: string;
  roleSort: number;
  status: number;
  dataScope: string;
  description?: string;
}

export interface SaveMenuRequest {
  id?: number;
  parentId?: number;
  menuName: string;
  menuType: 'DIRECTORY' | 'MENU' | 'BUTTON';
  routePath?: string;
  component?: string;
  icon?: string;
  permissionCode?: string;
  sortNo: number;
  visible: number;
  status: number;
}

export interface SaveDataRuleRequest {
  id?: number;
  tenantId?: number;
  ruleName: string;
  moduleKey: string;
  scopeType: string;
  customDeptIds?: string;
  fieldMasks?: string;
  filterExpression?: string;
  status: number;
}

export interface BackendCatalogProduct {
  id: number;
  categoryId?: number;
  categoryName?: string;
  productName: string;
  productCode: string;
  defaultSkuCode?: string;
  brandName?: string;
  shortDescription?: string;
  description?: string;
  price: number;
  marketPrice?: number;
  costPrice?: number;
  currentStock: number;
  safetyStock: number;
  salesCount: number;
  /** 默认计费重量（克），未设置时由运费服务按 1000 克兜底。 */
  weightGram?: number;
  mainImageUrl?: string;
  tagsCsv?: string;
  status: number;
  /** 审核状态：0 待审核、1 已通过、2 已驳回。 */
  auditStatus?: number;
  auditRemark?: string;
  createdAt?: string;
  updatedAt?: string;
  remark?: string;
  version?: number;
}

export interface BackendCatalogCategory {
  id: number;
  parentId?: number;
  categoryName: string;
  categoryCode: string;
  levelNo?: number;
  sortNo?: number;
  status: number;
  iconUrl?: string;
  remark?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface BackendCatalogSku {
  id: number;
  productId: number;
  skuCode: string;
  skuName: string;
  attributesJson?: string;
  price: number;
  marketPrice?: number;
  costPrice?: number;
  stock: number;
  safetyStock: number;
  status: number;
  createdAt?: string;
  updatedAt?: string;
  remark?: string;
}

export interface BackendCatalogProductFeature {
  id: number;
  productId: number;
  featureText: string;
  sortNo: number;
}

export interface BackendCatalogProductSpec {
  id: number;
  productId: number;
  specName: string;
  specValue: string;
  sortNo: number;
}

export interface BackendCatalogProductMedia {
  id: number;
  productId: number;
  skuId?: number;
  mediaType: 'IMAGE' | 'VIDEO' | string;
  objectKey: string;
  mediaUrl?: string;
  isCover: number;
  sortNo: number;
  remark?: string;
}

export interface BackendCatalogProductContent {
  features: BackendCatalogProductFeature[];
  specs: BackendCatalogProductSpec[];
  media: BackendCatalogProductMedia[];
}

export interface BackendContentBanner {
  id: number;
  bannerTitle: string;
  bannerTag?: string;
  subtitle?: string;
  /** 持久化的图片引用，新数据为 MinIO 对象键。 */
  imageUrl: string;
  /** 后端按当前配置重签的临时访问地址，仅用于展示，不回传保存。 */
  imageAccessUrl?: string;
  linkType: string;
  linkTarget?: string;
  sortNo: number;
  status: number;
  startAt?: string;
  endAt?: string;
  createdAt?: string;
  updatedAt?: string;
  remark?: string;
}

export interface BackendContentReview {
  id: number;
  productId: number;
  memberId?: number;
  memberName: string;
  memberAvatarUrl?: string;
  rating: number;
  reviewContent: string;
  variantSummary?: string;
  /** 评价图片地址 JSON 数组，后台审核页面用于晒单预览。 */
  imageUrls?: string;
  helpfulCount: number;
  /** 后端约定：0 待审核、1 已通过（门户展示）、2 审核未通过（门户隐藏）。 */
  status: 0 | 1 | 2;
  reviewedAt?: string;
  replyContent?: string;
  repliedAt?: string;
  repliedBy?: string;
  createdAt?: string;
}

export interface BackendMarketingCoupon {
  id: number;
  couponCode: string;
  couponTitle: string;
  discountAmount: number;
  minSpend: number;
  categoryCode?: string;
  tag?: string;
  description?: string;
  totalQuantity: number;
  perMemberLimit: number;
  claimedQuantity: number;
  /** 可选核销统计，后端未返回时前端展示为待同步。 */
  usedQuantity?: number;
  startAt: string;
  endAt: string;
  status: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface BackendMarketingFlashSale {
  id: number;
  activityCode: string;
  activityName: string;
  startAt: string;
  endAt: string;
  limitPerMember: number;
  status: number;
  createdAt?: string;
  updatedAt?: string;
  version?: number;
}

export interface BackendMarketingFlashSaleItem {
  id: number;
  activityId: number;
  productId: number;
  skuId?: number;
  activityPrice: number;
  totalStock: number;
  soldStock: number;
  limitPerMember: number;
  status: number;
  createdAt?: string;
  updatedAt?: string;
  version?: number;
}

/** 秒杀活动详情，库存与预占统计由服务端聚合返回。 */
export interface BackendMarketingFlashSaleDetail {
  id: number;
  activityCode: string;
  activityName: string;
  startAt: string;
  endAt: string;
  limitPerMember: number;
  status: number;
  statusText: string;
  createdAt?: string;
  updatedAt?: string;
  itemCount: number;
  totalStock: number;
  soldStock: number;
  remainingStock: number;
  sellThroughRate: number;
  reservedQuantity: number;
  releasedQuantity: number;
  participantCount: number;
  reservationCount: number;
  orderCount: number;
  earliestReservedAt?: string | null;
  latestReservedAt?: string | null;
}

/** 秒杀活动商品明细行，商品与规格名称由服务端补齐。 */
export interface BackendMarketingFlashSaleItemRow {
  id: number;
  productId: number;
  productCode?: string | null;
  productName?: string | null;
  skuId?: number | null;
  skuCode?: string | null;
  skuName?: string | null;
  activityPrice: number;
  originalPrice?: number | null;
  discountRate?: number | null;
  totalStock: number;
  soldStock: number;
  remainingStock: number;
  limitPerMember: number;
  status: number;
  statusText: string;
  createdAt?: string;
  updatedAt?: string;
}

/** 秒杀预占记录行，会员、订单与商品信息由服务端补齐。 */
export interface BackendMarketingFlashSaleReservationRow {
  id: number;
  createdAt: string;
  memberId: number;
  memberNo?: string | null;
  memberName?: string | null;
  memberPhone?: string | null;
  orderId: number;
  orderNo?: string | null;
  productId?: number | null;
  productName?: string | null;
  skuId?: number | null;
  skuName?: string | null;
  quantity: number;
  status: number;
  statusText: string;
  releasedAt?: string | null;
}

export interface BackendPaymentInvoice {
  id: number;
  invoiceNo: string;
  orderId: number;
  orderNo: string;
  memberId: number;
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
  remark?: string;
}

export interface BackendPaymentReconciliationRecord {
  id: string;
  transNo: string;
  orderNumber: string;
  type: 'order_income' | 'refund_payout' | 'commission_fee' | 'withdrawal';
  channel: 'wechat_pay' | 'alipay' | 'unionpay' | 'balance_pay';
  amount: number;
  fee: number;
  netAmount: number;
  status: 'reconciled' | 'pending_settle' | 'discrepancy';
  settledAt?: string;
  accountNumber?: string;
  notes?: string;
}

export interface BackendTradeOrder {
  id: number;
  orderNo: string;
  memberId?: number;
  memberName?: string;
  orderStatus: number;
  auditStatus?: number;
  auditRemark?: string;
  auditedAt?: string;
  auditedBy?: string;
  paymentStatus: number;
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
  sellerRemark?: string;
  flagColor?: 'red' | 'yellow' | 'green' | 'blue' | 'purple' | null;
  logisticsCompany?: string;
  trackingNo?: string;
  createdAt?: string;
  paidAt?: string;
  shippedAt?: string;
  completedAt?: string;
  version?: number;
  items?: BackendTradeOrderItem[];
}

export interface BackendTradeOrderItem {
  id: number;
  orderId: number;
  productId: number;
  skuId?: number;
  productName: string;
  skuName?: string;
  skuCode?: string;
  imageUrl?: string;
  unitPrice: number;
  quantity: number;
  itemAmount: number;
}

export interface BackendTradeFreightTemplate {
  id: number;
  templateName: string;
  carrierName?: string;
  baseWeightGram: number;
  baseFee: number;
  additionalWeightGram: number;
  additionalFee: number;
  freeShippingThreshold: number;
  remoteSurcharge: number;
  remoteRegionsCsv?: string;
  status: number;
  isDefault: number;
  version?: number;
}

export interface BackendTradeOrderLogistics {
  id: number;
  orderId: number;
  trackingNo: string;
  logisticsCompany: string;
  logisticsStatus?: string;
  eventTime: string;
  eventDescription: string;
  eventLocation?: string;
  sortNo: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface BackendInventoryStock {
  id: number;
  warehouseId: number;
  productId?: number;
  skuId: number;
  availableStock: number;
  lockedStock: number;
  soldStock: number;
  safetyStock: number;
  updatedAt?: string;
  remark?: string;
}

export interface BackendInventoryStockLock {
  id: number;
  lockNo: string;
  orderId: number;
  orderNo: string;
  stockId: number;
  skuId: number;
  quantity: number;
  status: number;
  expireAt?: string;
  releasedAt?: string;
  deductedAt?: string;
  createdAt?: string;
  updatedAt?: string;
  remark?: string;
}

export interface BackendInventoryWarehouse {
  id: number;
  warehouseCode: string;
  warehouseName: string;
  status: number;
  isDefault: number;
  createdAt?: string;
  updatedAt?: string;
  remark?: string;
  version?: number;
}

export interface BackendInventorySupplier {
  id: number;
  supplierCode: string;
  supplierName: string;
  contactName?: string;
  contactPhone?: string;
  address?: string;
  status: number;
  createdAt?: string;
  updatedAt?: string;
  remark?: string;
  version?: number;
}

export interface BackendStorageUploadResult {
  objectKey: string;
  url: string;
  size: number;
  contentType: string;
}

export interface BackendTradeAfterSale {
  id: number;
  afterSaleNo: string;
  orderId: number;
  orderItemId?: number;
  memberId?: number;
  afterSaleType: number;
  status: number;
  reason: string;
  refundAmount: number;
  createdAt?: string;
  updatedAt?: string;
  remark?: string;
}

export interface BackendReportingDashboardMetrics {
  date: string;
  todayOrderCount: number;
  todaySalesAmount: number;
  todayProductCount: number;
  todayMemberCount: number;
  totalOrderCount: number;
  totalSalesAmount: number;
  totalProductCount: number;
  totalMemberCount: number;
}

export interface BackendReportingSalesTrendPoint {
  date: string;
  salesAmount: number;
  orderCount: number;
  productQuantity: number;
}

export interface BackendReportingProductRankingItem {
  rank: number;
  productId?: number;
  productName: string;
  categoryName?: string;
  salesVolume: number;
  salesAmount: number;
  orderCount: number;
}

export interface BackendReportingMemberLevelStat {
  memberLevel: string;
  memberCount: number;
  activeMemberCount: number;
  paidOrderCount: number;
  paidAmount: number;
}

export interface BackendReportingMemberAnalysis {
  startDate: string;
  endDate: string;
  totalMemberCount: number;
  newMemberCount: number;
  activeMemberCount: number;
  repeatPurchaseMemberCount: number;
  repurchaseRate: number;
  paidOrderCount: number;
  paidAmount: number;
  averageOrderAmount: number;
  levelStats: BackendReportingMemberLevelStat[];
}

export interface BackendReportingChannelStat {
  channel: string;
  paymentOrderCount: number;
  paidAmount: number;
}

export interface BackendLoginLog {
  id: number;
  userId?: number;
  username: string;
  loginStatus: number;
  loginIp?: string;
  userAgent?: string;
  failureReason?: string;
  loginAt?: string;
}

export interface BackendOperationLog {
  id: number;
  traceId?: string;
  userId?: number;
  username?: string;
  moduleKey?: string;
  operation?: string;
  requestMethod?: string;
  requestUri?: string;
  requestParams?: string;
  responseStatus?: number;
  clientIp?: string;
  durationMs?: number;
  createdAt?: string;
}

interface ApiEnvelope<T> {
  code: string;
  message: string;
  data: T;
}

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080').replace(/\/$/, '');
const TOKEN_KEY = 'henfon_shop_admin_token';
const REFRESH_TOKEN_KEY = 'henfon_shop_admin_refresh_token';
let refreshPromise: Promise<string | null> | null = null;

export const getAdminToken = (): string | null => localStorage.getItem(TOKEN_KEY);
export const getAdminRefreshToken = (): string | null => localStorage.getItem(REFRESH_TOKEN_KEY);

export const clearAdminToken = (): void => {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
};

/** 使用刷新令牌换取新的管理员访问令牌，并在成功后轮换本地令牌。 */
export async function refreshAdminToken(): Promise<string | null> {
  if (refreshPromise) return refreshPromise;
  const refreshToken = getAdminRefreshToken();
  if (!refreshToken) return null;
  refreshPromise = (async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/admin/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });
      const body = await response.json().catch(() => null) as ApiEnvelope<{
        accessToken: string;
        refreshToken: string;
      }> | null;
      if (!response.ok || !body || body.code !== '0' || !body.data?.accessToken || !body.data.refreshToken) {
        clearAdminToken();
        return null;
      }
      localStorage.setItem(TOKEN_KEY, body.data.accessToken);
      localStorage.setItem(REFRESH_TOKEN_KEY, body.data.refreshToken);
      return body.data.accessToken;
    } catch {
      clearAdminToken();
      return null;
    } finally {
      refreshPromise = null;
    }
  })();
  return refreshPromise;
}

async function request<T>(path: string, options: RequestInit = {}, token?: string, allowRefresh = true): Promise<T> {
  const method = (options.method || 'GET').toUpperCase();
  const retryable = method === 'GET' || method === 'HEAD';
  const maxAttempts = retryable ? 3 : 1;
  let lastError: unknown;
  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    try {
      const headers = new Headers(options.headers);
      headers.set('Content-Type', 'application/json');
      const accessToken = token || getAdminToken();
      if (accessToken) {
        headers.set('Authorization', `Bearer ${accessToken}`);
      }
      const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers });
      if (response.status === 401 && allowRefresh && !path.startsWith('/api/admin/auth/')) {
        const refreshedToken = await refreshAdminToken();
        if (refreshedToken) {
          // 仅重放一次原请求，避免刷新令牌失效时递归请求形成死循环。
          return request<T>(path, options, refreshedToken, false);
        }
      }
      if (response.status >= 500 && retryable && attempt < maxAttempts - 1) {
        await delay(300 * 2 ** attempt);
        continue;
      }
      const body = await response.json().catch(() => null) as ApiEnvelope<T> | null;
      if (!response.ok || !body || body.code !== '0') {
        throw new Error(body?.message || `请求失败（${response.status}）`);
      }
      return body.data;
    } catch (error) {
      lastError = error;
      if (!retryable || !(error instanceof TypeError) || attempt >= maxAttempts - 1) break;
      await delay(300 * 2 ** attempt);
    }
  }
  if (!navigator.onLine) {
    throw new Error('当前网络不可用，请检查网络连接后重试');
  }
  throw lastError instanceof Error ? lastError : new Error('请求失败，请稍后重试');
}

function delay(milliseconds: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds));
}

export function listLoginLogs(params: { current?: number; size?: number; username?: string; status?: number } = {}): Promise<BackendPage<BackendLoginLog>> {
  const query = new URLSearchParams({ current: String(params.current || 1), size: String(params.size || 20) });
  if (params.username) query.set('username', params.username);
  if (params.status !== undefined) query.set('status', String(params.status));
  return request<BackendPage<BackendLoginLog>>(`/api/admin/audit/login-logs?${query.toString()}`);
}

export function listOperationLogs(params: { current?: number; size?: number; username?: string; moduleKey?: string } = {}): Promise<BackendPage<BackendOperationLog>> {
  const query = new URLSearchParams({ current: String(params.current || 1), size: String(params.size || 20) });
  if (params.username) query.set('username', params.username);
  if (params.moduleKey) query.set('moduleKey', params.moduleKey);
  return request<BackendPage<BackendOperationLog>>(`/api/admin/audit/operation-logs?${query.toString()}`);
}

export async function listSystemUsers(params: { current?: number; size?: number; keyword?: string } = {}): Promise<BackendPage<BackendSystemUser>> {
  const query = new URLSearchParams({ current: String(params.current || 1), size: String(params.size || 100) });
  if (params.keyword) query.set('keyword', params.keyword);
  return request<BackendPage<BackendSystemUser>>(`/api/admin/system/users?${query.toString()}`);
}

export async function listMemberUsers(params: { current?: number; size?: number; keyword?: string; memberLevel?: string; status?: number } = {}): Promise<BackendPage<BackendMemberUser>> {
  const query = new URLSearchParams({ current: String(params.current || 1), size: String(params.size || 100) });
  if (params.keyword) query.set('keyword', params.keyword);
  if (params.memberLevel) query.set('memberLevel', params.memberLevel);
  if (params.status !== undefined) query.set('status', String(params.status));
  return request<BackendPage<BackendMemberUser>>(`/api/admin/member/users?${query.toString()}`);
}

export function createMemberUser(payload: {
  nickname: string;
  username?: string;
  phone?: string;
  email?: string;
  memberLevel?: string;
  status?: number;
  avatarUrl?: string;
  remark?: string;
}): Promise<BackendMemberUser> {
  return request<BackendMemberUser>('/api/admin/member/users', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function updateMemberStatus(id: number, status: number): Promise<void> {
  return request<void>(`/api/admin/member/users/${id}/status?status=${status}`, { method: 'PUT' });
}

export function updateMemberProfile(id: number, payload: {
  nickname?: string;
  phone?: string;
  email?: string;
  memberLevel?: string;
  avatarUrl?: string;
  remark?: string;
}): Promise<BackendMemberUser> {
  return request<BackendMemberUser>(`/api/admin/member/users/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export function adjustMemberAssets(id: number, pointsDelta: number, balanceDelta: number, remark?: string): Promise<BackendMemberUser> {
  return request<BackendMemberUser>(`/api/admin/member/users/${id}/assets`, {
    method: 'PUT',
    body: JSON.stringify({ pointsDelta, balanceDelta, remark }),
  });
}

export function listMemberTags(): Promise<BackendMemberTag[]> {
  return request<BackendMemberTag[]>('/api/admin/member/tags');
}

export function updateMemberTags(id: number, tags: string[]): Promise<BackendMemberUser> {
  return request<BackendMemberUser>(`/api/admin/member/users/${id}/tags`, {
    method: 'PUT',
    body: JSON.stringify({ tags }),
  });
}

export function listDepartments(): Promise<BackendDepartment[]> {
  return request<BackendDepartment[]>('/api/admin/system/depts');
}

export function listSystemRoles(): Promise<BackendRole[]> {
  return request<BackendRole[]>('/api/admin/system/roles');
}

export function listSystemMenus(): Promise<BackendMenu[]> {
  return request<BackendMenu[]>('/api/admin/system/menus');
}

export function listDataRules(): Promise<BackendDataRule[]> {
  return request<BackendDataRule[]>('/api/admin/system/data-rules');
}

export function getSystemConfig(): Promise<BackendSystemConfig> {
  return request<BackendSystemConfig>('/api/admin/system/config');
}

export function saveSystemConfig(config: Omit<BackendSystemConfig, 'version'> & { version?: number }): Promise<BackendSystemConfig> {
  return request<BackendSystemConfig>('/api/admin/system/config', {
    method: 'PUT',
    body: JSON.stringify(config),
  });
}

export function resetSystemConfig(version?: number): Promise<BackendSystemConfig> {
  const query = version === undefined ? '' : `?version=${encodeURIComponent(String(version))}`;
  return request<BackendSystemConfig>(`/api/admin/system/config/reset${query}`, { method: 'POST' });
}

export function listLogisticsCarriers(): Promise<BackendLogisticsCarrier[]> {
  return request<BackendLogisticsCarrier[]>('/api/admin/system/dictionaries/logistics-carriers');
}

export function listUserRoleIds(userId: number): Promise<number[]> {
  return request<number[]>(`/api/admin/system/users/${userId}/roles`);
}

export function listRoleMenuIds(roleId: number): Promise<number[]> {
  return request<number[]>(`/api/admin/system/roles/${roleId}/menus`);
}

export function listRoleDataRuleIds(roleId: number): Promise<number[]> {
  return request<number[]>(`/api/admin/system/roles/${roleId}/data-rules`);
}

export function replaceRoleMenus(roleId: number, menuIds: number[]): Promise<void> {
  return request<void>(`/api/admin/system/roles/${roleId}/menus`, {
    method: 'PUT',
    body: JSON.stringify(menuIds)
  });
}

export function replaceUserRoles(userId: number, roleIds: number[]): Promise<void> {
  return request<void>(`/api/admin/system/users/${userId}/roles`, {
    method: 'PUT',
    body: JSON.stringify(roleIds)
  });
}

export function saveSystemRole(role: SaveRoleRequest): Promise<number> {
  return request<number>('/api/admin/system/roles', { method: 'POST', body: JSON.stringify(role) });
}

export function deleteSystemRole(id: number): Promise<void> {
  return request<void>(`/api/admin/system/roles/${id}`, { method: 'DELETE' });
}

export function saveSystemMenu(menu: SaveMenuRequest): Promise<number> {
  return request<number>('/api/admin/system/menus', { method: 'POST', body: JSON.stringify(menu) });
}

export function deleteSystemMenu(id: number): Promise<void> {
  return request<void>(`/api/admin/system/menus/${id}`, { method: 'DELETE' });
}

export function saveSystemDataRule(rule: SaveDataRuleRequest): Promise<number> {
  return request<number>('/api/admin/system/data-rules', { method: 'POST', body: JSON.stringify(rule) });
}

export function deleteSystemDataRule(id: number): Promise<void> {
  return request<void>(`/api/admin/system/data-rules/${id}`, { method: 'DELETE' });
}

export function listCatalogProducts(params: { current?: number; size?: number; keyword?: string; status?: number; categoryId?: number } = {}): Promise<BackendPage<BackendCatalogProduct>> {
  const query = new URLSearchParams({ current: String(params.current || 1), size: String(params.size || 200) });
  if (params.keyword) query.set('keyword', params.keyword);
  if (params.status !== undefined) query.set('status', String(params.status));
  if (params.categoryId !== undefined) query.set('categoryId', String(params.categoryId));
  return request<BackendPage<BackendCatalogProduct>>(`/api/admin/catalog/products?${query.toString()}`);
}

/** 按商品ID批量查询，用于活动编辑时回显已选商品信息。 */
export function listCatalogProductsByIds(ids: number[]): Promise<BackendCatalogProduct[]> {
  const safeIds = Array.from(new Set(ids.filter((id) => Number.isFinite(id) && id > 0)));
  if (safeIds.length === 0) return Promise.resolve([]);
  return request<BackendCatalogProduct[]>(`/api/admin/catalog/products/batch?ids=${safeIds.join(',')}`);
}

export function listCatalogCategories(): Promise<BackendCatalogCategory[]> {
  return request<BackendCatalogCategory[]>('/api/admin/catalog/categories');
}

export function listManageCatalogCategories(): Promise<BackendCatalogCategory[]> {
  return request<BackendCatalogCategory[]>('/api/admin/catalog/categories/manage');
}

export function saveCatalogCategory(payload: {
  id?: number;
  parentId?: number;
  categoryName: string;
  categoryCode: string;
  sortNo?: number;
  status: number;
  iconUrl?: string;
  remark?: string;
}): Promise<number> {
  return request<number>('/api/admin/catalog/categories', { method: 'POST', body: JSON.stringify(payload) });
}

export function updateCatalogCategoryStatus(id: number, status: number): Promise<void> {
  return request<void>(`/api/admin/catalog/categories/${id}/status?status=${status}`, { method: 'PUT' });
}

export function deleteCatalogCategory(id: number): Promise<void> {
  return request<void>(`/api/admin/catalog/categories/${id}`, { method: 'DELETE' });
}

export function saveCatalogProduct(product: {
  id?: number;
  categoryId?: number;
  categoryName?: string;
  productName: string;
  productCode: string;
  defaultSkuCode?: string;
  description?: string;
  price: number;
  marketPrice?: number;
  costPrice?: number;
  currentStock?: number;
  safetyStock?: number;
  mainImageUrl?: string;
  tagsCsv?: string;
  status?: number;
}): Promise<number> {
  return request<number>('/api/admin/catalog/products', { method: 'POST', body: JSON.stringify(product) });
}

export function deleteCatalogProduct(id: number): Promise<void> {
  return request<void>(`/api/admin/catalog/products/${id}`, { method: 'DELETE' });
}

export function updateCatalogProductStatus(id: number, status: number): Promise<void> {
  return request<void>(`/api/admin/catalog/products/${id}/status?status=${status}`, { method: 'PUT' });
}

export function listCatalogSkus(productId: number): Promise<BackendCatalogSku[]> {
  return request<BackendCatalogSku[]>(`/api/admin/catalog/products/${productId}/skus`);
}

export function saveCatalogSku(productId: number, sku: {
  id?: number;
  skuCode: string;
  skuName: string;
  attributesJson?: string;
  price: number;
  marketPrice?: number;
  costPrice?: number;
  stock: number;
  safetyStock: number;
  status: number;
  remark?: string;
}): Promise<number> {
  return request<number>(`/api/admin/catalog/products/${productId}/skus`, { method: 'POST', body: JSON.stringify(sku) });
}

export function updateCatalogSkuStatus(productId: number, skuId: number, status: number): Promise<void> {
  return request<void>(`/api/admin/catalog/products/${productId}/skus/${skuId}/status?status=${status}`, { method: 'PUT' });
}

export function deleteCatalogSku(productId: number, skuId: number): Promise<void> {
  return request<void>(`/api/admin/catalog/products/${productId}/skus/${skuId}`, { method: 'DELETE' });
}

export function getCatalogProductContent(productId: number): Promise<BackendCatalogProductContent> {
  return request<BackendCatalogProductContent>(`/api/admin/catalog/products/${productId}/content`);
}

export function saveCatalogProductContent(productId: number, content: {
  features: Array<{ featureText: string; sortNo?: number }>;
  specs: Array<{ specName: string; specValue: string; sortNo?: number }>;
  media: Array<{ skuId?: number; mediaType: 'IMAGE' | 'VIDEO'; objectKey: string; mediaUrl?: string; isCover: number; sortNo?: number; remark?: string }>;
}): Promise<BackendCatalogProductContent> {
  return request<BackendCatalogProductContent>(`/api/admin/catalog/products/${productId}/content`, {
    method: 'PUT',
    body: JSON.stringify(content),
  });
}

export function listInventoryWarnings(params: { warehouseId?: number; skuId?: number } = {}): Promise<BackendInventoryStock[]> {
  const query = new URLSearchParams();
  if (params.warehouseId !== undefined) query.set('warehouseId', String(params.warehouseId));
  if (params.skuId !== undefined) query.set('skuId', String(params.skuId));
  const suffix = query.toString() ? `?${query.toString()}` : '';
  return request<BackendInventoryStock[]>(`/api/admin/inventory/stocks/warnings${suffix}`);
}

export function listInventoryWarehouses(params: { current?: number; size?: number; keyword?: string; status?: number } = {}): Promise<BackendPage<BackendInventoryWarehouse>> {
  const query = new URLSearchParams({ current: String(params.current || 1), size: String(params.size || 200) });
  if (params.keyword) query.set('keyword', params.keyword);
  if (params.status !== undefined) query.set('status', String(params.status));
  return request<BackendPage<BackendInventoryWarehouse>>(`/api/admin/inventory/warehouses?${query.toString()}`);
}

export function listEnabledInventoryWarehouses(): Promise<BackendInventoryWarehouse[]> {
  return request<BackendInventoryWarehouse[]>('/api/admin/inventory/warehouses/enabled');
}

export function saveInventoryWarehouse(payload: {
  id?: number;
  warehouseCode: string;
  warehouseName: string;
  status: number;
  isDefault: number;
  remark?: string;
  version?: number;
}): Promise<number> {
  return request<number>('/api/admin/inventory/warehouses', { method: 'POST', body: JSON.stringify(payload) });
}

export function updateInventoryWarehouseStatus(id: number, status: number): Promise<void> {
  return request<void>(`/api/admin/inventory/warehouses/${id}/status?status=${status}`, { method: 'PUT' });
}

export function deleteInventoryWarehouse(id: number): Promise<void> {
  return request<void>(`/api/admin/inventory/warehouses/${id}`, { method: 'DELETE' });
}

export function listInventorySuppliers(params: { current?: number; size?: number; keyword?: string; status?: number } = {}): Promise<BackendPage<BackendInventorySupplier>> {
  const query = new URLSearchParams({ current: String(params.current || 1), size: String(params.size || 200) });
  if (params.keyword) query.set('keyword', params.keyword);
  if (params.status !== undefined) query.set('status', String(params.status));
  return request<BackendPage<BackendInventorySupplier>>(`/api/admin/inventory/suppliers?${query.toString()}`);
}

export function saveInventorySupplier(payload: {
  id?: number;
  supplierCode: string;
  supplierName: string;
  contactName?: string;
  contactPhone?: string;
  address?: string;
  status: number;
  remark?: string;
  version?: number;
}): Promise<number> {
  return request<number>('/api/admin/inventory/suppliers', { method: 'POST', body: JSON.stringify(payload) });
}

export function updateInventorySupplierStatus(id: number, status: number): Promise<void> {
  return request<void>(`/api/admin/inventory/suppliers/${id}/status?status=${status}`, { method: 'PUT' });
}

export function deleteInventorySupplier(id: number): Promise<void> {
  return request<void>(`/api/admin/inventory/suppliers/${id}`, { method: 'DELETE' });
}

export async function uploadStorageFile(file: File): Promise<BackendStorageUploadResult> {
  const maxFileSize = 10 * 1024 * 1024;
  const allowedContentTypes = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'video/mp4', 'application/pdf']);
  if (!file || file.size === 0) {
    throw new Error('上传文件不能为空');
  }
  if (file.size > maxFileSize) {
    throw new Error('文件大小不能超过10MB');
  }
  if (!allowedContentTypes.has(file.type.toLowerCase())) {
    throw new Error('仅支持 JPG、PNG、WEBP、GIF、MP4 和 PDF 文件');
  }

  const formData = new FormData();
  formData.append('file', file);
  const headers = new Headers();
  const accessToken = getAdminToken();
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`);
  let lastError: unknown;
  for (let attempt = 0; attempt < 3; attempt += 1) {
    try {
      const response = await fetch(`${API_BASE_URL}/api/admin/storage/upload`, { method: 'POST', headers, body: formData });
      if (response.status >= 500 && attempt < 2) {
        await delay(300 * 2 ** attempt);
        continue;
      }
      const body = await response.json().catch(() => null) as ApiEnvelope<BackendStorageUploadResult> | null;
      if (!response.ok || !body || body.code !== '0') {
        throw new Error(body?.message || `上传失败（${response.status}）`);
      }
      return body.data;
    } catch (error) {
      lastError = error;
      if (!(error instanceof TypeError) || attempt >= 2) break;
      await delay(300 * 2 ** attempt);
    }
  }
  throw lastError instanceof Error ? lastError : new Error('上传失败，请稍后重试');
}

export function deleteStorageFile(objectKey: string): Promise<void> {
  return request<void>(`/api/admin/storage?objectKey=${encodeURIComponent(objectKey)}`, { method: 'DELETE' });
}

/** 后台通知中心列表项：平台发给会员的站内通知投递记录。 */
export interface BackendContentNotification {
  id: number;
  memberId?: number;
  memberName: string;
  memberAccount?: string;
  orderId?: number;
  businessId?: string;
  eventType: string;
  title: string;
  content: string;
  /** 会员本人的已读状态：0 未读、1 已读。 */
  readStatus: 0 | 1;
  readAt?: string;
  /** 运营的已读状态，与会员已读相互独立，标记它不会改变会员端的未读提示。 */
  adminReadStatus: 0 | 1;
  adminReadAt?: string;
  createdAt?: string;
}

/** 后台通知中心统计概览。 */
export interface BackendContentNotificationSummary {
  total: number;
  adminUnread: number;
  memberUnread: number;
  eventTypes: Array<{ eventType: string; count: number }>;
}

export function listContentReviews(params: { current?: number; size?: number; productId?: number; status?: number; keyword?: string } = {}): Promise<BackendPage<BackendContentReview>> {
  const query = new URLSearchParams({ current: String(params.current || 1), size: String(params.size || 100) });
  if (params.productId !== undefined) query.set('productId', String(params.productId));
  if (params.status !== undefined) query.set('status', String(params.status));
  if (params.keyword) query.set('keyword', params.keyword);
  return request<BackendPage<BackendContentReview>>(`/api/admin/content/reviews?${query.toString()}`);
}

export function updateContentReviewStatus(id: number, status: 1 | 2): Promise<void> {
  return request<void>(`/api/admin/content/reviews/${id}/status?status=${status}`, { method: 'PUT' });
}

/** 待审核评价数量，后台据此提示审核待办。 */
export function countPendingContentReviews(): Promise<number> {
  return request<number>('/api/admin/content/reviews/pending-count');
}

export function replyContentReview(id: number, replyContent: string): Promise<void> {
  return request<void>(`/api/admin/content/reviews/${id}/reply`, {
    method: 'PUT',
    body: JSON.stringify({ replyContent }),
  });
}

export function listContentNotifications(params: {
  current?: number;
  size?: number;
  eventType?: string;
  adminReadStatus?: 0 | 1;
  memberReadStatus?: 0 | 1;
  keyword?: string;
  memberId?: number;
} = {}): Promise<BackendPage<BackendContentNotification>> {
  const query = new URLSearchParams({ current: String(params.current || 1), size: String(params.size || 20) });
  if (params.eventType) query.set('eventType', params.eventType);
  if (params.adminReadStatus !== undefined) query.set('adminReadStatus', String(params.adminReadStatus));
  if (params.memberReadStatus !== undefined) query.set('memberReadStatus', String(params.memberReadStatus));
  if (params.keyword) query.set('keyword', params.keyword);
  if (params.memberId !== undefined) query.set('memberId', String(params.memberId));
  return request<BackendPage<BackendContentNotification>>(`/api/admin/content/notifications?${query.toString()}`);
}

export function getContentNotificationSummary(): Promise<BackendContentNotificationSummary> {
  return request<BackendContentNotificationSummary>('/api/admin/content/notifications/summary');
}

export function markContentNotificationRead(id: number): Promise<void> {
  return request<void>(`/api/admin/content/notifications/${id}/read`, { method: 'PUT' });
}

/** 把全部运营未读通知标记为已读，返回本次更新条数。 */
export function markAllContentNotificationsRead(): Promise<number> {
  return request<number>('/api/admin/content/notifications/read-all', { method: 'PUT' });
}

export function listContentBanners(params: { current?: number; size?: number; keyword?: string; status?: number } = {}): Promise<BackendPage<BackendContentBanner>> {
  const query = new URLSearchParams({ current: String(params.current || 1), size: String(params.size || 200) });
  if (params.keyword) query.set('keyword', params.keyword);
  if (params.status !== undefined) query.set('status', String(params.status));
  return request<BackendPage<BackendContentBanner>>(`/api/admin/content/banners?${query.toString()}`);
}

export function saveContentBanner(banner: {
  id?: number;
  bannerTitle: string;
  bannerTag?: string;
  subtitle?: string;
  imageUrl: string;
  linkType?: string;
  linkTarget?: string;
  sortNo?: number;
  status?: number;
  startAt?: string;
  endAt?: string;
  remark?: string;
}): Promise<number> {
  return request<number>('/api/admin/content/banners', { method: 'POST', body: JSON.stringify(banner) });
}

export function updateContentBannerStatus(id: number, status: number): Promise<void> {
  return request<void>(`/api/admin/content/banners/${id}/status?status=${status}`, { method: 'PUT' });
}

export function deleteContentBanner(id: number): Promise<void> {
  return request<void>(`/api/admin/content/banners/${id}`, { method: 'DELETE' });
}

export function listMarketingCoupons(params: { current?: number; size?: number; keyword?: string; status?: number } = {}): Promise<BackendPage<BackendMarketingCoupon>> {
  const query = new URLSearchParams({ current: String(params.current || 1), size: String(params.size || 200) });
  if (params.keyword) query.set('keyword', params.keyword);
  if (params.status !== undefined) query.set('status', String(params.status));
  return request<BackendPage<BackendMarketingCoupon>>(`/api/admin/marketing/coupons?${query.toString()}`);
}

export function listPaymentInvoices(params: {
  current?: number;
  size?: number;
  keyword?: string;
  status?: number;
  orderId?: number;
  memberId?: number;
} = {}): Promise<BackendPage<BackendPaymentInvoice>> {
  const query = new URLSearchParams({ current: String(params.current || 1), size: String(params.size || 200) });
  if (params.keyword) query.set('keyword', params.keyword);
  if (params.status !== undefined) query.set('status', String(params.status));
  if (params.orderId !== undefined) query.set('orderId', String(params.orderId));
  if (params.memberId !== undefined) query.set('memberId', String(params.memberId));
  return request<BackendPage<BackendPaymentInvoice>>(`/api/admin/payment/invoices?${query.toString()}`);
}

export function updatePaymentInvoiceStatus(invoiceNo: string, payload: {
  status: number;
  invoiceUrl?: string;
  failureReason?: string;
}): Promise<BackendPaymentInvoice> {
  return request<BackendPaymentInvoice>(`/api/admin/payment/invoices/${encodeURIComponent(invoiceNo)}/status`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export function listPaymentReconciliation(params: {
  current?: number;
  size?: number;
  keyword?: string;
  type?: string;
  status?: string;
} = {}): Promise<BackendPage<BackendPaymentReconciliationRecord>> {
  const query = new URLSearchParams({ current: String(params.current || 1), size: String(params.size || 20) });
  if (params.keyword) query.set('keyword', params.keyword);
  if (params.type && params.type !== 'all') query.set('type', params.type);
  if (params.status && params.status !== 'all') query.set('status', params.status);
  return request<BackendPage<BackendPaymentReconciliationRecord>>(`/api/admin/payment/reconciliation?${query.toString()}`);
}

export function actionPaymentReconciliation(recordId: string, payload: {
  action: 'confirm' | 'reconcile' | 'ignore' | 'remark' | 'rematch';
  remark?: string;
  matchPaymentNo?: string;
}): Promise<BackendPaymentReconciliationRecord> {
  return request<BackendPaymentReconciliationRecord>(
    `/api/admin/payment/reconciliation/${encodeURIComponent(recordId)}/action`,
    { method: 'PUT', body: JSON.stringify(payload) },
  );
}

export function saveMarketingCoupon(payload: {
  id?: number;
  couponCode: string;
  couponTitle: string;
  discountAmount: number;
  minSpend: number;
  categoryCode?: string;
  tag?: string;
  description?: string;
  totalQuantity: number;
  perMemberLimit: number;
  startAt: string;
  endAt: string;
  status: number;
}): Promise<number> {
  return request<number>('/api/admin/marketing/coupons', { method: 'POST', body: JSON.stringify(payload) });
}

export function updateMarketingCouponStatus(id: number, status: number): Promise<void> {
  return request<void>(`/api/admin/marketing/coupons/${id}/status?status=${status}`, { method: 'PUT' });
}

export function deleteMarketingCoupon(id: number): Promise<void> {
  return request<void>(`/api/admin/marketing/coupons/${id}`, { method: 'DELETE' });
}

export function listMarketingFlashSales(params: { current?: number; size?: number; keyword?: string; status?: number } = {}): Promise<BackendPage<BackendMarketingFlashSale>> {
  const query = new URLSearchParams({ current: String(params.current || 1), size: String(params.size || 20) });
  if (params.keyword) query.set('keyword', params.keyword);
  if (params.status !== undefined) query.set('status', String(params.status));
  return request<BackendPage<BackendMarketingFlashSale>>(`/api/admin/marketing/flash-sales?${query.toString()}`);
}

export function listMarketingFlashSaleItems(id: number): Promise<BackendMarketingFlashSaleItem[]> {
  return request<BackendMarketingFlashSaleItem[]>(`/api/admin/marketing/flash-sales/${id}/items`);
}

/** 查询秒杀活动详情，含库存与预占统计。 */
export function getMarketingFlashSaleDetail(id: number): Promise<BackendMarketingFlashSaleDetail> {
  return request<BackendMarketingFlashSaleDetail>(`/api/admin/marketing/flash-sales/${id}`);
}

/** 分页查询活动商品明细，附带商品与规格名称。 */
export function listMarketingFlashSaleItemRows(id: number, params: { current?: number; size?: number } = {}): Promise<BackendPage<BackendMarketingFlashSaleItemRow>> {
  const query = new URLSearchParams({ current: String(params.current || 1), size: String(params.size || 20) });
  return request<BackendPage<BackendMarketingFlashSaleItemRow>>(`/api/admin/marketing/flash-sales/${id}/items/page?${query.toString()}`);
}

/** 分页查询活动预占记录，statusText 取 reserved/released。 */
export function listMarketingFlashSaleReservations(id: number, params: { current?: number; size?: number; statusText?: string } = {}): Promise<BackendPage<BackendMarketingFlashSaleReservationRow>> {
  const query = new URLSearchParams({ current: String(params.current || 1), size: String(params.size || 20) });
  if (params.statusText) query.set('statusText', params.statusText);
  return request<BackendPage<BackendMarketingFlashSaleReservationRow>>(`/api/admin/marketing/flash-sales/${id}/reservations?${query.toString()}`);
}

export function saveMarketingFlashSale(payload: {
  id?: number;
  activityCode: string;
  activityName: string;
  startAt: string;
  endAt: string;
  limitPerMember: number;
  status: number;
  items: Array<{
    id?: number;
    productId: number;
    skuId?: number;
    activityPrice: number;
    totalStock: number;
    limitPerMember: number;
    status: number;
    version?: number;
  }>;
  version?: number;
}): Promise<number> {
  return request<number>('/api/admin/marketing/flash-sales', { method: 'POST', body: JSON.stringify(payload) });
}

export function updateMarketingFlashSaleStatus(id: number, status: number): Promise<void> {
  return request<void>(`/api/admin/marketing/flash-sales/${id}/status?status=${status}`, { method: 'PUT' });
}

export function deleteMarketingFlashSale(id: number): Promise<void> {
  return request<void>(`/api/admin/marketing/flash-sales/${id}`, { method: 'DELETE' });
}

export function listTradeOrders(params: { current?: number; size?: number; keyword?: string; orderStatus?: number } = {}): Promise<BackendPage<BackendTradeOrder>> {
  const query = new URLSearchParams({ current: String(params.current || 1), size: String(params.size || 200) });
  if (params.keyword) query.set('keyword', params.keyword);
  if (params.orderStatus !== undefined) query.set('orderStatus', String(params.orderStatus));
  return request<BackendPage<BackendTradeOrder>>(`/api/admin/trade/orders?${query.toString()}`);
}

export function getTradeFreightTemplate(): Promise<BackendTradeFreightTemplate> {
  return request<BackendTradeFreightTemplate>('/api/admin/trade/freight/template');
}

export function saveTradeFreightTemplate(payload: Omit<BackendTradeFreightTemplate, 'id' | 'isDefault' | 'version'> & { id?: number }): Promise<BackendTradeFreightTemplate> {
  return request<BackendTradeFreightTemplate>('/api/admin/trade/freight/template', {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export function getReportingDashboardMetrics(date?: string): Promise<BackendReportingDashboardMetrics> {
  const query = date ? `?date=${encodeURIComponent(date)}` : '';
  return request<BackendReportingDashboardMetrics>(`/api/admin/reporting/overview${query}`);
}

export function getReportingSalesTrend(startDate?: string, endDate?: string): Promise<BackendReportingSalesTrendPoint[]> {
  const query = new URLSearchParams();
  if (startDate) query.set('startDate', startDate);
  if (endDate) query.set('endDate', endDate);
  const queryString = query.toString();
  return request<BackendReportingSalesTrendPoint[]>(`/api/admin/reporting/sales-trend${queryString ? `?${queryString}` : ''}`);
}

export function getReportingProductRanking(params: {
  startDate?: string;
  endDate?: string;
  limit?: number;
} = {}): Promise<BackendReportingProductRankingItem[]> {
  const query = new URLSearchParams({ limit: String(params.limit || 20) });
  if (params.startDate) query.set('startDate', params.startDate);
  if (params.endDate) query.set('endDate', params.endDate);
  return request<BackendReportingProductRankingItem[]>(`/api/admin/reporting/product-ranking?${query.toString()}`);
}

export function getReportingMemberAnalysis(startDate?: string, endDate?: string): Promise<BackendReportingMemberAnalysis> {
  const query = new URLSearchParams();
  if (startDate) query.set('startDate', startDate);
  if (endDate) query.set('endDate', endDate);
  const queryString = query.toString();
  return request<BackendReportingMemberAnalysis>(`/api/admin/reporting/member-analysis${queryString ? `?${queryString}` : ''}`);
}

export function getReportingChannelStats(startDate?: string, endDate?: string): Promise<BackendReportingChannelStat[]> {
  const query = new URLSearchParams();
  if (startDate) query.set('startDate', startDate);
  if (endDate) query.set('endDate', endDate);
  const queryString = query.toString();
  return request<BackendReportingChannelStat[]>(`/api/admin/reporting/channel-stats${queryString ? `?${queryString}` : ''}`);
}

export type BackendExportTaskStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'EXPIRED';

export interface BackendExportTask {
  id: number;
  taskNo: string | null;
  exportType: string;
  exportName: string;
  status: BackendExportTaskStatus;
  fileName: string | null;
  fileSize: number | null;
  rowCount: number | null;
  errorMessage: string | null;
  requestedByName: string | null;
  createdAt: string | null;
  finishedAt: string | null;
  expiresAt: string | null;
  downloadable: boolean;
}

/** 导出任务提交条件，含义随导出类型而定，未使用的字段保持为空。 */
export interface ExportTaskRequest {
  exportType: string;
  keyword?: string;
  status?: number;
  statusText?: string;
  categoryId?: number;
  orderStatus?: number;
  memberLevel?: string;
  moduleKey?: string;
  financeType?: string;
  /** 库存流水业务类型：RESERVE/RELEASE/EXPIRE_RELEASE/DEDUCT。 */
  bizType?: string;
  skuId?: number;
  warehouseId?: number;
  /** 秒杀活动ID，秒杀商品明细与预占记录导出的必需条件。 */
  activityId?: number;
  startDate?: string;
  endDate?: string;
  rankLimit?: number;
}

export function submitExportTask(payload: ExportTaskRequest): Promise<BackendExportTask> {
  return request<BackendExportTask>('/api/admin/export/tasks', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function listExportTasks(params: { current?: number; size?: number } = {}): Promise<BackendPage<BackendExportTask>> {
  const query = new URLSearchParams({ current: String(params.current || 1), size: String(params.size || 20) });
  return request<BackendPage<BackendExportTask>>(`/api/admin/export/tasks?${query.toString()}`);
}

export function retryExportTask(taskId: number): Promise<BackendExportTask> {
  return request<BackendExportTask>(`/api/admin/export/tasks/${taskId}/retry`, { method: 'POST' });
}

export function deleteExportTask(taskId: number): Promise<void> {
  return request<void>(`/api/admin/export/tasks/${taskId}`, { method: 'DELETE' });
}

/**
 * 下载导出文件。
 *
 * 文件走服务端流式转发而不是预签名地址，因此必须携带登录令牌；
 * 令牌过期时先刷新再重放一次，避免用户看到空文件。
 */
export async function downloadExportTaskFile(taskId: number): Promise<Blob> {
  const send = (token: string | null) => fetch(`${API_BASE_URL}/api/admin/export/tasks/${taskId}/file`, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  });
  let response = await send(getAdminToken());
  if (response.status === 401) {
    const refreshedToken = await refreshAdminToken();
    if (refreshedToken) {
      response = await send(refreshedToken);
    }
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null) as ApiEnvelope<unknown> | null;
    throw new Error(body?.message || `下载失败（${response.status}）`);
  }
  return response.blob();
}

export function shipTradeOrder(orderId: number, payload: { logisticsCompany: string; trackingNo: string }): Promise<void> {
  return request<void>(`/api/admin/trade/orders/${orderId}/ship`, { method: 'PUT', body: JSON.stringify(payload) });
}

export function batchShipTradeOrders(shipments: Array<{
  orderId: number;
  logisticsCompany: string;
  trackingNo: string;
}>): Promise<number> {
  return request<number>('/api/admin/trade/orders/batch-ship', {
    method: 'POST',
    body: JSON.stringify({ shipments })
  });
}

export function syncTradeOrderLogistics(orderId: number): Promise<{
  success: boolean;
  provider: string;
  status: string;
  syncedCount: number;
  message: string;
}> {
  return request(`/api/admin/trade/orders/${orderId}/logistics/sync`, { method: 'POST' });
}

export function listTradeOrderLogistics(orderId: number): Promise<BackendTradeOrderLogistics[]> {
  return request<BackendTradeOrderLogistics[]>(`/api/admin/trade/orders/${orderId}/logistics`);
}

export function upsertTradeOrderLogistics(orderId: number, payload: {
  id?: number;
  logisticsCompany: string;
  trackingNo: string;
  logisticsStatus?: string;
  eventTime: string;
  eventDescription: string;
  eventLocation?: string;
  sortNo?: number;
}): Promise<BackendTradeOrderLogistics> {
  return request<BackendTradeOrderLogistics>(`/api/admin/trade/orders/${orderId}/logistics`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function appendTradeOrderLogistics(orderId: number, payload: {
  logisticsCompany: string;
  trackingNo: string;
  logisticsStatus?: string;
  eventTime: string;
  eventDescription: string;
  eventLocation?: string;
  sortNo?: number;
}): Promise<BackendTradeOrderLogistics> {
  return upsertTradeOrderLogistics(orderId, payload);
}

export function updateTradeOrderLogistics(orderId: number, logisticsId: number, payload: {
  logisticsCompany: string;
  trackingNo: string;
  logisticsStatus?: string;
  eventTime: string;
  eventDescription: string;
  eventLocation?: string;
  sortNo?: number;
}): Promise<BackendTradeOrderLogistics> {
  return request<BackendTradeOrderLogistics>(`/api/admin/trade/orders/${orderId}/logistics/${logisticsId}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
}

export function cancelTradeOrder(orderId: number, reason?: string): Promise<void> {
  return request<void>(`/api/admin/trade/orders/${orderId}/cancel`, { method: 'PUT', body: JSON.stringify({ reason }) });
}

export function auditTradeOrder(orderId: number, approved: boolean, payload: { remark?: string; version: number }): Promise<BackendTradeOrder> {
  const query = new URLSearchParams({ approved: String(approved) });
  return request<BackendTradeOrder>(`/api/admin/trade/orders/${orderId}/audit?${query.toString()}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
}

export function approveTradeOrder(orderId: number, payload: { remark?: string; version: number }): Promise<BackendTradeOrder> {
  return request<BackendTradeOrder>(`/api/admin/trade/orders/${orderId}/audit/approve`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
}

export function rejectTradeOrder(orderId: number, payload: { remark?: string; version: number }): Promise<BackendTradeOrder> {
  return request<BackendTradeOrder>(`/api/admin/trade/orders/${orderId}/audit/reject`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
}

export function updateTradeOrderRemark(orderId: number, payload: {
  sellerRemark: string;
  flagColor?: 'red' | 'yellow' | 'green' | 'blue' | 'purple' | null;
  version: number;
}): Promise<BackendTradeOrder> {
  return request<BackendTradeOrder>(`/api/admin/trade/orders/${orderId}/remark`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
}

export function refundTradeOrder(orderId: number, refundAmount: number, reason: string): Promise<void> {
  return request<void>('/api/admin/payment/refunds', {
    method: 'POST',
    body: JSON.stringify({ orderId, amount: refundAmount, reason, idempotencyKey: `admin-refund-${orderId}-${refundAmount}` })
  });
}

export function listTradeAfterSales(params: { current?: number; size?: number; status?: number; orderId?: number } = {}): Promise<BackendPage<BackendTradeAfterSale>> {
  const query = new URLSearchParams({ current: String(params.current || 1), size: String(params.size || 200) });
  if (params.status !== undefined) query.set('status', String(params.status));
  if (params.orderId !== undefined) query.set('orderId', String(params.orderId));
  return request<BackendPage<BackendTradeAfterSale>>(`/api/admin/trade/after-sales?${query.toString()}`);
}

export function approveTradeAfterSale(afterSaleId: number, remark?: string): Promise<BackendTradeAfterSale> {
  return request<BackendTradeAfterSale>(`/api/admin/trade/after-sales/${afterSaleId}/approve`, {
    method: 'PUT',
    body: JSON.stringify({ remark })
  });
}

export function rejectTradeAfterSale(afterSaleId: number, remark?: string): Promise<BackendTradeAfterSale> {
  return request<BackendTradeAfterSale>(`/api/admin/trade/after-sales/${afterSaleId}/reject`, {
    method: 'PUT',
    body: JSON.stringify({ remark })
  });
}

export function confirmTradeAfterSaleReturn(afterSaleId: number, remark?: string): Promise<BackendTradeAfterSale> {
  return request<BackendTradeAfterSale>(`/api/admin/trade/after-sales/${afterSaleId}/return-received`, {
    method: 'PUT',
    body: JSON.stringify({ remark }),
  });
}

export function listInventoryStocks(params: { current?: number; size?: number; skuId?: number } = {}): Promise<BackendPage<BackendInventoryStock>> {
  const query = new URLSearchParams({ current: String(params.current || 1), size: String(params.size || 200) });
  if (params.skuId !== undefined) query.set('skuId', String(params.skuId));
  return request<BackendPage<BackendInventoryStock>>(`/api/admin/inventory/stocks?${query.toString()}`);
}

export function listInventoryStockLocks(params: { current?: number; size?: number; orderId?: number; status?: number } = {}): Promise<BackendPage<BackendInventoryStockLock>> {
  const query = new URLSearchParams({ current: String(params.current || 1), size: String(params.size || 200) });
  if (params.orderId !== undefined) query.set('orderId', String(params.orderId));
  if (params.status !== undefined) query.set('status', String(params.status));
  return request<BackendPage<BackendInventoryStockLock>>(`/api/admin/inventory/locks?${query.toString()}`);
}

export function saveInventoryStock(payload: {
  warehouseId?: number;
  productId?: number;
  skuId: number;
  availableStock: number;
  safetyStock?: number;
  remark?: string;
}): Promise<BackendInventoryStock> {
  return request<BackendInventoryStock>('/api/admin/inventory/stocks', {
    method: 'POST',
    body: JSON.stringify({ safetyStock: 0, ...payload })
  });
}

export function adjustInventoryStock(stockId: number, changeQuantity: number, remark?: string): Promise<BackendInventoryStock> {
  return request<BackendInventoryStock>(`/api/admin/inventory/stocks/${stockId}/adjust`, {
    method: 'PUT',
    body: JSON.stringify({ changeQuantity, remark })
  });
}

export function replaceRoleDataRules(roleId: number, ruleIds: number[]): Promise<void> {
  return request<void>(`/api/admin/system/roles/${roleId}/data-rules`, {
    method: 'PUT',
    body: JSON.stringify(ruleIds)
  });
}

export function createSystemUser(user: {
  tenantId?: number;
  username: string;
  password: string;
  realName: string;
  nickname?: string;
  phone?: string;
  email?: string;
  avatarUrl?: string;
  deptId?: number;
  status?: number;
  remark?: string;
}): Promise<number> {
  return request<number>('/api/admin/system/users', { method: 'POST', body: JSON.stringify(user) });
}

export function updateSystemUser(id: number, user: {
  realName?: string;
  nickname?: string;
  phone?: string;
  email?: string;
  avatarUrl?: string;
  deptId?: number;
  status?: number;
  remark?: string;
}): Promise<void> {
  return request<void>(`/api/admin/system/users/${id}`, { method: 'PUT', body: JSON.stringify(user) });
}

export function deleteSystemUser(id: number): Promise<void> {
  return request<void>(`/api/admin/system/users/${id}`, { method: 'DELETE' });
}

export interface LoginCaptcha {
  captchaId: string;
  imageBase64: string;
}

/** 获取登录图形验证码；明文只存在服务端 Redis，前端仅持有图片与标识。 */
export function getLoginCaptcha(): Promise<LoginCaptcha> {
  return request<LoginCaptcha>('/api/admin/auth/captcha', { method: 'GET' }, undefined);
}

export async function loginAdmin(username: string, password: string, captchaId: string, captchaCode: string): Promise<{ token: string; user: AdminUser }> {
  const data = await request<{ accessToken: string; refreshToken: string; userId: number; tenantId?: number; username: string; realName: string; avatarUrl?: string; permissions: string[] }>(
    '/api/admin/auth/login',
    { method: 'POST', body: JSON.stringify({ username, password, captchaId, captchaCode }) },
    undefined
  );
  localStorage.setItem(TOKEN_KEY, data.accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, data.refreshToken);
  return {
    token: data.accessToken,
    user: { userId: data.userId, tenantId: data.tenantId ?? 0, username: data.username, realName: data.realName, avatarUrl: data.avatarUrl, permissions: data.permissions }
  };
}

/** 修改当前管理员密码。 */
export function changeAdminPassword(oldPassword: string, newPassword: string): Promise<void> {
  return request<void>('/api/admin/auth/password', {
    method: 'POST',
    body: JSON.stringify({ oldPassword, newPassword }),
  });
}

/** 注销当前管理员会话并吊销访问令牌。 */
export function logoutAdmin(): Promise<void> {
  return request<void>('/api/admin/auth/logout', {
    method: 'POST',
    body: JSON.stringify({ refreshToken: getAdminRefreshToken() }),
  });
}

export function getCurrentAdmin(): Promise<AdminUser> {
  return request<AdminUser>('/api/admin/auth/me');
}

/**
 * 修改当前登录管理员本人的资料。
 *
 * 头像传对象键或上传接口返回的访问地址均可，服务端会归一化成对象键；字段传 undefined 表示不改动。
 */
export function updateAdminProfile(payload: {
  nickname?: string;
  phone?: string;
  email?: string;
  avatarUrl?: string;
}): Promise<AdminUser> {
  return request<AdminUser>('/api/admin/auth/profile', {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export function getAdminMenus(): Promise<BackendMenu[]> {
  return request<BackendMenu[]>('/api/admin/auth/menus');
}

/** AI 客服转人工工单。 */
export interface BackendAiTicket {
  id: number;
  ticketNo?: string;
  conversationId?: string;
  memberId?: number;
  contact?: string;
  question: string;
  aiSummary?: string;
  /** PENDING 待处理，PROCESSING 处理中，CLOSED 已关闭。 */
  status: 'PENDING' | 'PROCESSING' | 'CLOSED';
  handlerId?: number;
  handlerName?: string;
  handleNote?: string;
  handledAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export function listAiTickets(params: { current?: number; size?: number; status?: string; keyword?: string } = {}): Promise<BackendPage<BackendAiTicket>> {
  const query = new URLSearchParams({ current: String(params.current || 1), size: String(params.size || 20) });
  if (params.status) query.set('status', params.status);
  if (params.keyword) query.set('keyword', params.keyword);
  return request<BackendPage<BackendAiTicket>>(`/api/admin/ai/tickets?${query.toString()}`);
}

export function getAiTicket(id: number): Promise<BackendAiTicket> {
  return request<BackendAiTicket>(`/api/admin/ai/tickets/${id}`);
}

export function handleAiTicket(id: number, payload: { status: string; handleNote?: string }): Promise<BackendAiTicket> {
  return request<BackendAiTicket>(`/api/admin/ai/tickets/${id}/handle`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}
