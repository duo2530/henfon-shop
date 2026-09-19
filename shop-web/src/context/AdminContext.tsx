import React, { createContext, useCallback, useContext, useEffect, useState, ReactNode } from 'react';
import { 
  Product, 
  ProductStatus,
  ProductCategory,
  Order, 
  User, 
  UserStatus,
  TodoItem, 
  NavigationTab, 
  Role,
  MenuItem,
  SystemUser,
  Department,
  DataRule
} from '../types';
import { formatDateTime } from '../utils/datetime';
import {
  AdminUser,
  BackendCatalogProduct,
  BackendLogisticsCarrier,
  BackendMemberTag,
  BackendMenu,
  BackendTradeOrder,
  clearAdminToken,
  getAdminMenus,
  getAdminToken,
  getCurrentAdmin,
  logoutAdmin,
  updateAdminProfile,
  listMemberUsers,
  listMemberTags,
  createMemberUser,
  updateMemberStatus,
  updateMemberProfile,
  adjustMemberAssets,
  updateMemberTags,
  listDepartments,
  listDataRules,
  listRoleMenuIds,
  listRoleDataRuleIds,
  replaceRoleMenus,
  listSystemMenus,
  listSystemRoles,
  listSystemUsers,
  listLogisticsCarriers,
  listUserRoleIds,
  loginAdmin,
  replaceRoleDataRules,
  replaceUserRoles,
  saveSystemDataRule,
  saveSystemMenu,
  saveSystemRole,
  createSystemUser,
  updateSystemUser as updateSystemUserApi,
  deleteSystemUser as deleteSystemUserApi,
  deleteSystemMenu as deleteSystemMenuApi,
  deleteSystemRole,
  deleteSystemDataRule,
  deleteCatalogProduct as deleteCatalogProductApi,
  updateCatalogProductStatus,
  listCatalogCategories,
  listCatalogProducts,
  saveCatalogProduct as saveCatalogProductApi,
  saveCatalogProductContent,
  listTradeOrders,
  shipTradeOrder,
  batchShipTradeOrders,
  syncTradeOrderLogistics,
  cancelTradeOrder,
  updateTradeOrderRemark,
  refundTradeOrder,
  listInventoryWarnings,
  listTradeAfterSales,
  auditTradeOrder,
  getContentNotificationSummary,
  markContentNotificationRead,
  markAllContentNotificationsRead
} from '../api/adminApi';
import { backendMenusToTree, containsMenuTab, firstMenuTab } from '../navigation/menuAdapter';
import { backendDataRulesToFrontend, backendDepartmentsToFrontend, backendMembersToFrontend, backendMenusToFrontend, backendRolesToFrontend, backendUsersToFrontend } from '../navigation/identityAdapter';
import { backendCategoriesToMap, backendCategoriesToOptions, backendProductsToFrontend } from '../navigation/catalogAdapter';
import { AdminDialog, type AdminDialogRequest } from '../components/common/AdminDialog';

interface Toast {
  id: string;
  message: string;
  type: 'success' | 'info' | 'warning' | 'error';
}

type DialogResult = boolean | string | null;

interface AdminContextType {
  authLoading: boolean;
  isAuthenticated: boolean;
  currentUser: AdminUser | null;
  login: (username: string, password: string, captchaId: string, captchaCode: string) => Promise<void>;
  logout: () => Promise<void>;
  /** 修改当前登录管理员本人的资料；头像传对象键或访问地址均可，服务端会归一化。 */
  updateCurrentProfile: (payload: {
    nickname?: string;
    phone?: string;
    email?: string;
    avatarUrl?: string;
  }) => Promise<void>;
  currentTab: NavigationTab;
  setCurrentTab: (tab: NavigationTab) => void;
  /** 判断当前管理员是否拥有指定按钮权限（支持超级管理员通配符）。 */
  hasPermission: (permission: string) => boolean;
  /** 在执行写操作前统一校验权限，并在拒绝时给出可理解的提示。 */
  requirePermission: (permission: string, actionLabel?: string) => boolean;
  products: Product[];
  orders: Order[];
  users: User[];
  /** 会员画像标签字典（含绑定人数），用于筛选栏展示完整标签体系。 */
  memberTags: BackendMemberTag[];
  /** 重新拉取画像标签字典。 */
  refreshMemberTags: () => Promise<void>;
  /** 服务端类目（含父子关系），商品页按类目筛选时据此展开整棵子树。 */
  catalogCategories: Array<{ id: number; code: ProductCategory; name: string; parentId?: number }>;
  logisticsCarriers: BackendLogisticsCarrier[];
  todos: TodoItem[];
  /** 服务端统计的运营未读通知数，顶栏铃铛角标据此显示（0 时不渲染角标）。 */
  notificationUnreadCount: number;
  searchQuery: string;
  setSearchQuery: (query: string) => void;
  toasts: Toast[];
  showToast: (message: string, type?: 'success' | 'info' | 'warning' | 'error') => void;
  removeToast: (id: string) => void;
  confirm: (message: string, title?: string) => Promise<boolean>;
  prompt: (message: string, options?: { title?: string; defaultValue?: string }) => Promise<string | null>;
  
  // Product actions
  addProduct: (product: Omit<Product, 'id' | 'createdAt' | 'salesCount'>) => Promise<number | null>;
  updateProduct: (id: string, updates: Partial<Product>) => void;
  deleteProduct: (id: string) => void;
  toggleProductStatus: (id: string) => void;
  batchUpdateProductStatus: (ids: string[], status: ProductStatus) => void;
  batchDeleteProducts: (ids: string[]) => void;
  batchUpdateProductCategory: (ids: string[], category: ProductCategory, categoryName: string) => void;
  adjustProductStock: (id: string, newStock: number, reason?: string) => void;
  
  // Order actions
  updateOrderStatus: (id: string, status: Order['status'], trackingNumber?: string, carrier?: string) => void;
  syncOrderLogistics: (id: string) => Promise<void>;
  cancelOrder: (id: string) => void;
  batchShipOrders: (shipments: { orderId: string; carrier: string; trackingNumber: string }[]) => void;
  batchCancelOrders: (ids: string[], reason?: string) => void;
  updateOrderRemark: (id: string, sellerNote: string, flagColor?: Order['flagColor'] | null) => void;
  processOrderRefund: (id: string, refundAmount: number, refundReason: string) => Promise<void>;
  auditOrder: (id: string, approved: boolean, remark?: string) => Promise<void>;
  
  // User actions (Customer members)
  addUser: (user: Omit<User, 'id' | 'registeredAt' | 'totalSpent' | 'orderCount' | 'lastActive'>) => Promise<void>;
  updateUserStatus: (id: string, status: User['status']) => void;
  updateUser: (id: string, updates: Partial<User>) => void;
  batchUpdateUserStatus: (ids: string[], status: UserStatus) => void;
  adjustUserBalanceAndPoints: (id: string, pointsDelta: number, balanceDelta: number, note: string) => void;
  updateUserTags: (id: string, tags: string[]) => void;
  
  // RBAC Roles
  roles: Role[];
  addRole: (role: Omit<Role, 'id' | 'createdAt' | 'userCount'>) => void;
  updateRole: (id: string, updates: Partial<Role>) => void;
  deleteRole: (id: string) => void;
  updateRolePermissions: (roleId: string, permissionKeys: string[]) => void;
  updateRoleDataScope: (roleId: string, dataScope: Role['dataScope'], customDeptIds?: string[]) => void;

  // RBAC Menus
  menuItems: MenuItem[];
  authorizedMenuItems: MenuItem[];
  addMenuItem: (item: Omit<MenuItem, 'id'>) => void;
  updateMenuItem: (id: string, updates: Partial<MenuItem>) => void;
  deleteMenuItem: (id: string) => void;

  // RBAC System Users
  systemUsers: SystemUser[];
  addSystemUser: (user: Omit<SystemUser, 'id' | 'createdAt' | 'lastLoginTime' | 'lastLoginIp'> & { initialPassword: string }) => void;
  updateSystemUser: (id: string, updates: Partial<SystemUser>) => void;
  deleteSystemUser: (id: string) => void;
  toggleSystemUserStatus: (id: string) => void;

  // RBAC Departments
  departments: Department[];

  // RBAC Data Rules
  dataRules: DataRule[];
  addDataRule: (rule: Omit<DataRule, 'id' | 'updatedAt'>) => void;
  updateDataRule: (id: string, updates: Partial<DataRule>) => void;
  deleteDataRule: (id: string) => void;
  toggleDataRuleStatus: (id: string) => void;

  // Todo actions
  resolveTodo: (id: string) => void;
  
  // Notification actions
  markNotificationAsRead: (id: number) => Promise<void>;
  markAllNotificationsAsRead: () => Promise<number>;
  /** 重新统计运营未读通知数，标记已读后调用。 */
  refreshNotificationUnreadCount: () => Promise<void>;
}

const AdminContext = createContext<AdminContextType | undefined>(undefined);

function menuItemsFlat(items: MenuItem[]): MenuItem[] {
  return items.flatMap((item) => [item, ...(item.children ? menuItemsFlat(item.children) : [])]);
}

