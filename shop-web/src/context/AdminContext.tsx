import React, { createContext, useContext, useEffect, useState, ReactNode } from 'react';
import { 
  Product, 
  ProductStatus,
  ProductCategory,
  Order, 
  User, 
  UserStatus,
  TodoItem, 
  NavigationTab, 
  NotificationItem,
  Role,
  MenuItem,
  SystemUser,
  Department,
  DataRule
} from '../types';
import { initialProducts, initialOrders, initialUsers, initialTodos, initialNotifications } from '../data/mockData';
import { 
  initialRoles, 
  initialSystemUsers, 
  initialDepartments, 
  initialDataRules 
} from '../data/rbacMockData';
import {
  AdminUser,
  BackendMenu,
  BackendTradeOrder,
  clearAdminToken,
  getAdminMenus,
  getAdminToken,
  getCurrentAdmin,
  listMemberUsers,
  updateMemberStatus,
  listDepartments,
  listDataRules,
  listRoleMenuIds,
  listRoleDataRuleIds,
  replaceRoleMenus,
  listSystemMenus,
  listSystemRoles,
  listSystemUsers,
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
  listCatalogCategories,
  listCatalogProducts,
  saveCatalogProduct as saveCatalogProductApi,
  listTradeOrders,
  shipTradeOrder,
  cancelTradeOrder,
  updateTradeOrderRemark,
  refundTradeOrder
} from '../api/adminApi';
import { backendMenusToTree, containsMenuTab, firstMenuTab } from '../navigation/menuAdapter';
import { backendDataRulesToFrontend, backendDepartmentsToFrontend, backendMembersToFrontend, backendMenusToFrontend, backendRolesToFrontend, backendUsersToFrontend } from '../navigation/identityAdapter';
import { backendCategoriesToMap, backendCategoriesToOptions, backendProductsToFrontend } from '../navigation/catalogAdapter';

interface Toast {
  id: string;
  message: string;
  type: 'success' | 'info' | 'warning' | 'error';
}

interface AdminContextType {
  authLoading: boolean;
  isAuthenticated: boolean;
  currentUser: AdminUser | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  currentTab: NavigationTab;
  setCurrentTab: (tab: NavigationTab) => void;
  products: Product[];
  orders: Order[];
  users: User[];
  catalogCategories: Array<{ id: number; code: ProductCategory; name: string }>;
  todos: TodoItem[];
  notifications: NotificationItem[];
  searchQuery: string;
  setSearchQuery: (query: string) => void;
  toasts: Toast[];
  showToast: (message: string, type?: 'success' | 'info' | 'warning' | 'error') => void;
  removeToast: (id: string) => void;
  
  // Product actions
  addProduct: (product: Omit<Product, 'id' | 'createdAt' | 'salesCount'>) => void;
  updateProduct: (id: string, updates: Partial<Product>) => void;
  deleteProduct: (id: string) => void;
  toggleProductStatus: (id: string) => void;
  batchUpdateProductStatus: (ids: string[], status: ProductStatus) => void;
  batchDeleteProducts: (ids: string[]) => void;
  batchUpdateProductCategory: (ids: string[], category: ProductCategory, categoryName: string) => void;
  adjustProductStock: (id: string, newStock: number, reason?: string) => void;
  
  // Order actions
  addOrder: (order: Omit<Order, 'id' | 'createdAt'>) => void;
  updateOrderStatus: (id: string, status: Order['status'], trackingNumber?: string, carrier?: string) => void;
  cancelOrder: (id: string) => void;
  batchShipOrders: (shipments: { orderId: string; carrier: string; trackingNumber: string }[]) => void;
  batchCancelOrders: (ids: string[], reason?: string) => void;
  updateOrderRemark: (id: string, sellerNote: string, flagColor?: Order['flagColor']) => void;
  processOrderRefund: (id: string, refundAmount: number, refundReason: string) => void;
  
