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
  const headers = new Headers(options.headers);
  headers.set('Content-Type', 'application/json');
  const accessToken = token || getAdminToken();
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }
  const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers });
  const body = await response.json().catch(() => null) as ApiEnvelope<T> | null;
  if (!response.ok || !body || body.code !== '0') {
    throw new Error(body?.message || `请求失败（${response.status}）`);
  }
  return body.data;
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

export function listTradeOrders(params: { current?: number; size?: number; keyword?: string; orderStatus?: number } = {}): Promise<BackendPage<BackendTradeOrder>> {
  const query = new URLSearchParams({ current: String(params.current || 1), size: String(params.size || 200) });
  if (params.keyword) query.set('keyword', params.keyword);
  if (params.orderStatus !== undefined) query.set('orderStatus', String(params.orderStatus));
  return request<BackendPage<BackendTradeOrder>>(`/api/admin/trade/orders?${query.toString()}`);
}

export function shipTradeOrder(orderId: number, payload: { logisticsCompany: string; trackingNo: string }): Promise<void> {
  return request<void>(`/api/admin/trade/orders/${orderId}/ship`, { method: 'PUT', body: JSON.stringify(payload) });
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

export function getCurrentAdmin(): Promise<AdminUser> {
  return request<AdminUser>('/api/admin/auth/me');
}

export function getAdminMenus(): Promise<BackendMenu[]> {
  return request<BackendMenu[]>('/api/admin/auth/menus');
}
