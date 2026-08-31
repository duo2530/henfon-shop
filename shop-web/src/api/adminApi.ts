export interface AdminUser {
  userId: number;
  tenantId: number;
  username: string;
  realName?: string;
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
  mainImageUrl?: string;
  tagsCsv?: string;
  status: number;
  createdAt?: string;
}

export interface BackendCatalogCategory {
  id: number;
  categoryName: string;
  categoryCode: string;
  status: number;
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
  imageUrl: string;
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
  helpfulCount: number;
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
  claimedQuantity: number;
  startAt: string;
  endAt: string;
  status: number;
  createdAt?: string;
  updatedAt?: string;
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

export interface BackendTradeOrder {
  id: number;
  orderNo: string;
  memberId?: number;
  memberName?: string;
  orderStatus: number;
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
  logisticsCompany?: string;
  trackingNo?: string;
  createdAt?: string;
  paidAt?: string;
  shippedAt?: string;
  completedAt?: string;
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

export const getAdminToken = (): string | null => localStorage.getItem(TOKEN_KEY);

export const clearAdminToken = (): void => localStorage.removeItem(TOKEN_KEY);

async function request<T>(path: string, options: RequestInit = {}, token?: string): Promise<T> {
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

export function listCatalogProducts(params: { current?: number; size?: number; keyword?: string; status?: number } = {}): Promise<BackendPage<BackendCatalogProduct>> {
  const query = new URLSearchParams({ current: String(params.current || 1), size: String(params.size || 200) });
  if (params.keyword) query.set('keyword', params.keyword);
  if (params.status !== undefined) query.set('status', String(params.status));
  return request<BackendPage<BackendCatalogProduct>>(`/api/admin/catalog/products?${query.toString()}`);
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

export function listInventoryWarnings(): Promise<BackendInventoryStock[]> {
  return request<BackendInventoryStock[]>('/api/admin/inventory/stocks/warnings');
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
  const allowedContentTypes = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'video/mp4']);
  if (!file || file.size === 0) {
    throw new Error('上传文件不能为空');
  }
  if (file.size > maxFileSize) {
    throw new Error('文件大小不能超过10MB');
  }
  if (!allowedContentTypes.has(file.type.toLowerCase())) {
    throw new Error('仅支持 JPG、PNG、WEBP、GIF 和 MP4 文件');
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

export function listContentReviews(params: { current?: number; size?: number; productId?: number; status?: number; keyword?: string } = {}): Promise<BackendPage<BackendContentReview>> {
  const query = new URLSearchParams({ current: String(params.current || 1), size: String(params.size || 100) });
  if (params.productId !== undefined) query.set('productId', String(params.productId));
  if (params.status !== undefined) query.set('status', String(params.status));
  if (params.keyword) query.set('keyword', params.keyword);
  return request<BackendPage<BackendContentReview>>(`/api/admin/content/reviews?${query.toString()}`);
}

export function updateContentReviewStatus(id: number, status: 0 | 1 | 2): Promise<void> {
  return request<void>(`/api/admin/content/reviews/${id}/status?status=${status}`, { method: 'PUT' });
}

export function replyContentReview(id: number, replyContent: string): Promise<void> {
  return request<void>(`/api/admin/content/reviews/${id}/reply`, {
    method: 'PUT',
    body: JSON.stringify({ replyContent }),
  });
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

export function listTradeOrders(params: { current?: number; size?: number; keyword?: string; orderStatus?: number } = {}): Promise<BackendPage<BackendTradeOrder>> {
  const query = new URLSearchParams({ current: String(params.current || 1), size: String(params.size || 200) });
  if (params.keyword) query.set('keyword', params.keyword);
  if (params.orderStatus !== undefined) query.set('orderStatus', String(params.orderStatus));
  return request<BackendPage<BackendTradeOrder>>(`/api/admin/trade/orders?${query.toString()}`);
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

export function shipTradeOrder(orderId: number, payload: { logisticsCompany: string; trackingNo: string }): Promise<void> {
  return request<void>(`/api/admin/trade/orders/${orderId}/ship`, { method: 'PUT', body: JSON.stringify(payload) });
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

export function updateTradeOrderRemark(orderId: number, sellerRemark: string): Promise<void> {
  return request<void>(`/api/admin/trade/orders/${orderId}/remark`, { method: 'PUT', body: JSON.stringify({ sellerRemark }) });
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

export function listInventoryStocks(params: { current?: number; size?: number; skuId?: number } = {}): Promise<BackendPage<BackendInventoryStock>> {
  const query = new URLSearchParams({ current: String(params.current || 1), size: String(params.size || 200) });
  if (params.skuId !== undefined) query.set('skuId', String(params.skuId));
  return request<BackendPage<BackendInventoryStock>>(`/api/admin/inventory/stocks?${query.toString()}`);
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

export async function loginAdmin(username: string, password: string): Promise<{ token: string; user: AdminUser }> {
  const data = await request<{ accessToken: string; userId: number; username: string; realName: string; permissions: string[] }>(
    '/api/admin/auth/login',
    { method: 'POST', body: JSON.stringify({ username, password }) },
    undefined
  );
  localStorage.setItem(TOKEN_KEY, data.accessToken);
  return {
    token: data.accessToken,
    user: { userId: data.userId, tenantId: 0, username: data.username, realName: data.realName, permissions: data.permissions }
  };
}

/** 修改当前管理员密码。 */
export function changeAdminPassword(oldPassword: string, newPassword: string): Promise<void> {
  return request<void>('/api/admin/auth/password', {
    method: 'POST',
    body: JSON.stringify({ oldPassword, newPassword }),
  });
}

export function getCurrentAdmin(): Promise<AdminUser> {
  return request<AdminUser>('/api/admin/auth/me');
}

export function getAdminMenus(): Promise<BackendMenu[]> {
  return request<BackendMenu[]>('/api/admin/auth/menus');
}