  // User actions (Customer members)
  addUser: (user: Omit<User, 'id' | 'registeredAt' | 'totalSpent' | 'orderCount' | 'lastActive'>) => void;
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
  addSystemUser: (user: Omit<SystemUser, 'id' | 'createdAt' | 'lastLoginTime' | 'lastLoginIp'>) => void;
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
  markNotificationAsRead: (id: string) => void;
  markAllNotificationsAsRead: () => void;
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

const categoryIdByCode: Record<ProductCategory, number> = {
  electronics: 1,
  clothing: 2,
  home: 3,
  beauty: 4,
  food: 5
};

function productToCatalogRequest(product: Product) {
  return {
    id: Number(product.id),
    // 优先使用后端返回的真实类目ID，兼容旧的本地演示数据再回退到默认映射。
    categoryId: product.categoryId ?? categoryIdByCode[product.category],
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
    createdAt: record.createdAt || '',
    customerName: record.memberName || record.receiverName,
    customerPhone: record.receiverPhone,
    amount: Number(record.paidAmount || record.payableAmount || 0),
    paymentMethod: backendPaymentMethodToFrontend(record.paymentMethod),
    status: backendOrderStatusToFrontend(record.orderStatus),
    items: [],
    shippingAddress: [record.receiverProvince, record.receiverCity, record.receiverDistrict, record.receiverAddress]
      .filter(Boolean)
      .join(' '),
    trackingNumber: record.trackingNo || undefined,
    shippingCarrier: record.logisticsCompany || undefined,
    sellerNote: record.sellerRemark || undefined,
    discountAmount: Number(record.discountAmount || 0),
    freightAmount: Number(record.freightAmount || 0),
    refundStatus: record.orderStatus === 60 ? 'pending' : record.orderStatus === 70 ? 'approved' : 'none'
  }));
}