function roleScopeToBackend(scope: Role['dataScope']): string {
  return scope === 'dept_and_sub' ? 'DEPT_AND_SUB' : scope.toUpperCase();
}

function menuTypeToBackend(type: MenuItem['type']): 'DIRECTORY' | 'MENU' | 'BUTTON' {
  return type === 'directory' ? 'DIRECTORY' : type === 'button' ? 'BUTTON' : 'MENU';
}

function productToCatalogRequest(product: Product) {
  return {
    id: Number(product.id),
    // 商品类目ID必须来自类目接口，避免用前端枚举推断数据库主键。
    categoryId: product.categoryId,
    categoryName: product.categoryName,
    productName: product.name,
    productCode: product.productCode || product.sku || `PROD-${product.id}`,
    defaultSkuCode: product.sku,
    description: product.description,
    price: product.price,
    marketPrice: product.originalPrice,
    costPrice: product.costPrice,
    currentStock: product.stock,
    safetyStock: product.safetyStock,
    mainImageUrl: product.imageUrl,
    tagsCsv: product.tags?.join(','),
    status: product.status === 'active' ? 1 : 2
  };
}

function backendOrderStatusToFrontend(status: number): Order['status'] {
  if (status === 10) return 'pending_payment';
  if (status === 20) return 'pending_shipment';
  if (status === 30) return 'shipped';
  if (status === 40) return 'completed';
  if (status === 50) return 'cancelled';
  if (status === 60) return 'refunding';
  if (status === 70) return 'refunded';
  return 'pending_payment';
}

function backendPaymentMethodToFrontend(method?: string): Order['paymentMethod'] {
  const normalized = (method || '').toLowerCase();
  if (normalized.includes('alipay') || normalized.includes('支付宝')) return 'alipay';
  if (normalized.includes('card') || normalized.includes('银行卡')) return 'card';
  return 'wechat';
}

function backendOrdersToFrontend(records: BackendTradeOrder[]): Order[] {
  return records.map((record) => ({
    id: String(record.id),
    orderNumber: record.orderNo,
    createdAt: formatDateTime(record.createdAt, ''),
    customerName: record.memberName || record.receiverName,
    customerPhone: record.receiverPhone,
    amount: Number(record.paidAmount || record.payableAmount || 0),
    paymentMethod: backendPaymentMethodToFrontend(record.paymentMethod),
    status: backendOrderStatusToFrontend(record.orderStatus),
    auditStatus: record.auditStatus === 20 ? 'approved' : record.auditStatus === 30 ? 'rejected' : 'pending',
    auditRemark: record.auditRemark || undefined,
    items: (record.items || []).map((item) => ({
      productId: String(item.productId),
      productName: item.productName,
      price: Number(item.unitPrice || 0),
      quantity: Number(item.quantity || 0),
      imageUrl: item.imageUrl || '',
      sku: item.skuCode || item.skuName || undefined,
    })),
    shippingAddress: [record.receiverProvince, record.receiverCity, record.receiverDistrict, record.receiverAddress]
      .filter(Boolean)
      .join(' '),
    trackingNumber: record.trackingNo || undefined,
    shippingCarrier: record.logisticsCompany || undefined,
    sellerNote: record.sellerRemark || undefined,
    flagColor: record.flagColor || undefined,
    version: record.version,
    discountAmount: Number(record.discountAmount || 0),
    freightAmount: Number(record.freightAmount || 0),
    refundStatus: record.orderStatus === 60 ? 'pending' : record.orderStatus === 70 ? 'approved' : 'none'
  }));
}

