import { BackendDataRule, BackendDepartment, BackendMemberUser, BackendMenu, BackendRole, BackendSystemUser } from '../api/adminApi';
import { DataRule, Department, MenuItem, Role, SystemUser, User } from '../types';
import { backendMenusToTree } from './menuAdapter';

export interface PermissionNode {
  id: string;
  label: string;
  key: string;
  type: 'module' | 'menu' | 'action';
  description?: string;
  children?: PermissionNode[];
}

const toStatus = (status?: number): 'active' | 'inactive' => status === 1 ? 'active' : 'inactive';

const toDataScope = (scope?: string): Role['dataScope'] => {
  switch ((scope || '').toUpperCase()) {
    case 'ALL': return 'all';
    case 'DEPT_AND_SUB': return 'dept_and_sub';
    case 'DEPT': return 'dept';
    case 'CUSTOM': return 'custom';
    default: return 'self';
  }
};

const splitList = (value?: string): string[] => value ? value.split(',').map((item) => item.trim()).filter(Boolean) : [];

const moduleNameMap: Record<string, string> = {
  orders: '订单履约数据',
  products: '商品类目与库存',
  users: '商城会员资料',
  finance: '财务与结算流水',
  inventory: '仓储调拨与盘点'
};

export function backendRolesToFrontend(source: BackendRole[], permissionKeysByRole: Map<number, string[]> = new Map()): Role[] {
  return source.map((role) => ({
    id: String(role.id),
    roleKey: role.roleKey.toLowerCase(),
    roleName: role.roleName,
    roleSort: role.roleSort || 0,
    status: toStatus(role.status),
    dataScope: toDataScope(role.dataScope),
    permissionKeys: permissionKeysByRole.get(role.id) || [],
    userCount: 0,
    createdAt: role.createdAt?.slice(0, 10) || '',
    description: role.description || ''
  }));
}

export function backendMenusToFrontend(source: BackendMenu[]): MenuItem[] {
  return backendMenusToTree(source, true);
}

export function menuItemsToPermissionTree(items: MenuItem[]): PermissionNode[] {
  return items.filter((item) => item.status === 'active' && item.visible).map((item) => {
    const type = item.type === 'directory' ? 'module' : item.type === 'button' ? 'action' : 'menu';
    const children = item.children ? menuItemsToPermissionTree(item.children) : undefined;
    return {
      id: `permission-${item.id}`,
      label: item.title,
      key: item.permission || item.path || `menu:${item.id}`,
      type,
      description: item.component ? `对应页面：${item.component}` : undefined,
      children: children?.length ? children : undefined
    };
  });
}

export function backendDepartmentsToFrontend(source: BackendDepartment[]): Department[] {
  const nodes = source.map((dept) => ({
    id: String(dept.id),
    parentId: dept.parentId && dept.parentId !== 0 ? String(dept.parentId) : null,
    name: dept.deptName,
    code: dept.deptCode,
    leader: dept.leaderUserId ? String(dept.leaderUserId) : '-',
    phone: '-',
    status: toStatus(dept.status),
    sort: dept.sortNo || 0
  }));
  const byParent = new Map<string, Department[]>();
  nodes.forEach((node) => {
    const key = node.parentId || '0';
    byParent.set(key, [...(byParent.get(key) || []), node]);
  });
  const attach = (node: Department): Department => {
    const children = (byParent.get(node.id) || []).sort((a, b) => a.sort - b.sort).map(attach);
    return children.length ? { ...node, children } : node;
  };
  return (byParent.get('0') || []).sort((a, b) => a.sort - b.sort).map(attach);
}

export function backendUsersToFrontend(source: BackendSystemUser[], deptNames: Map<number, string>, roleAssignments: Map<number, { ids: string[]; names: string[] }>): SystemUser[] {
  return source.map((user) => ({
    id: String(user.id),
    username: user.username,
    realName: user.realName,
    avatar: user.avatarUrl,
    deptId: user.deptId ? String(user.deptId) : '',
    deptName: user.deptId ? (deptNames.get(user.deptId) || '-') : '-',
    roles: roleAssignments.get(user.id)?.ids || [],
    roleNames: roleAssignments.get(user.id)?.names || [],
    phone: user.phone || '-',
    email: user.email || '-',
    status: toStatus(user.status),
    createdAt: user.createdAt?.slice(0, 10) || '',
    lastLoginTime: user.lastLoginAt?.replace('T', ' ') || '-',
    lastLoginIp: user.lastLoginIp || '-',
    dataScope: 'self'
  }));
}

export function backendMembersToFrontend(source: BackendMemberUser[]): User[] {
  return source.map((member) => ({
    id: String(member.id),
    userCode: member.memberNo,
    name: member.nickname || member.username,
    phone: member.phone || '-',
    avatar: member.avatarUrl,
    email: member.email || '-',
    registeredAt: member.registeredAt?.replace('T', ' ') || '-',
    totalSpent: Number(member.totalSpent || 0),
    orderCount: Number(member.orderCount || 0),
    status: member.status === 1 ? 'active' : 'suspended',
    tier: (['regular', 'silver', 'gold', 'platinum'].includes(member.memberLevel.toLowerCase())
      ? member.memberLevel.toLowerCase() : 'regular') as User['tier'],
    lastActive: member.lastLoginAt?.replace('T', ' ') || '-',
    balance: Number(member.balance || 0),
    points: Number(member.points || 0),
    tags: splitList(member.tagsCsv),
    notes: member.remark || '',
    growthValue: 0
  }));
}

export function backendDataRulesToFrontend(source: BackendDataRule[], roleByRuleId: Map<number, { id: string; name: string }> = new Map()): DataRule[] {
  return source.map((rule) => ({
    id: String(rule.id),
    ruleName: rule.ruleName,
    module: (['orders', 'products', 'users', 'finance', 'inventory'].includes(rule.moduleKey) ? rule.moduleKey : 'orders') as DataRule['module'],
    moduleName: moduleNameMap[rule.moduleKey] || rule.moduleKey,
    roleId: roleByRuleId.get(rule.id)?.id || '',
    roleName: roleByRuleId.get(rule.id)?.name || '-',
    scopeType: toDataScope(rule.scopeType) as DataRule['scopeType'],
    customDeptIds: splitList(rule.customDeptIds),
    fieldMasks: splitList(rule.fieldMasks),
    filterCondition: rule.filterExpression || '',
    status: toStatus(rule.status),
    updatedAt: rule.updatedAt?.replace('T', ' ') || ''
  }));
}