export const AdminProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [authLoading, setAuthLoading] = useState(true);
  const [currentUser, setCurrentUser] = useState<AdminUser | null>(null);
  const [currentTab, setCurrentTab] = useState<NavigationTab>('dashboard');
  const [products, setProducts] = useState<Product[]>(initialProducts);
  const [orders, setOrders] = useState<Order[]>(initialOrders);
  const [users, setUsers] = useState<User[]>(initialUsers);
  const [catalogCategories, setCatalogCategories] = useState<Array<{ id: number; code: ProductCategory; name: string }>>([]);
  const [todos, setTodos] = useState<TodoItem[]>(initialTodos);
  const [notifications, setNotifications] = useState<NotificationItem[]>(initialNotifications);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [toasts, setToasts] = useState<Toast[]>([]);

  // RBAC state
  const [roles, setRoles] = useState<Role[]>(initialRoles);
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [authorizedMenuItems, setAuthorizedMenuItems] = useState<MenuItem[]>([]);
  const [systemUsers, setSystemUsers] = useState<SystemUser[]>(initialSystemUsers);
  const [departments, setDepartmentsState] = useState<Department[]>(initialDepartments);
  const [dataRules, setDataRules] = useState<DataRule[]>(initialDataRules);

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
    if (frontendRoles.length) setRoles(frontendRoles);

    if (deptsResult.status === 'fulfilled') {
      setDepartmentsState(backendDepartmentsToFrontend(deptsResult.value));
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
    }
    if (memberResult.status === 'fulfilled') {
      // 会员接口成功后以服务端事实替换本地演示数据，接口失败则保留演示数据。
      setUsers(backendMembersToFrontend(memberResult.value.records));
    }
  };

  const hydrateCatalogMetadata = async () => {
    const [productsResult, categoriesResult] = await Promise.allSettled([listCatalogProducts(), listCatalogCategories()]);
    if (categoriesResult.status === 'fulfilled') {
      setCatalogCategories(backendCategoriesToOptions(categoriesResult.value));
    }
    if (productsResult.status === 'fulfilled') {
      const categoryMap = backendCategoriesToMap(categoriesResult.status === 'fulfilled' ? categoriesResult.value : []);
      setProducts(backendProductsToFrontend(productsResult.value.records, categoryMap));
    }
  };

  const hydrateTradeMetadata = async () => {
    try {
      // 订单接口成功时以后端数据为准，只有请求失败才保留当前演示数据。
      const ordersResult = await listTradeOrders({ size: 200 });
      setOrders(backendOrdersToFrontend(ordersResult.records || []));
    } catch (error) {
      console.warn('订单接口暂不可用，继续使用当前订单数据', error);
    }
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
      setCurrentTab((previousTab) => containsMenuTab(menuTree, previousTab) ? previousTab : (firstMenuTab(menuTree) || 'dashboard'));
      await hydrateIdentityMetadata(menus);
      await hydrateCatalogMetadata();
      await hydrateTradeMetadata();
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

  const login = async (username: string, password: string) => {
    const result = await loginAdmin(username, password);
    const menus = await getAdminMenus();
    const menuTree = backendMenusToTree(menus);
    setCurrentUser(result.user);
    setAuthorizedMenuItems(menuTree);
    setCurrentTab(firstMenuTab(menuTree) || 'dashboard');
    await hydrateIdentityMetadata(menus);
    await hydrateCatalogMetadata();
    await hydrateTradeMetadata();
  };

  const logout = () => {
    clearAdminToken();
    setCurrentUser(null);
    setMenuItems([]);
    setAuthorizedMenuItems([]);
  };

  const showToast = (message: string, type: 'success' | 'info' | 'warning' | 'error' = 'success') => {
    const id = Date.now().toString() + Math.random().toString(36).substring(2, 5);
    setToasts((prev) => [...prev, { id, message, type }]);
    setTimeout(() => {
      removeToast(id);
    }, 3500);
  };

  const removeToast = (id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  };

  // Product Methods
  const addProduct = (productData: Omit<Product, 'id' | 'createdAt' | 'salesCount'>) => {
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
    void saveCatalogProductApi(request).then((id) => {
      setProducts((prev) => prev.map((product) => product.id === newProduct.id ? { ...product, id: String(id) } : product));
    }).catch(() => showToast('商品已加入当前页面，但服务端保存失败', 'warning'));
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
    setProducts((prev) =>
      prev.map((p) => {
        if (p.id === id) {
          const nextStatus = p.status === 'active' ? 'inactive' : 'active';
          showToast(`商品状态已变更为「${nextStatus === 'active' ? '上架中' : '已下架'}」`, 'info');
          return { ...p, status: nextStatus };
        }
        return p;
      })
    );
    if (target && Number.isFinite(Number(id))) {
      void saveCatalogProductApi(productToCatalogRequest({ ...target, status: target.status === 'active' ? 'inactive' : 'active' }))
        .catch(() => showToast('商品状态已更新本地状态，但服务端保存失败', 'warning'));
    }
  };

  const batchUpdateProductStatus = (ids: string[], status: ProductStatus) => {
    setProducts((prev) =>
      prev.map((p) => (ids.includes(p.id) ? { ...p, status } : p))
    );
    showToast(`已批量${status === 'active' ? '上架' : '下架'} ${ids.length} 件商品`, 'success');
    ids.map((id) => products.find((product) => product.id === id)).filter((product): product is Product => Boolean(product) && Number.isFinite(Number(product.id))).forEach((product) => {
      void saveCatalogProductApi(productToCatalogRequest({ ...product, status })).catch(() => showToast(`商品「${product.name}」服务端状态更新失败`, 'warning'));
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
  const addOrder = (orderData: Omit<Order, 'id' | 'createdAt'>) => {
    const now = new Date();
    const formattedDate = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')} ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`;
    const newOrder: Order = {
      ...orderData,
      id: `ord-${Date.now()}`,
      createdAt: formattedDate
    };
    setOrders((prev) => [newOrder, ...prev]);
    showToast(`订单 ${newOrder.orderNumber} 已创建`, 'success');
  };

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
              desc: `${carrier || o.shippingCarrier || '物流公司'} 运单号: ${trackingNumber || o.trackingNumber || 'SF8899201'} 已生成并出库`,
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
      .map((shipment) => shipTradeOrder(shipment.numericId, {
        logisticsCompany: shipment.carrier,
        trackingNo: shipment.trackingNumber
      }));
    if (requests.length > 0) {
      void Promise.allSettled(requests).then((results) => {
        if (results.some((result) => result.status === 'rejected')) {
          showToast('部分订单服务端发货失败，已重新同步订单', 'warning');
        }
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

  const updateOrderRemark = (id: string, sellerNote: string, flagColor?: Order['flagColor']) => {
    setOrders((prev) =>
      prev.map((o) => (o.id === id ? { ...o, sellerNote, flagColor: flagColor || o.flagColor } : o))
    );
    showToast('卖家备注及标旗已更新', 'success');
    const numericId = Number(id);
    if (Number.isFinite(numericId)) {
      void updateTradeOrderRemark(numericId, sellerNote).catch(() => {
        showToast('备注已更新本地状态，但服务端保存失败', 'warning');
        void hydrateTradeMetadata();
      });
    }
  };

  const processOrderRefund = (id: string, refundAmount: number, refundReason: string) => {
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
    showToast(`退款申请已提交，等待渠道确认 ¥${refundAmount.toFixed(2)}`, 'success');
    const numericId = Number(id);
    if (Number.isFinite(numericId)) {
      void refundTradeOrder(numericId, refundAmount, refundReason).catch(() => {
        showToast('退款已更新本地状态，但服务端处理失败', 'warning');
        void hydrateTradeMetadata();
      });
    }
  };

  // User Methods
  const addUser = (userData: Omit<User, 'id' | 'registeredAt' | 'totalSpent' | 'orderCount' | 'lastActive'>) => {
    const now = new Date();
    const formattedDate = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')} ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
    const newUser: User = {
      ...userData,
      id: `usr-${Date.now()}`,
      registeredAt: formattedDate,
      totalSpent: 0,
      orderCount: 0,
      lastActive: '刚刚',
      balance: 0,
      points: 0,
      growthValue: 0,
      tags: ['新注册会员']
    };
    setUsers((prev) => [newUser, ...prev]);
    showToast(`会员 ${newUser.name} 已成功录入`, 'success');
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
    setUsers((prev) =>
      prev.map((u) => (u.id === id ? { ...u, ...updates } : u))
    );
    showToast('会员资料更新成功', 'success');
  };

  const batchUpdateUserStatus = (ids: string[], status: UserStatus) => {
    setUsers((prev) =>
      prev.map((u) => (ids.includes(u.id) ? { ...u, status } : u))
    );
    showToast(`已批量${status === 'active' ? '解冻' : '冻结'} ${ids.length} 位会员账号`, 'info');
  };

  const adjustUserBalanceAndPoints = (id: string, pointsDelta: number, balanceDelta: number, note: string) => {
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
  };

  const updateUserTags = (id: string, tags: string[]) => {
    setUsers((prev) =>
      prev.map((u) => (u.id === id ? { ...u, tags } : u))
    );
    showToast('用户画像标签更新成功', 'success');
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
    showToast(`角色「${newRole.roleName}」创建成功`, 'success');
    void saveSystemRole({
      roleKey: newRole.roleKey.toUpperCase(),
      roleName: newRole.roleName,
      roleSort: newRole.roleSort,
      status: newRole.status === 'active' ? 1 : 0,
      dataScope: roleScopeToBackend(newRole.dataScope),
      description: newRole.description
    }).then((id) => {
      setRoles((prev) => prev.map((role) => role.id === newRole.id ? { ...role, id: String(id) } : role));
    }).catch(() => showToast('角色已加入当前页面，但服务端保存失败', 'warning'));
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
    showToast(`菜单「${newItem.title}」已添加`, 'success');
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
    }).catch(() => showToast('菜单已加入当前页面，但服务端保存失败', 'warning'));
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
  const addSystemUser = (userData: Omit<SystemUser, 'id' | 'createdAt' | 'lastLoginTime' | 'lastLoginIp'>) => {
    const newUser: SystemUser = {
      ...userData,
      id: `sys-${Date.now()}`,
      createdAt: new Date().toISOString().split('T')[0],
      lastLoginTime: '尚未登录',
      lastLoginIp: '-'
    };
    setSystemUsers((prev) => [newUser, ...prev]);
    showToast(`系统用户「${newUser.username}」已开通`, 'success');
    void createSystemUser({
      username: newUser.username,
      password: '123456',
      realName: newUser.realName,
      phone: newUser.phone === '-' ? undefined : newUser.phone,
      email: newUser.email === '-' ? undefined : newUser.email,
      deptId: Number(newUser.deptId) || undefined,
      status: newUser.status === 'active' ? 1 : 0
    }).then(async (id) => {
      setSystemUsers((prev) => prev.map((user) => user.id === newUser.id ? { ...user, id: String(id) } : user));
      const roleIds = newUser.roles.map(Number).filter((roleId) => Number.isFinite(roleId));
      if (roleIds.length) await replaceUserRoles(id, roleIds);
    }).catch(() => showToast('系统用户已加入当前页面，但服务端保存失败', 'warning'));
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
      }).catch(() => showToast('系统用户已更新本地状态，但服务端保存失败', 'warning'));
      if (updates.roles) {
        void replaceUserRoles(Number(id), updates.roles.map(Number).filter((roleId) => Number.isFinite(roleId)))
          .catch(() => showToast('角色绑定已更新本地状态，但服务端保存失败', 'warning'));
      }
    }
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
      void deleteSystemUserApi(Number(id)).catch(() => showToast('系统用户已从当前页面移除，但服务端删除失败', 'warning'));
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
    showToast(`数据权限规则「${newRule.ruleName}」已添加`, 'success');
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
    }).catch(() => showToast('规则已加入当前页面，但服务端保存失败', 'warning'));
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
  const markNotificationAsRead = (id: string) => {
    setNotifications((prev) =>
      prev.map((n) => (n.id === id ? { ...n, read: true } : n))
    );
  };

  const markAllNotificationsAsRead = () => {
    setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
    showToast('所有通知已标记为已读', 'info');
  };

  return (
    <AdminContext.Provider
      value={{
        authLoading,
        isAuthenticated: currentUser !== null,
        currentUser,
        login,
        logout,
        currentTab,
        setCurrentTab,
        products,
        orders,
        users,
        catalogCategories,
        todos,
        notifications,
        searchQuery,
        setSearchQuery,
        toasts,
        showToast,
        removeToast,
        addProduct,
        updateProduct,
        deleteProduct,
        toggleProductStatus,
        batchUpdateProductStatus,
        batchDeleteProducts,
        batchUpdateProductCategory,
        adjustProductStock,
        addOrder,
        updateOrderStatus,
        cancelOrder,
        batchShipOrders,
        batchCancelOrders,
        updateOrderRemark,
        processOrderRefund,
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
