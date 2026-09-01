import { MenuItem, MenuType, NavigationTab } from '../types';
import { BackendMenu } from '../api/adminApi';

const routeTabMap: Record<string, NavigationTab> = {
  '/dashboard': 'dashboard',
  '/ecommerce/products': 'products',
  '/ecommerce/categories': 'categories',
  '/ecommerce/orders': 'orders',
  '/ecommerce/customers': 'users',
  '/marketing/coupons': 'coupons',
  '/marketing/flash-sales': 'flash_sales',
  '/inventory/stock': 'inventory_stock',
  '/inventory/suppliers': 'inventory_suppliers',
  '/finance/transactions': 'finance_transactions',
  '/finance/invoices': 'finance_invoices',
  '/analytics/overview': 'analytics_overview',
  '/analytics/products': 'analytics_products',
  '/content/banners': 'content_banners',
  '/content/reviews': 'content_reviews',
  '/system/roles': 'roles',
  '/system/menus': 'menus',
  '/system/users': 'system_users',
  '/system/auth': 'authorization',
  '/system/data-rules': 'data_permissions',
  '/system/data-permissions': 'data_permissions',
  '/settings': 'settings',
  '/settings/login-logs': 'login_logs',
  '/settings/operation-logs': 'operation_logs'
};

export function routeToTab(path?: string): NavigationTab | undefined {
  return path ? routeTabMap[path] : undefined;
}

/**
 * 判断后端菜单树是否包含指定的前端页面标识。
 *
 * @param items 后端菜单树
 * @param tab 页面标识
 * @returns 是否存在对应菜单
 */
export function containsMenuTab(items: MenuItem[], tab: NavigationTab): boolean {
  return items.some((item) => routeToTab(item.path) === tab || (item.children ? containsMenuTab(item.children, tab) : false));
}

/**
 * 按后端菜单排序获取第一个可访问页面，作为登录后的默认页面。
 *
 * @param items 后端菜单树
 * @returns 第一个页面标识，不存在时返回 undefined
 */
export function firstMenuTab(items: MenuItem[]): NavigationTab | undefined {
  for (const item of items) {
    if (item.type !== 'button') {
      const tab = routeToTab(item.path);
      if (tab) return tab;
    }
    if (item.children) {
      const childTab = firstMenuTab(item.children);
      if (childTab) return childTab;
    }
  }
  return undefined;
}

export function backendMenusToTree(source: BackendMenu[], includeDisabled = false): MenuItem[] {
  const visibleMenus = includeDisabled ? source : source.filter((item) => item.status === 1 && item.visible === 1);
  const byParent = new Map<string, MenuItem[]>();
  const nodes = visibleMenus.map((item) => ({
    id: String(item.id),
    parentId: item.parentId ? String(item.parentId) : null,
    title: item.menuName,
    icon: item.icon,
    type: item.menuType.toLowerCase() as MenuType,
    path: item.routePath,
    component: item.component,
    permission: item.permissionCode,
    sort: item.sortNo,
    visible: item.visible === 1,
    status: item.status === 1 ? 'active' as const : 'inactive' as const
  }));
  nodes.forEach((node) => {
    const parent = node.parentId || '0';
    const children = byParent.get(parent) || [];
    children.push(node);
    byParent.set(parent, children);
  });
  const attachChildren = (node: MenuItem): MenuItem => {
    const children = (byParent.get(node.id) || []).sort((a, b) => a.sort - b.sort).map(attachChildren);
    return children.length ? { ...node, children } : node;
  };
  return (byParent.get('0') || []).sort((a, b) => a.sort - b.sort).map(attachChildren);
}
