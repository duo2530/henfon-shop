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
  '/content/member-addresses': 'content_member_addresses',
  // 客服中心的四个页面同属一级目录「客服中心」，路由前缀与目录一致。
  '/service/ai-tickets': 'ai_tickets',
  '/service/ai-knowledge': 'ai_knowledge',
  '/service/ai-agent': 'ai_agent',
  '/service/ai-agent-schedule': 'ai_agent_schedule',
  '/service/ai-agent-stats': 'ai_agent_stats',
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

/**
 * 不占导航项的从属页面：没有自己的菜单，靠权限点进入。
 *
 * 角色授权页就是这类页面——它只能从角色管理某一行点进去，进不去时提示的
 * 「暂无该页面访问权限」并不是权限没配，而是菜单树里根本没有 /system/auth 这个节点。
 */
const dependentTabPermissions: Partial<Record<NavigationTab, string>> = {
  authorization: 'system:role-menu:save'
};

export function routeToTab(path?: string): NavigationTab | undefined {
  return path ? routeTabMap[path] : undefined;
}

/**
 * 判断当前用户能否打开指定页面：菜单树里有对应菜单，或该页面是从属页面且持有对应权限点。
 *
 * @param items 后端菜单树
 * @param tab 页面标识
 * @param permissions 当前用户权限字
 * @returns 是否允许打开
 */
export function hasTabAccess(items: MenuItem[], tab: NavigationTab, permissions: string[]): boolean {
  if (containsMenuTab(items, tab)) return true;
  const required = dependentTabPermissions[tab];
  return !!required && (permissions.includes('*:*:*') || permissions.includes(required));
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