export const AdminProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [authLoading, setAuthLoading] = useState(true);
  const [currentUser, setCurrentUser] = useState<AdminUser | null>(null);
  const [currentTab, setCurrentTabState] = useState<NavigationTab>('dashboard');
  const [products, setProducts] = useState<Product[]>([]);
  const [orders, setOrders] = useState<Order[]>([]);
  // 会员列表以服务端返回为唯一事实来源，避免管理端展示本地演示会员。
  const [users, setUsers] = useState<User[]>([]);
  // 画像标签字典（含每个标签的绑定人数）。筛选栏展示的是完整标签体系，不能从当前页会员数据反向推导。
  const [memberTags, setMemberTags] = useState<BackendMemberTag[]>([]);
  const [catalogCategories, setCatalogCategories] = useState<Array<{ id: number; code: ProductCategory; name: string; parentId?: number }>>([]);
  const [logisticsCarriers, setLogisticsCarriers] = useState<BackendLogisticsCarrier[]>([]);
  // 工作台待办和通知只展示服务端同步结果，避免把本地演示数据误当成线上业务数据。
  const [todos, setTodos] = useState<TodoItem[]>([]);
  const [notificationUnreadCount, setNotificationUnreadCount] = useState<number>(0);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [toasts, setToasts] = useState<Toast[]>([]);
  const [dialog, setDialog] = useState<AdminDialogRequest | null>(null);

  // RBAC state
  // RBAC 数据只允许来自服务端，接口不可用时保持空状态，避免展示本地演示数据。
  const [roles, setRoles] = useState<Role[]>([]);
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [authorizedMenuItems, setAuthorizedMenuItems] = useState<MenuItem[]>([]);
  const [systemUsers, setSystemUsers] = useState<SystemUser[]>([]);
  const [departments, setDepartmentsState] = useState<Department[]>([]);
  const [dataRules, setDataRules] = useState<DataRule[]>([]);

  const showToast = useCallback((message: string, type: 'success' | 'info' | 'warning' | 'error' = 'success') => {
    const id = Date.now().toString() + Math.random().toString(36).substring(2, 5);
    setToasts((prev) => [...prev, { id, message, type }]);
    setTimeout(() => setToasts((prev) => prev.filter((toast) => toast.id !== id)), 3500);
  }, []);

  /**
   * 重新拉取会员画像标签字典。
   *
   * 新增或删除标签后调用，保证筛选栏的标签全集与后端一致。
   * 拉取失败时清空字典，页面会回退到「按已加载会员的标签推导」，不会因此丢掉筛选能力。
   */
  const refreshMemberTags = useCallback(async () => {
    try {
      setMemberTags(await listMemberTags());
    } catch {
      setMemberTags([]);
    }
  }, []);

  const openDialog = useCallback((request: Omit<AdminDialogRequest, 'resolve'>): Promise<DialogResult> => {
    return new Promise<DialogResult>((resolve) => {
      setDialog((previous) => {
        if (previous) previous.resolve(previous.kind === 'confirm' ? false : null);
        return { ...request, resolve: (value) => resolve(value) };
      });
    });
  }, []);

  const confirm = useCallback(async (message: string, title = '请确认操作'): Promise<boolean> => {
    return (await openDialog({ kind: 'confirm', title, message })) === true;
  }, [openDialog]);

  const prompt = useCallback(async (message: string, options: { title?: string; defaultValue?: string } = {}): Promise<string | null> => {
    const result = await openDialog({
      kind: 'prompt',
      title: options.title || '请输入信息',
      message,
      defaultValue: options.defaultValue
    });
    return typeof result === 'string' ? result : null;
  }, [openDialog]);

  const resolveDialog = useCallback((request: AdminDialogRequest, value: DialogResult) => {
    request.resolve(value);
    setDialog((current) => current === request ? null : current);
  }, []);

  const hasPermission = useCallback((permission: string): boolean => {
    const permissions = currentUser?.permissions || [];
    if (permissions.includes('*:*:*') || permissions.includes(permission)) return true;
    // 兼容后端当前以模块查询权限保护商品/订单列表的实现，后续补充细粒度按钮权限后自动优先使用精确值。
    const aliases: Record<string, string[]> = {
      'product:add': ['catalog:product:save', 'catalog:product:query'],
      'product:edit': ['catalog:product:save', 'catalog:product:query'],
      'product:delete': ['catalog:product:delete', 'catalog:product:query'],
      'product:status': ['catalog:product:status', 'catalog:product:query'],
      'product:export': ['catalog:product:export', 'catalog:product:query'],
      'product:cost:view': ['catalog:product:cost:view', 'catalog:product:query'],
      'inventory:stock:adjust': ['inventory:stock:adjust'],
      'order:export': ['trade:order:export', 'trade:order:query'],
      'order:pii:view': ['trade:order:pii:view', 'trade:order:query'],
      'order:ship': ['trade:order:ship'],
      'order:cancel': ['trade:order:cancel'],
      'order:remark': ['trade:order:remark'],
      'order:refund': ['trade:order:refund'],
      'order:audit': ['trade:order:audit'],
      'system:config:save': ['system:config:save', 'system:config:view']
    };
    return (aliases[permission] || []).some((candidate) => permissions.includes(candidate));
  }, [currentUser]);

  const requirePermission = useCallback((permission: string, actionLabel = '该操作'): boolean => {
    if (hasPermission(permission)) return true;
    showToast(`暂无${actionLabel}权限，请联系管理员`, 'warning');
    return false;
  }, [hasPermission]);

  /** 仅允许跳转到后端菜单返回的页面，避免手工篡改状态访问未授权路由。 */
  const setCurrentTab = useCallback((tab: NavigationTab) => {
    if (authorizedMenuItems.length > 0 && !containsMenuTab(authorizedMenuItems, tab)) {
      showToast('暂无该页面访问权限，请联系管理员', 'warning');
      return;
    }
    setCurrentTabState(tab);
  }, [authorizedMenuItems, showToast]);

  const hydrateIdentityMetadata = async (authorizedMenus: BackendMenu[]) => {
    const [usersResult, memberResult, deptsResult, rolesResult, menusResult, rulesResult] = await Promise.allSettled([
      listSystemUsers(),
      listMemberUsers(),
      listDepartments(),
      listSystemRoles(),
      listSystemMenus(),
      listDataRules()
    ]);
    const backendMenus = menusResult.status === 'fulfilled' ? menusResult.value : authorizedMenus;
    const frontendMenus = backendMenusToFrontend(backendMenus);
    const authorizedTree = backendMenusToTree(authorizedMenus);
    setMenuItems(frontendMenus);
    setAuthorizedMenuItems(authorizedTree);

    const backendRoles = rolesResult.status === 'fulfilled' ? rolesResult.value : [];
    const permissionMap = new Map<number, string[]>();
    if (backendRoles.length && backendMenus.length) {
      const menuMap = new Map(backendMenus.map((menu) => [menu.id, menu.permissionCode]).filter((entry): entry is [number, string] => Boolean(entry[1])));
      const rolePermissions = await Promise.all(backendRoles.map(async (role) => {
        try {
          const menuIds = await listRoleMenuIds(role.id);
          return [role.id, menuIds.map((menuId) => menuMap.get(menuId)).filter((key): key is string => Boolean(key))] as const;
        } catch {
          return [role.id, [] as string[]] as const;
        }
      }));
      rolePermissions.forEach(([roleId, permissions]) => permissionMap.set(roleId, permissions));
    }
    const frontendRoles = backendRolesToFrontend(backendRoles, permissionMap);
    // 即使接口返回空数组，也必须覆盖当前状态，不能保留旧的本地数据。
    setRoles(frontendRoles);

    if (deptsResult.status === 'fulfilled') {
      setDepartmentsState(backendDepartmentsToFrontend(deptsResult.value));
    } else {
      setDepartmentsState([]);
    }
    if (rulesResult.status === 'fulfilled') {
      const ruleRoleMap = new Map<number, { id: string; name: string }>();
      await Promise.all(backendRoles.map(async (role) => {
        try {
          const ruleIds = await listRoleDataRuleIds(role.id);
          ruleIds.forEach((ruleId) => ruleRoleMap.set(ruleId, { id: String(role.id), name: role.roleName }));
        } catch {
          // 当前账号没有角色数据权限查询权限时，保留规则本身但不绑定角色。
        }
      }));
      setDataRules(backendDataRulesToFrontend(rulesResult.value, ruleRoleMap));
    } else {
      setDataRules([]);
    }
    if (usersResult.status === 'fulfilled') {
      const deptNames = new Map((deptsResult.status === 'fulfilled' ? deptsResult.value : []).map((dept) => [dept.id, dept.deptName]));
      const roleNameMap = new Map(backendRoles.map((role) => [role.id, role.roleName]));
      const roleAssignments = new Map<number, { ids: string[]; names: string[] }>();
      await Promise.all(usersResult.value.records.map(async (user) => {
        try {
          const roleIds = await listUserRoleIds(user.id);
          roleAssignments.set(user.id, {
            ids: roleIds.map(String),
            names: roleIds.map((roleId) => roleNameMap.get(roleId)).filter((name): name is string => Boolean(name))
          });
        } catch {
          roleAssignments.set(user.id, { ids: [], names: [] });
        }
      }));
      setSystemUsers(backendUsersToFrontend(usersResult.value.records, deptNames, roleAssignments));
      const userCountByRole = new Map<string, number>();
      roleAssignments.forEach((assignment) => assignment.ids.forEach((roleId) => {
        userCountByRole.set(roleId, (userCountByRole.get(roleId) || 0) + 1);
      }));
      setRoles((previous) => previous.map((role) => ({ ...role, userCount: userCountByRole.get(role.id) || 0 })));
    } else {
      setSystemUsers([]);
    }
    if (memberResult.status === 'fulfilled') {
      // 会员接口成功后以服务端事实替换当前快照，不混入本地演示会员。
      setUsers(backendMembersToFrontend(memberResult.value.records));
    } else {
      // 查询失败时清空列表，避免把过期或演示数据误当成线上会员。
      setUsers([]);
    }
    // 标签字典与会员列表并行加载，标签接口失败不影响会员列表展示。
    await refreshMemberTags();
  };

  /**
   * 拉取全部上架商品。
   *
   * 列表接口单页上限 200，而管理端商品页是「全量本地筛选 + 前端分页」，
   * 只取第一页会让筛选结果天然缺一截，因此按页取完（最多 10 页兜底）。
   */
  const listAllCatalogProducts = async (): Promise<BackendCatalogProduct[]> => {
    const pageSize = 200;
    const firstPage = await listCatalogProducts({ current: 1, size: pageSize });
    const records = [...firstPage.records];
    const totalPages = Math.min(Math.ceil((firstPage.total || records.length) / pageSize), 10);
    for (let page = 2; page <= totalPages; page += 1) {
      const nextPage = await listCatalogProducts({ current: page, size: pageSize });
      records.push(...nextPage.records);
    }
    return records;
  };

  const hydrateCatalogMetadata = async () => {
    const [productsResult, categoriesResult] = await Promise.allSettled([listAllCatalogProducts(), listCatalogCategories()]);
    if (categoriesResult.status === 'fulfilled') {
      setCatalogCategories(backendCategoriesToOptions(categoriesResult.value));
    }
    if (productsResult.status === 'fulfilled') {
      const categoryMap = backendCategoriesToMap(categoriesResult.status === 'fulfilled' ? categoriesResult.value : []);
      setProducts(backendProductsToFrontend(productsResult.value, categoryMap));
    } else {
      // 商品接口不可用时清空列表，避免继续展示本地演示商品。
      setProducts([]);
    }
  };

  const hydrateSystemDictionaries = async () => {
    try {
      setLogisticsCarriers(await listLogisticsCarriers());
    } catch (error) {
      // 字典接口失败时清空候选项，禁止页面继续使用本地承运商演示值。
      setLogisticsCarriers([]);
      console.warn('物流承运商字典接口暂不可用，已清空候选项', error);
    }
  };

  const hydrateTradeMetadata = async () => {
    try {
      // 订单接口成功时以后端数据为准，只有请求失败才保留当前演示数据。
      const ordersResult = await listTradeOrders({ size: 200 });
      setOrders(backendOrdersToFrontend(ordersResult.records || []));
    } catch (error) {
      // 订单接口不可用时清空列表，避免继续展示本地演示订单。
      setOrders([]);
      console.warn('订单接口暂不可用，已清空当前订单数据', error);
    }
  };

  /**
   * 重新统计运营未读通知数。
   *
   * 通知中心的数据源是会员站内通知投递记录，未读数由服务端统计，
   * 前端只缓存这个数字供顶栏铃铛角标使用，列表本身由抽屉按需分页拉取。
   */
  const refreshNotificationUnreadCount = async () => {
    try {
      const summary = await getContentNotificationSummary();
      setNotificationUnreadCount(Number(summary?.adminUnread ?? 0));
    } catch (error) {
      // 统计接口不可用时按无未读处理，避免角标常亮却点不出内容。
      setNotificationUnreadCount(0);
      console.warn('通知统计接口暂不可用，已按无未读处理', error);
    }
  };

  // 工作台待办取自真实的售后与库存接口；顶栏通知另走后端通知中心，这里顺带刷新未读数。
  const hydrateDashboardTasks = async () => {
    const [afterSalesResult, warningsResult] = await Promise.allSettled([
      listTradeAfterSales({ status: 10, size: 200 }),
      listInventoryWarnings()
    ]);
    const nextTodos: TodoItem[] = [];

    if (afterSalesResult.status === 'fulfilled') {
      const count = Number(afterSalesResult.value.total ?? afterSalesResult.value.records?.length ?? 0);
      if (count > 0) {
        nextTodos.push({
          id: 'todo-after-sale-pending',
          title: `${count}个售后申请待审核`,
          subtitle: '来自服务端售后单数据，请及时处理审核',
          type: 'refund',
          count,
          urgent: true,
          linkTab: 'orders'
        });
      }
    }

    if (warningsResult.status === 'fulfilled') {
      const count = warningsResult.value.length;
      if (count > 0) {
        nextTodos.push({
          id: 'todo-stock-warning',
          title: `${count}个库存低于安全线`,
          subtitle: '来自服务端库存台账，请及时补货',
          type: 'stock_alert',
          count,
          urgent: true,
          linkTab: 'products'
        });
      }
    }

    setTodos(nextTodos);
    await refreshNotificationUnreadCount();
  };

  const loadAdminSession = async () => {
    const token = getAdminToken();
    if (!token) {
      setAuthLoading(false);
      return;
    }
    try {
      const [user, menus] = await Promise.all([getCurrentAdmin(), getAdminMenus()]);
      setCurrentUser(user);
      const menuTree = backendMenusToTree(menus);
      setAuthorizedMenuItems(menuTree);
      setCurrentTabState((previousTab) => containsMenuTab(menuTree, previousTab) ? previousTab : (firstMenuTab(menuTree) || 'dashboard'));
      await hydrateIdentityMetadata(menus);
      await hydrateSystemDictionaries();
      await hydrateCatalogMetadata();
      await hydrateTradeMetadata();
      await hydrateDashboardTasks();
    } catch {
      clearAdminToken();
      setCurrentUser(null);
    } finally {
      setAuthLoading(false);
    }
  };

  useEffect(() => {
    void loadAdminSession();
  }, []);

  useEffect(() => {
    if (!currentUser || currentTab !== 'orders') return undefined;

    // 进入订单履约页时立即同步，并在停留期间定时刷新，避免后端重启后页面继续使用旧内存数据。
    void hydrateTradeMetadata();
    const refreshTimer = window.setInterval(() => {
      void hydrateTradeMetadata();
    }, 30_000);
    return () => window.clearInterval(refreshTimer);
  }, [currentTab, currentUser?.userId]);

  useEffect(() => {
    if (!currentUser || currentTab !== 'dashboard') return undefined;

    // 工作台停留期间定时同步待办和通知，确保售后、库存变化能及时反映。
    void hydrateDashboardTasks();
    const refreshTimer = window.setInterval(() => {
      void hydrateDashboardTasks();
    }, 30_000);
    return () => window.clearInterval(refreshTimer);
  }, [currentTab, currentUser?.userId]);

  const login = async (username: string, password: string, captchaId: string, captchaCode: string) => {
    const result = await loginAdmin(username, password, captchaId, captchaCode);
    const menus = await getAdminMenus();
    const menuTree = backendMenusToTree(menus);
    setCurrentUser(result.user);
    setAuthorizedMenuItems(menuTree);
    setCurrentTab(firstMenuTab(menuTree) || 'dashboard');
    await hydrateIdentityMetadata(menus);
    await hydrateSystemDictionaries();
    await hydrateCatalogMetadata();
    await hydrateTradeMetadata();
    await hydrateDashboardTasks();
  };

  const logout = async () => {
    try {
      // 先通知服务端吊销当前 JWT，网络失败仍清理本地会话避免残留登录态。
      await logoutAdmin();
    } catch (error) {
      console.warn('管理员退出接口调用失败，已清理本地会话', error);
    } finally {
      clearAdminToken();
      setCurrentUser(null);
      setMenuItems([]);
      setAuthorizedMenuItems([]);
    }
  };

  const removeToast = (id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  };

  // Product Methods
  const addProduct = async (productData: Omit<Product, 'id' | 'createdAt' | 'salesCount'>): Promise<number | null> => {
    const newProduct: Product = {
      ...productData,
      id: `prod-${Date.now()}`,
      salesCount: 0,
      createdAt: new Date().toISOString().split('T')[0]
    };
    setProducts((prev) => [newProduct, ...prev]);
    showToast(`商品「${newProduct.name}」添加成功`, 'success');
    const request = productToCatalogRequest(newProduct);
    delete request.id;
    try {
      const id = await saveCatalogProductApi(request);
      setProducts((prev) => prev.map((product) => product.id === newProduct.id ? { ...product, id: String(id) } : product));
      return id;
    } catch {
      showToast('商品已加入当前页面，但服务端保存失败', 'warning');
      return null;
    }
  };

  const updateProduct = (id: string, updates: Partial<Product>) => {
    setProducts((prev) =>
      prev.map((p) => (p.id === id ? { ...p, ...updates } : p))
    );
    showToast('商品信息更新成功', 'success');
    const target = products.find((product) => product.id === id);
    if (target && Number.isFinite(Number(id))) {
      void saveCatalogProductApi(productToCatalogRequest({ ...target, ...updates })).catch(() => showToast('商品已更新本地状态，但服务端保存失败', 'warning'));
    }
  };

  const deleteProduct = (id: string) => {
    const target = products.find((p) => p.id === id);
    setProducts((prev) => prev.filter((p) => p.id !== id));
    showToast(`已删除商品 ${target ? target.name : ''}`, 'info');
    if (Number.isFinite(Number(id))) {
      void deleteCatalogProductApi(Number(id)).catch(() => showToast('商品已从当前页面移除，但服务端删除失败', 'warning'));
    }
  };

  const toggleProductStatus = (id: string) => {
    const target = products.find((product) => product.id === id);
    const nextStatus = target?.status === 'active' ? 'inactive' : 'active';
    if (!target) return;
    setProducts((prev) =>
      prev.map((p) => {
        if (p.id === id) {
          return { ...p, status: nextStatus };
        }
        return p;
      })
    );
    // 状态更新函数可能在开发模式被重复执行，提示必须放在函数外避免重复弹窗。
    showToast(`商品状态已变更为「${nextStatus === 'active' ? '上架中' : '已下架'}」`, 'info');
    if (Number.isFinite(Number(id))) {
      void updateCatalogProductStatus(Number(id), nextStatus === 'active' ? 1 : 2)
        .catch(() => {
          setProducts((prev) => prev.map((product) => product.id === id ? { ...product, status: target.status } : product));
          showToast('商品状态更新失败，已恢复原状态', 'warning');
        });
    }
  };

  const batchUpdateProductStatus = (ids: string[], status: ProductStatus) => {
    setProducts((prev) =>
      prev.map((p) => (ids.includes(p.id) ? { ...p, status } : p))
    );
    showToast(`已批量${status === 'active' ? '上架' : '下架'} ${ids.length} 件商品`, 'success');
    const updates = ids.map((id) => products.find((product) => product.id === id))
      .filter((product): product is Product => Boolean(product) && Number.isFinite(Number(product.id)))
      .map((product) => updateCatalogProductStatus(Number(product.id), status === 'active' ? 1 : 2));
    void Promise.allSettled(updates).then((results) => {
      if (results.some((result) => result.status === 'rejected')) {
        void hydrateCatalogMetadata();
        showToast('部分商品状态更新失败，已重新同步服务端数据', 'warning');
      }
    });
  };

  const batchDeleteProducts = (ids: string[]) => {
    setProducts((prev) => prev.filter((p) => !ids.includes(p.id)));
    showToast(`已批量删除 ${ids.length} 件商品`, 'info');
    ids.map(Number).filter((id) => Number.isFinite(id)).forEach((id) => {
      void deleteCatalogProductApi(id).catch(() => showToast(`商品 ${id} 服务端删除失败`, 'warning'));
    });
  };

  const batchUpdateProductCategory = (ids: string[], category: ProductCategory, categoryName: string) => {
    const categoryId = catalogCategories.find((item) => item.code === category)?.id;
    setProducts((prev) =>
      prev.map((p) => (ids.includes(p.id) ? { ...p, category, categoryName, categoryId } : p))
    );
    showToast(`已将 ${ids.length} 件商品批量转移至「${categoryName}」`, 'success');
    ids.map((id) => products.find((product) => product.id === id)).filter((product): product is Product => Boolean(product) && Number.isFinite(Number(product.id))).forEach((product) => {
      void saveCatalogProductApi(productToCatalogRequest({ ...product, category, categoryName, categoryId })).catch(() => showToast(`商品「${product.name}」服务端类目更新失败`, 'warning'));
    });
  };

  const adjustProductStock = (id: string, newStock: number, reason?: string) => {
    const target = products.find((product) => product.id === id);
    setProducts((prev) =>
      prev.map((p) => {
        if (p.id === id) {
          const oldStock = p.stock;
          const diff = newStock - oldStock;
          const targetStatus = newStock === 0 ? 'inactive' : p.status;
          showToast(`库存调整成功：${p.name} (${oldStock} -> ${newStock}件${diff !== 0 ? `, 变动 ${diff > 0 ? `+${diff}` : diff}` : ''}${reason ? ` / ${reason}` : ''})`, 'success');
          return { ...p, stock: newStock, status: targetStatus };
        }
        return p;
      })
    );
    if (target && Number.isFinite(Number(id))) {
      const nextStatus = newStock === 0 ? 'inactive' : target.status;
      void saveCatalogProductApi(productToCatalogRequest({ ...target, stock: newStock, status: nextStatus })).catch(() => {
        showToast('库存已更新本地状态，但服务端保存失败', 'warning');
      });
    }
  };

  // Order Methods
  const updateOrderStatus = (id: string, status: Order['status'], trackingNumber?: string, carrier?: string) => {
    setOrders((prev) =>
      prev.map((o) => {
        if (o.id === id) {
          const nowStr = new Date().toISOString().slice(0, 16).replace('T', ' ');
          let newLogistics = o.logisticsSteps ? [...o.logisticsSteps] : [];
          if (status === 'shipped') {
            newLogistics.unshift({
              time: nowStr,
              title: '已发货/待揽收',
              desc: `${carrier || o.shippingCarrier || '未设置承运商'} 运单号: ${trackingNumber || o.trackingNumber || '未填写'} 已生成并出库`,
              status: 'current'
            });
          }
          return {
            ...o,
            status,
            ...(trackingNumber ? { trackingNumber } : {}),
            ...(carrier ? { shippingCarrier: carrier } : {}),
            logisticsSteps: newLogistics
          };
        }
        return o;
      })
    );
    showToast('订单状态已更新', 'success');
    const numericId = Number(id);
    if (status === 'shipped' && Number.isFinite(numericId) && trackingNumber && carrier) {
      void shipTradeOrder(numericId, { logisticsCompany: carrier, trackingNo: trackingNumber }).catch(() => {
        showToast('订单状态已更新本地状态，但服务端发货失败', 'warning');
        void hydrateTradeMetadata();
      });
    }
  };

  const syncOrderLogistics = async (id: string) => {
    if (!requirePermission('order:ship', '同步物流')) return;
    const numericId = Number(id);
    if (!Number.isFinite(numericId)) {
      showToast('当前订单不是服务端订单，无法同步真实物流', 'warning');
      return;
    }
    try {
      const result = await syncTradeOrderLogistics(numericId);
      await hydrateTradeMetadata();
      showToast(`物流同步完成：${result.syncedCount} 个节点`, 'success');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '物流同步失败，请稍后重试', 'error');
    }
  };

  const auditOrder = async (id: string, approved: boolean, remark?: string) => {
    const numericId = Number(id);
    const previous = orders.find((order) => order.id === id);
    if (!Number.isFinite(numericId) || !previous || previous.version === undefined) {
      showToast('当前订单缺少服务端版本，无法提交审核', 'warning');
      return;
    }
    // 先更新本地状态给出即时反馈，接口失败时按快照恢复。
    setOrders((prev) => prev.map((order) => order.id === id ? {
      ...order,
      auditStatus: approved ? 'approved' : 'rejected',
      auditRemark: remark || (approved ? '审核通过' : '审核驳回')
    } : order));
    try {
      const saved = await auditTradeOrder(numericId, approved, { remark, version: previous.version });
      setOrders((prev) => prev.map((order) => order.id === id ? {
        ...order,
        auditStatus: saved.auditStatus === 20 ? 'approved' : saved.auditStatus === 30 ? 'rejected' : 'pending',
        auditRemark: saved.auditRemark || undefined,
        version: saved.version
      } : order));
      showToast(approved ? '订单审核已通过' : '订单审核已驳回', approved ? 'success' : 'warning');
    } catch (error) {
      setOrders((prev) => prev.map((order) => order.id === id ? previous : order));
      showToast(error instanceof Error ? error.message : '订单审核失败，已恢复原状态', 'error');
    }
  };

  const cancelOrder = (id: string) => {
    setOrders((prev) =>
      prev.map((o) => (o.id === id ? { ...o, status: 'cancelled' } : o))
    );
    showToast('订单已取消', 'warning');
    const numericId = Number(id);
    if (Number.isFinite(numericId)) {
      void cancelTradeOrder(numericId).catch(() => {
        showToast('订单已取消本地状态，但服务端取消失败', 'warning');
        void hydrateTradeMetadata();
      });
    }
  };

  const batchShipOrders = (shipments: { orderId: string; carrier: string; trackingNumber: string }[]) => {
    const shipmentMap = new Map(shipments.map((s) => [s.orderId, s]));
    setOrders((prev) =>
      prev.map((o) => {
        if (shipmentMap.has(o.id)) {
          const s = shipmentMap.get(o.id)!;
          return {
            ...o,
            status: 'shipped',
            shippingCarrier: s.carrier,
            trackingNumber: s.trackingNumber
          };
        }
        return o;
      })
    );
    showToast(`成功批量发货 ${shipments.length} 笔订单`, 'success');
    const requests = shipments
      .map((shipment) => ({ ...shipment, numericId: Number(shipment.orderId) }))
      .filter((shipment) => Number.isFinite(shipment.numericId))
    if (requests.length > 0) {
      void batchShipTradeOrders(requests.map((shipment) => ({
        orderId: shipment.numericId,
        logisticsCompany: shipment.carrier,
        trackingNo: shipment.trackingNumber
      }))).then(() => {
        showToast(`服务端已批量发货 ${requests.length} 笔订单`, 'success');
        void hydrateTradeMetadata();
      }).catch(() => {
        showToast('批量发货失败，整批订单已回滚', 'error');
        void hydrateTradeMetadata();
      });
    }
  };

  const batchCancelOrders = (ids: string[], reason?: string) => {
    setOrders((prev) =>
      prev.map((o) => (ids.includes(o.id) ? { ...o, status: 'cancelled', notes: reason || '批量取消' } : o))
    );
    showToast(`已批量取消 ${ids.length} 笔订单`, 'warning');
    const requests = ids
      .map((id) => Number(id))
      .filter((id) => Number.isFinite(id))
      .map((id) => cancelTradeOrder(id, reason));
    if (requests.length > 0) {
      void Promise.allSettled(requests).then((results) => {
        if (results.some((result) => result.status === 'rejected')) {
          showToast('部分订单服务端取消失败，已重新同步订单', 'warning');
        }
        void hydrateTradeMetadata();
      });
    }
  };

  const updateOrderRemark = (id: string, sellerNote: string, flagColor?: Order['flagColor'] | null) => {
    const previousOrder = orders.find((order) => order.id === id);
    const optimisticOrder = previousOrder
      ? {
          ...previousOrder,
          sellerNote,
          // undefined 表示调用方未修改标旗，null 表示明确清除标旗。
          flagColor: flagColor === undefined ? previousOrder.flagColor : flagColor || undefined
        }
      : undefined;
    setOrders((prev) => prev.map((o) => o.id === id && optimisticOrder ? optimisticOrder : o));
    const numericId = Number(id);
    if (!Number.isFinite(numericId) || !previousOrder || previousOrder.version === undefined) {
      showToast('卖家备注及标旗已更新', 'success');
      return;
    }
    const requestFlagColor = flagColor === undefined ? previousOrder.flagColor || null : flagColor;
    void updateTradeOrderRemark(numericId, {
      sellerRemark: sellerNote,
      flagColor: requestFlagColor,
      version: previousOrder.version
    }).then((saved) => {
      // 以后端规范化后的值和新版本覆盖本地快照，保证下一次写入携带最新版本。
      setOrders((prev) => prev.map((o) => o.id === id ? {
        ...o,
        sellerNote: saved.sellerRemark || undefined,
        flagColor: saved.flagColor || undefined,
        version: saved.version
      } : o));
      showToast('卖家备注及标旗已更新', 'success');
    }).catch(() => {
      // 仅回滚仍处于本次乐观状态的订单，避免覆盖用户随后发起的新编辑。
      setOrders((prev) => prev.map((o) => {
        if (o.id !== id || !previousOrder || !optimisticOrder) return o;
        const sameOptimisticState = o.sellerNote === optimisticOrder.sellerNote
          && o.flagColor === optimisticOrder.flagColor
          && o.version === optimisticOrder.version;
        return sameOptimisticState ? previousOrder : o;
      }));
      showToast('备注及标旗保存失败，已恢复原值', 'warning');
      void hydrateTradeMetadata();
    });
  };

  const processOrderRefund = async (id: string, refundAmount: number, refundReason: string): Promise<void> => {
    const previousOrder = orders.find((order) => order.id === id);
    if (!previousOrder) {
      showToast('未找到待退款订单，请刷新后重试', 'error');
      return;
    }
    // 退款请求采用乐观更新，但必须保留快照，服务端失败时立即恢复，避免页面长期停留在“退款中”。
    setOrders((prev) =>
      prev.map((o) => {
        if (o.id === id) {
          return {
            ...o,
            status: 'refunding',
            refundAmount,
            refundReason,
            refundStatus: 'pending'
          };
        }
        return o;
      })
    );
    const numericId = Number(id);
    if (Number.isFinite(numericId)) {
      try {
        await refundTradeOrder(numericId, refundAmount, refundReason);
        // 以服务端订单状态覆盖本地快照，确保退款单号/状态等字段及时同步。
        await hydrateTradeMetadata();
        showToast(`退款申请已提交，等待渠道确认 ¥${refundAmount.toFixed(2)}`, 'success');
      } catch (error) {
        setOrders((prev) => prev.map((order) => order.id === id ? previousOrder : order));
        showToast(error instanceof Error ? error.message : '退款提交失败，已恢复原订单状态', 'error');
        throw error;
      }
    } else {
      showToast(`退款申请已提交，等待渠道确认 ¥${refundAmount.toFixed(2)}`, 'success');
    }
  };

  // User Methods
  const addUser = async (userData: Omit<User, 'id' | 'registeredAt' | 'totalSpent' | 'orderCount' | 'lastActive'>) => {
    try {
      // 新增会员统一写入服务端，避免刷新页面后回退到本地演示数据。
      const record = await createMemberUser({
        nickname: userData.name,
        phone: userData.phone,
        email: userData.email,
        memberLevel: userData.tier,
        status: userData.status === 'active' ? 1 : 0,
        avatarUrl: userData.avatar,
        remark: userData.notes,
      });
      const synced = backendMembersToFrontend([record])[0];
      setUsers((prev) => [synced, ...prev]);
      if (userData.tags?.length && Number.isFinite(Number(record.id))) {
        try {
          const tagged = await updateMemberTags(Number(record.id), userData.tags);
          const taggedUser = backendMembersToFrontend([tagged])[0];
          setUsers((prev) => prev.map((user) => user.id === synced.id ? taggedUser : user));
          void refreshMemberTags();
        } catch {
          // 会员已成功创建，标签失败仅提示并保留创建结果，便于稍后在画像面板重试。
          showToast('会员已创建，但默认标签保存失败，请稍后补充', 'warning');
        }
      }
      showToast(`会员 ${synced.name} 已成功录入`, 'success');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '会员创建失败，请稍后重试', 'warning');
      throw error;
    }
  };

  const updateUserStatus = (id: string, status: User['status']) => {
    const previousStatus = users.find((user) => user.id === id)?.status;
    setUsers((prev) =>
      prev.map((u) => {
        if (u.id === id) {
          showToast(`会员账户状态已变更为「${status === 'active' ? '正常' : '已冻结'}」`, 'info');
          return { ...u, status };
        }
        return u;
      })
    );
    const numericId = Number(id);
    if (Number.isFinite(numericId) && previousStatus && previousStatus !== status) {
      void updateMemberStatus(numericId, status === 'active' ? 1 : 0).catch(() => {
        setUsers((prev) => prev.map((user) => user.id === id ? { ...user, status: previousStatus } : user));
        showToast('会员状态已回滚，服务端保存失败', 'warning');
      });
    }
  };

  const updateUser = (id: string, updates: Partial<User>) => {
    const previous = users.find((user) => user.id === id);
    setUsers((prev) =>
      prev.map((u) => (u.id === id ? { ...u, ...updates } : u))
    );
    showToast('会员资料更新成功', 'success');
    const numericId = Number(id);
    if (!previous || !Number.isFinite(numericId)) return;
    void updateMemberProfile(numericId, {
      nickname: updates.name,
      phone: updates.phone,
      email: updates.email,
      memberLevel: updates.tier,
      avatarUrl: updates.avatar,
      remark: updates.notes,
    }).then((record) => {
      const synced = backendMembersToFrontend([record])[0];
      setUsers((prev) => prev.map((user) => user.id === id
        ? { ...user, ...synced, tags: user.tags, totalSpent: user.totalSpent, orderCount: user.orderCount }
        : user));
    }).catch((error) => {
      setUsers((prev) => prev.map((user) => user.id === id ? previous : user));
      showToast(error instanceof Error ? error.message : '会员资料保存失败，已回滚', 'warning');
    });
  };

  const batchUpdateUserStatus = (ids: string[], status: UserStatus) => {
    const previousStatuses = new Map(
      users.filter((user) => ids.includes(user.id)).map((user) => [user.id, user.status])
    );
    setUsers((prev) =>
      prev.map((u) => (ids.includes(u.id) ? { ...u, status } : u))
    );
    const numericIds = ids
      .map((id) => Number(id))
      .filter((id) => Number.isFinite(id));
    if (numericIds.length === 0) {
      showToast('当前选中的会员没有可提交的服务端编号', 'warning');
      return;
    }
    showToast(`正在批量${status === 'active' ? '解冻' : '冻结'} ${numericIds.length} 位会员账号`, 'info');
    void Promise.allSettled(
      numericIds.map((id) => updateMemberStatus(id, status === 'active' ? 1 : 0))
    ).then((results) => {
      const failedIds = results
        .map((result, index) => result.status === 'rejected' ? String(numericIds[index]) : null)
        .filter((id): id is string => Boolean(id));
      if (failedIds.length > 0) {
        // 仅回滚服务端失败的记录，成功项保留乐观更新结果，避免批量操作整体误回滚。
        setUsers((prev) => prev.map((user) => {
          const previous = previousStatuses.get(user.id);
          return failedIds.includes(user.id) && previous ? { ...user, status: previous } : user;
        }));
        showToast(`${failedIds.length} 位会员状态保存失败，已回滚失败项`, 'warning');
        return;
      }
      showToast(`已批量${status === 'active' ? '解冻' : '冻结'} ${numericIds.length} 位会员账号`, 'success');
    });
  };

  const adjustUserBalanceAndPoints = (id: string, pointsDelta: number, balanceDelta: number, note: string) => {
    const previous = users.find((user) => user.id === id);
    setUsers((prev) =>
      prev.map((u) => {
        if (u.id === id) {
          const currentBal = u.balance || 0;
          const currentPts = u.points || 0;
          const nextBal = Math.max(0, currentBal + balanceDelta);
          const nextPts = Math.max(0, currentPts + pointsDelta);
          showToast(`账户调账成功: ${u.name} (余额 ${balanceDelta >= 0 ? `+${balanceDelta}` : balanceDelta}, 积分 ${pointsDelta >= 0 ? `+${pointsDelta}` : pointsDelta} / 备注: ${note})`, 'success');
          return { ...u, balance: nextBal, points: nextPts };
        }
        return u;
      })
    );
    const numericId = Number(id);
    if (!previous || !Number.isFinite(numericId)) return;
    void adjustMemberAssets(numericId, pointsDelta, balanceDelta, note).then((record) => {
      const synced = backendMembersToFrontend([record])[0];
      setUsers((prev) => prev.map((user) => user.id === id
        ? { ...user, balance: synced.balance, points: synced.points }
        : user));
    }).catch((error) => {
      setUsers((prev) => prev.map((user) => user.id === id ? previous : user));
      showToast(error instanceof Error ? error.message : '会员调账失败，已回滚', 'warning');
    });
  };

  const updateUserTags = (id: string, tags: string[]) => {
    const previous = users.find((user) => user.id === id);
    setUsers((prev) =>
      prev.map((u) => (u.id === id ? { ...u, tags } : u))
    );
    showToast('用户画像标签更新成功', 'success');
    const numericId = Number(id);
    if (!previous || !Number.isFinite(numericId)) return;
    void updateMemberTags(numericId, tags).then((record) => {
      const synced = backendMembersToFrontend([record])[0];
      setUsers((prev) => prev.map((user) => user.id === id ? synced : user));
      // 会员可以现场新建标签，保存后同步字典，新增的标签才会出现在筛选栏里。
      void refreshMemberTags();
    }).catch((error) => {
      setUsers((prev) => prev.map((user) => user.id === id ? previous : user));
      showToast(error instanceof Error ? error.message : '会员标签保存失败，已回滚', 'warning');
    });
  };

  // RBAC Role Methods
  const addRole = (roleData: Omit<Role, 'id' | 'createdAt' | 'userCount'>) => {
    const newRole: Role = {
      ...roleData,
      id: `role-${Date.now()}`,
      userCount: 0,
      createdAt: new Date().toISOString().split('T')[0]
    };
    setRoles((prev) => [...prev, newRole]);
    showToast(`正在创建角色「${newRole.roleName}」`, 'info');
    void saveSystemRole({
      roleKey: newRole.roleKey.toUpperCase(),
      roleName: newRole.roleName,
      roleSort: newRole.roleSort,
      status: newRole.status === 'active' ? 1 : 0,
      dataScope: roleScopeToBackend(newRole.dataScope),
      description: newRole.description
    }).then((id) => {
      setRoles((prev) => prev.map((role) => role.id === newRole.id ? { ...role, id: String(id) } : role));
      showToast(`角色「${newRole.roleName}」创建成功`, 'success');
    }).catch(() => {
      setRoles((prev) => prev.filter((role) => role.id !== newRole.id));
      showToast('角色创建失败，已回滚本地状态', 'error');
    });
  };

  const updateRole = (id: string, updates: Partial<Role>) => {
    setRoles((prev) =>
      prev.map((r) => (r.id === id ? { ...r, ...updates } : r))
    );
    showToast('角色信息已更新', 'success');
    const target = roles.find((role) => role.id === id);
    if (target && Number.isFinite(Number(id))) {
      const merged = { ...target, ...updates };
      void saveSystemRole({
        id: Number(id),
        roleKey: merged.roleKey.toUpperCase(),
        roleName: merged.roleName,
        roleSort: merged.roleSort,
        status: merged.status === 'active' ? 1 : 0,
        dataScope: roleScopeToBackend(merged.dataScope),
        description: merged.description
      }).catch(() => showToast('角色已更新本地状态，但服务端保存失败', 'warning'));
    }
  };

  const deleteRole = (id: string) => {
    const target = roles.find((r) => r.id === id);
    if (target?.roleKey === 'super_admin') {
      showToast('超级管理员角色受系统保护，不可删除', 'error');
      return;
    }
    setRoles((prev) => prev.filter((r) => r.id !== id));
    showToast(`已删除角色 ${target?.roleName || ''}`, 'info');
    if (Number.isFinite(Number(id))) {
      void deleteSystemRole(Number(id)).catch(() => showToast('角色已从当前页面移除，但服务端删除失败', 'warning'));
    }
  };

  const updateRolePermissions = (roleId: string, permissionKeys: string[]) => {
    setRoles((prev) =>
      prev.map((r) => (r.id === roleId ? { ...r, permissionKeys } : r))
    );
    const target = roles.find((r) => r.id === roleId);
    showToast(`角色「${target?.roleName || ''}」功能授权已保存`, 'success');
    const permissionSet = new Set(permissionKeys);
    const menuIds = menuItemsFlat(menuItems)
      .filter((menu) => permissionSet.has('*:*:*') || (menu.permission && permissionSet.has(menu.permission)))
      .map((menu) => Number(menu.id))
      .filter((id) => Number.isFinite(id));
    void replaceRoleMenus(Number(roleId), menuIds).catch(() => {
      showToast('功能授权本地已更新，但服务端保存失败', 'warning');
    });
  };

  const updateRoleDataScope = (roleId: string, dataScope: Role['dataScope'], customDeptIds?: string[]) => {
    setRoles((prev) =>
      prev.map((r) => (r.id === roleId ? { ...r, dataScope, customDeptIds } : r))
    );
    const target = roles.find((r) => r.id === roleId);
    showToast(`角色「${target?.roleName || ''}」数据权限范围已更新`, 'success');
    if (target && Number.isFinite(Number(roleId))) {
      void saveSystemRole({
        id: Number(roleId),
        roleKey: target.roleKey.toUpperCase(),
        roleName: target.roleName,
        roleSort: target.roleSort,
        status: target.status === 'active' ? 1 : 0,
        dataScope: roleScopeToBackend(dataScope),
        description: target.description
      }).catch(() => showToast('数据范围已更新本地状态，但服务端保存失败', 'warning'));
    }
  };

  // RBAC Menu Methods
  const addMenuItem = (itemData: Omit<MenuItem, 'id'>) => {
    const newItem: MenuItem = {
      ...itemData,
      id: `menu-${Date.now()}`
    };

    if (!newItem.parentId) {
      setMenuItems((prev) => [...prev, newItem]);
    } else {
      const addChild = (items: MenuItem[]): MenuItem[] => {
        return items.map((m) => {
          if (m.id === newItem.parentId) {
            return {
              ...m,
              children: [...(m.children || []), newItem]
            };
          }
          if (m.children) {
            return {
              ...m,
              children: addChild(m.children)
            };
          }
          return m;
        });
      };
      setMenuItems((prev) => addChild(prev));
    }
    showToast(`正在添加菜单「${newItem.title}」`, 'info');
    void saveSystemMenu({
      parentId: newItem.parentId ? Number(newItem.parentId) : 0,
      menuName: newItem.title,
      menuType: menuTypeToBackend(newItem.type),
      routePath: newItem.path,
      component: newItem.component,
      icon: newItem.icon,
      permissionCode: newItem.permission,
      sortNo: newItem.sort,
      visible: newItem.visible ? 1 : 0,
      status: newItem.status === 'active' ? 1 : 0
    }).then((id) => {
      const replaceId = (items: MenuItem[]): MenuItem[] => items.map((item) => item.id === newItem.id
        ? { ...item, id: String(id) }
        : { ...item, children: item.children ? replaceId(item.children) : undefined });
      setMenuItems((prev) => replaceId(prev));
      showToast(`菜单「${newItem.title}」已添加`, 'success');
    }).catch(() => {
      const removeItem = (items: MenuItem[]): MenuItem[] => items
        .filter((item) => item.id !== newItem.id)
        .map((item) => ({ ...item, children: item.children ? removeItem(item.children) : undefined }));
      setMenuItems((prev) => removeItem(prev));
      showToast('菜单创建失败，已回滚本地状态', 'error');
    });
  };

  const updateMenuItem = (id: string, updates: Partial<MenuItem>) => {
    const updateRecursive = (items: MenuItem[]): MenuItem[] => {
      return items.map((m) => {
        if (m.id === id) {
          return { ...m, ...updates };
        }
        if (m.children) {
          return { ...m, children: updateRecursive(m.children) };
        }
        return m;
      });
    };
    setMenuItems((prev) => updateRecursive(prev));
    setAuthorizedMenuItems((prev) => updateRecursive(prev));
    showToast('菜单项配置已更新', 'success');
    const target = menuItemsFlat(menuItems).find((item) => item.id === id);
    if (target && Number.isFinite(Number(id))) {
      const merged = { ...target, ...updates };
      void saveSystemMenu({
        id: Number(id),
        parentId: merged.parentId ? Number(merged.parentId) : 0,
        menuName: merged.title,
        menuType: menuTypeToBackend(merged.type),
        routePath: merged.path,
        component: merged.component,
        icon: merged.icon,
        permissionCode: merged.permission,
        sortNo: merged.sort,
        visible: merged.visible ? 1 : 0,
        status: merged.status === 'active' ? 1 : 0
      }).catch(() => showToast('菜单已更新本地状态，但服务端保存失败', 'warning'));
    }
  };

  const deleteMenuItem = (id: string) => {
    const deleteRecursive = (items: MenuItem[]): MenuItem[] => {
      return items
        .filter((m) => m.id !== id)
        .map((m) => ({
          ...m,
          children: m.children ? deleteRecursive(m.children) : undefined
        }));
    };
    setMenuItems((prev) => deleteRecursive(prev));
    setAuthorizedMenuItems((prev) => deleteRecursive(prev));
    showToast('菜单项已移除', 'info');
    if (Number.isFinite(Number(id))) {
      void deleteSystemMenuApi(Number(id)).catch(() => showToast('菜单已从当前页面移除，但服务端删除失败', 'warning'));
    }
  };

  // RBAC System User Methods
  const addSystemUser = (userData: Omit<SystemUser, 'id' | 'createdAt' | 'lastLoginTime' | 'lastLoginIp'> & { initialPassword: string }) => {
    const { initialPassword, ...systemUserData } = userData;
    const newUser: SystemUser = {
      ...systemUserData,
      id: `sys-${Date.now()}`,
      createdAt: new Date().toISOString().split('T')[0],
      lastLoginTime: '尚未登录',
      lastLoginIp: '-'
    };
    setSystemUsers((prev) => [newUser, ...prev]);
    showToast(`正在开通系统用户「${newUser.username}」`, 'info');
    void createSystemUser({
      username: newUser.username,
      password: initialPassword,
      realName: newUser.realName,
      phone: newUser.phone === '-' ? undefined : newUser.phone,
      email: newUser.email === '-' ? undefined : newUser.email,
      avatarUrl: newUser.avatar,
      deptId: Number(newUser.deptId) || undefined,
      status: newUser.status === 'active' ? 1 : 0
    }).then(async (id) => {
      setSystemUsers((prev) => prev.map((user) => user.id === newUser.id ? { ...user, id: String(id) } : user));
      const roleIds = newUser.roles.map(Number).filter((roleId) => Number.isFinite(roleId));
      if (roleIds.length) await replaceUserRoles(id, roleIds);
      showToast(`系统用户「${newUser.username}」已开通`, 'success');
    }).catch(() => {
      setSystemUsers((prev) => prev.filter((user) => user.id !== newUser.id));
      showToast('系统用户创建失败，已回滚本地状态', 'error');
    });
  };

  const updateSystemUser = (id: string, updates: Partial<SystemUser>) => {
    setSystemUsers((prev) =>
      prev.map((u) => (u.id === id ? { ...u, ...updates } : u))
    );
    showToast('系统用户信息更新成功', 'success');
    const target = systemUsers.find((user) => user.id === id);
    if (target && Number.isFinite(Number(id))) {
      const merged = { ...target, ...updates };
      void updateSystemUserApi(Number(id), {
        realName: merged.realName,
        phone: merged.phone === '-' ? undefined : merged.phone,
        email: merged.email === '-' ? undefined : merged.email,
        avatarUrl: merged.avatar,
        deptId: Number(merged.deptId) || undefined,
        status: merged.status === 'active' ? 1 : 0
      }).then(() => {
        // 当前登录账号编辑成功后同步顶部资料，避免必须退出登录才能看到新头像。
        if (currentUser?.userId === Number(id)) {
          setCurrentUser((previous) => previous ? {
            ...previous,
            realName: merged.realName,
            avatarUrl: merged.avatar
          } : previous);
        }
      }).catch(() => {
        setSystemUsers((previous) => previous.map((user) => user.id === id ? target : user));
        showToast('系统用户保存失败，已回滚本地状态', 'warning');
      });
      if (updates.roles) {
        void replaceUserRoles(Number(id), updates.roles.map(Number).filter((roleId) => Number.isFinite(roleId)))
          .catch(() => showToast('角色绑定已更新本地状态，但服务端保存失败', 'warning'));
      }
    }
  };

  /**
   * 修改当前登录管理员本人的资料。
   *
   * <p>走 /api/admin/auth/profile，不需要 system:user:update 权限；成功后同步顶栏头像与姓名，
   * 避免必须退出重登才能看到新头像。</p>
   */
  const updateCurrentProfile = async (payload: {
    nickname?: string;
    phone?: string;
    email?: string;
    avatarUrl?: string;
  }) => {
    const updated = await updateAdminProfile(payload);
    setCurrentUser((previous) => previous ? {
      ...previous,
      realName: updated.realName ?? previous.realName,
      avatarUrl: updated.avatarUrl,
    } : previous);
    showToast('账号资料已更新', 'success');
  };

  const deleteSystemUser = (id: string) => {
    const target = systemUsers.find((u) => u.id === id);
    if (target?.username === 'admin') {
      showToast('超级主管理员账号不可删除', 'error');
      return;
    }
    setSystemUsers((prev) => prev.filter((u) => u.id !== id));
    showToast(`系统用户 ${target?.username || ''} 已删除`, 'info');
    if (Number.isFinite(Number(id))) {
      void deleteSystemUserApi(Number(id)).catch(() => {
        if (target) setSystemUsers((prev) => [target, ...prev]);
        showToast('系统用户删除失败，已恢复本地状态', 'warning');
      });
    }
  };

  const toggleSystemUserStatus = (id: string) => {
    setSystemUsers((prev) =>
      prev.map((u) => {
        if (u.id === id) {
          if (u.username === 'admin') {
            showToast('超级管理员账号不可停用', 'warning');
            return u;
          }
          const nextStatus = u.status === 'active' ? 'inactive' : 'active';
          showToast(`账号「${u.username}」已${nextStatus === 'active' ? '启用' : '禁用'}`, 'info');
          if (Number.isFinite(Number(id))) {
            void updateSystemUserApi(Number(id), { status: nextStatus === 'active' ? 1 : 0 })
              .catch(() => showToast('账号状态已更新本地状态，但服务端保存失败', 'warning'));
          }
          return { ...u, status: nextStatus };
        }
        return u;
      })
    );
  };

  // RBAC Data Rule Methods
  const addDataRule = (ruleData: Omit<DataRule, 'id' | 'updatedAt'>) => {
    const newRule: DataRule = {
      ...ruleData,
      id: `rule-${Date.now()}`,
      updatedAt: new Date().toISOString().split('T')[0]
    };
    setDataRules((prev) => [newRule, ...prev]);
    showToast(`正在添加数据权限规则「${newRule.ruleName}」`, 'info');
    void saveSystemDataRule({
      ruleName: newRule.ruleName,
      moduleKey: newRule.module,
      scopeType: roleScopeToBackend(newRule.scopeType),
      customDeptIds: newRule.customDeptIds?.join(','),
      fieldMasks: newRule.fieldMasks.join(','),
      filterExpression: newRule.filterCondition,
      status: newRule.status === 'active' ? 1 : 0
    }).then(async (id) => {
      setDataRules((prev) => prev.map((rule) => rule.id === newRule.id ? { ...rule, id: String(id) } : rule));
      if (Number.isFinite(Number(newRule.roleId))) {
        const existingRuleIds = await listRoleDataRuleIds(Number(newRule.roleId)).catch(() => []);
        await replaceRoleDataRules(Number(newRule.roleId), Array.from(new Set([...existingRuleIds, id])));
      }
      showToast(`数据权限规则「${newRule.ruleName}」已添加`, 'success');
    }).catch(() => {
      setDataRules((prev) => prev.filter((rule) => rule.id !== newRule.id));
      showToast('数据权限规则创建失败，已回滚本地状态', 'error');
    });
  };

  const updateDataRule = (id: string, updates: Partial<DataRule>) => {
    setDataRules((prev) =>
      prev.map((r) =>
        r.id === id
          ? { ...r, ...updates, updatedAt: new Date().toISOString().split('T')[0] }
          : r
      )
    );
    showToast('数据权限规则已更新', 'success');
    const target = dataRules.find((rule) => rule.id === id);
    if (target && Number.isFinite(Number(id))) {
      const merged = { ...target, ...updates };
      void saveSystemDataRule({
        id: Number(id),
        ruleName: merged.ruleName,
        moduleKey: merged.module,
        scopeType: roleScopeToBackend(merged.scopeType),
        customDeptIds: merged.customDeptIds?.join(','),
        fieldMasks: merged.fieldMasks.join(','),
        filterExpression: merged.filterCondition,
        status: merged.status === 'active' ? 1 : 0
      }).catch(() => showToast('规则已更新本地状态，但服务端保存失败', 'warning'));
    }
  };

  const deleteDataRule = (id: string) => {
    setDataRules((prev) => prev.filter((r) => r.id !== id));
    showToast('数据权限规则已移除', 'info');
    if (Number.isFinite(Number(id))) {
      void deleteSystemDataRule(Number(id)).catch(() => showToast('规则已从当前页面移除，但服务端删除失败', 'warning'));
    }
  };

  const toggleDataRuleStatus = (id: string) => {
    setDataRules((prev) =>
      prev.map((r) => {
        if (r.id === id) {
          const nextStatus = r.status === 'active' ? 'inactive' : 'active';
          showToast(`规则「${r.ruleName}」已${nextStatus === 'active' ? '生效' : '暂停'}`, 'info');
          if (Number.isFinite(Number(id))) {
            void saveSystemDataRule({
              id: Number(id),
              ruleName: r.ruleName,
              moduleKey: r.module,
              scopeType: roleScopeToBackend(r.scopeType),
              customDeptIds: r.customDeptIds?.join(','),
              fieldMasks: r.fieldMasks.join(','),
              filterExpression: r.filterCondition,
              status: nextStatus === 'active' ? 1 : 0
            }).catch(() => showToast('规则状态已更新本地状态，但服务端保存失败', 'warning'));
          }
          return { ...r, status: nextStatus };
        }
        return r;
      })
    );
  };

  // Todo Methods
  const resolveTodo = (id: string) => {
    setTodos((prev) => prev.filter((t) => t.id !== id));
    showToast('待办事项已处理', 'success');
  };

  // Notification Methods
  /** 标记单条通知为运营已读，并同步顶栏未读数。 */
  const markNotificationAsRead = async (id: number) => {
    try {
      await markContentNotificationRead(id);
      await refreshNotificationUnreadCount();
    } catch (error) {
      showToast(error instanceof Error ? error.message : '标记已读失败', 'error');
    }
  };

  /** 把全部未读通知标记为已读，返回本次更新条数。 */
  const markAllNotificationsAsRead = async () => {
    try {
      const updated = await markAllContentNotificationsRead();
      await refreshNotificationUnreadCount();
      showToast(updated > 0 ? `已标记 ${updated} 条通知为已读` : '当前没有未读通知', 'info');
      return Number(updated ?? 0);
    } catch (error) {
      showToast(error instanceof Error ? error.message : '标记已读失败', 'error');
      return 0;
    }
  };

  return (
    <AdminContext.Provider
      value={{
        authLoading,
        isAuthenticated: currentUser !== null,
        currentUser,
        login,
        logout,
        updateCurrentProfile,
        currentTab,
        setCurrentTab,
        hasPermission,
        requirePermission,
        products,
        orders,
        users,
        memberTags,
        refreshMemberTags,
        catalogCategories,
        logisticsCarriers,
        todos,
        notificationUnreadCount,
        refreshNotificationUnreadCount,
        searchQuery,
        setSearchQuery,
        toasts,
        showToast,
        removeToast,
        confirm,
        prompt,
        addProduct,
        updateProduct,
        deleteProduct,
        toggleProductStatus,
        batchUpdateProductStatus,
        batchDeleteProducts,
        batchUpdateProductCategory,
        adjustProductStock,
        updateOrderStatus,
        syncOrderLogistics,
        cancelOrder,
        batchShipOrders,
        batchCancelOrders,
        updateOrderRemark,
        processOrderRefund,
        auditOrder,
        addUser,
        updateUserStatus,
        updateUser,
        batchUpdateUserStatus,
        adjustUserBalanceAndPoints,
        updateUserTags,
        roles,
        addRole,
        updateRole,
        deleteRole,
        updateRolePermissions,
        updateRoleDataScope,
        menuItems,
        authorizedMenuItems,
        addMenuItem,
        updateMenuItem,
        deleteMenuItem,
        systemUsers,
        addSystemUser,
        updateSystemUser,
        deleteSystemUser,
        toggleSystemUserStatus,
        departments,
        dataRules,
        addDataRule,
        updateDataRule,
        deleteDataRule,
        toggleDataRuleStatus,
        resolveTodo,
        markNotificationAsRead,
        markAllNotificationsAsRead,
      }}
    >
      {children}
      <AdminDialog request={dialog} onResolve={resolveDialog} />
    </AdminContext.Provider>
  );
};

export const useAdmin = (): AdminContextType => {
  const context = useContext(AdminContext);
  if (!context) {
    throw new Error('useAdmin must be used within an AdminProvider');
  }
  return context;
};
