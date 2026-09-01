import { Role, MenuItem, SystemUser, Department, DataRule } from '../types';

export const initialDepartments: Department[] = [
  {
    id: 'dept-1',
    parentId: null,
    name: '总部管理中心',
    code: 'HQ_CENTER',
    leader: '张董事',
    phone: '021-88880001',
    status: 'active',
    sort: 1,
    children: [
      {
        id: 'dept-1-1',
        parentId: 'dept-1',
        name: '电商运营中心',
        code: 'ECOM_OPS',
        leader: '李总监',
        phone: '021-88880002',
        status: 'active',
        sort: 1,
        children: [
          {
            id: 'dept-1-1-1',
            parentId: 'dept-1-1',
            name: '商品与类目组',
            code: 'PROD_GRP',
            leader: '王组长',
            phone: '021-88880011',
            status: 'active',
            sort: 1
          },
          {
            id: 'dept-1-1-2',
            parentId: 'dept-1-1',
            name: '订单履约与仓储组',
            code: 'ORDER_GRP',
            leader: '赵主管',
            phone: '021-88880012',
            status: 'active',
            sort: 2
          },
          {
            id: 'dept-1-1-3',
            parentId: 'dept-1-1',
            name: '客户服务支持组',
            code: 'CS_GRP',
            leader: '孙主管',
            phone: '021-88880013',
            status: 'active',
            sort: 3
          }
        ]
      },
      {
        id: 'dept-1-2',
        parentId: 'dept-1',
        name: '华东大区直营事业部',
        code: 'REGION_EAST',
        leader: '钱经理',
        phone: '021-88880003',
        status: 'active',
        sort: 2,
        children: [
          {
            id: 'dept-1-2-1',
            parentId: 'dept-1-2',
            name: '上海静安旗舰店',
            code: 'STORE_SH_01',
            leader: '周店长',
            phone: '021-66223311',
            status: 'active',
            sort: 1
          },
          {
            id: 'dept-1-2-2',
            parentId: 'dept-1-2',
            name: '杭州西湖体验店',
            code: 'STORE_HZ_01',
            leader: '吴店长',
            phone: '0571-88223311',
            status: 'active',
            sort: 2
          }
        ]
      },
      {
        id: 'dept-1-3',
        parentId: 'dept-1',
        name: '财务与风控审计中心',
        code: 'FINANCE_AUDIT',
        leader: '郑总监',
        phone: '021-88880004',
        status: 'active',
        sort: 3
      }
    ]
  }
];

export const initialRoles: Role[] = [
  {
    id: 'role-super-admin',
    roleKey: 'super_admin',
    roleName: '超级管理员',
    roleSort: 1,
    status: 'active',
    dataScope: 'all',
    permissionKeys: ['*:*:*'],
    userCount: 2,
    createdAt: '2023-01-01',
    description: '系统最高权限拥有者，具备所有业务模块、配置管理和全部数据权限。'
  },
  {
    id: 'role-ops-manager',
    roleKey: 'ops_manager',
    roleName: '电商运营主管',
    roleSort: 2,
    status: 'active',
    dataScope: 'dept_and_sub',
    permissionKeys: [
      'dashboard:view',
      'product:list',
      'product:add',
      'product:edit',
      'product:status',
      'order:list',
      'order:detail',
      'order:ship',
      'user:list',
      'user:detail'
    ],
    userCount: 4,
    createdAt: '2023-03-15',
    description: '负责商品上下架、促销活动规划与订单履约监管，具备运营中心全域数据查看权限。'
  },
  {
    id: 'role-order-specialist',
    roleKey: 'order_specialist',
    roleName: '订单履约专员',
    roleSort: 3,
    status: 'active',
    dataScope: 'dept',
    permissionKeys: [
      'dashboard:view',
      'order:list',
      'order:detail',
      'order:ship',
      'order:export',
      'product:list'
    ],
    userCount: 6,
    createdAt: '2023-04-10',
    description: '负责订单发货跟踪、物流单号录入及仓储打单核验。'
  },
  {
    id: 'role-cs-agent',
    roleKey: 'cs_agent',
    roleName: '售后客服专员',
    roleSort: 4,
    status: 'active',
    dataScope: 'self',
    permissionKeys: [
      'dashboard:view',
      'order:list',
      'order:detail',
      'order:refund',
      'user:list',
      'user:detail'
    ],
    userCount: 8,
    createdAt: '2023-05-20',
    description: '处理消费者退款申请、售后咨询解答与订单异常工单。'
  },
  {
    id: 'role-finance-auditor',
    roleKey: 'finance_auditor',
    roleName: '财务风控审计员',
    roleSort: 5,
    status: 'active',
    dataScope: 'all',
    permissionKeys: [
      'dashboard:view',
      'order:list',
      'order:detail',
      'order:export',
      'finance:view',
      'finance:export',
      'user:list'
    ],
    userCount: 3,
    createdAt: '2023-06-01',
    description: '负责交易结算审核、退款资金对账与营收报表导出。'
  },
  {
    id: 'role-store-manager',
    roleKey: 'store_manager',
    roleName: '区域门店店长',
    roleSort: 6,
    status: 'active',
    dataScope: 'custom',
    customDeptIds: ['dept-1-2-1', 'dept-1-2-2'],
    permissionKeys: [
      'dashboard:view',
      'product:list',
      'order:list',
      'order:detail',
      'order:ship'
    ],
    userCount: 5,
    createdAt: '2023-07-12',
    description: '负责指定区域门店的库存核销与自提/即时配送订单。'
  }
];

export const initialMenuItems: MenuItem[] = [
  {
    id: 'menu-dashboard',
    parentId: null,
    title: '工作台仪表盘',
    icon: 'LayoutDashboard',
    type: 'menu',
    path: '/dashboard',
    component: 'DashboardView',
    permission: 'dashboard:view',
    sort: 1,
    visible: true,
    status: 'active'
  },
  {
    id: 'menu-ecommerce',
    parentId: null,
    title: '电商业务中心',
    icon: 'ShoppingBag',
    type: 'directory',
    path: '/ecommerce',
    sort: 2,
    visible: true,
    status: 'active',
    children: [
      {
        id: 'menu-prod-mgmt',
        parentId: 'menu-ecommerce',
        title: '商品管理',
        icon: 'Package',
        type: 'menu',
        path: '/ecommerce/products',
        component: 'ProductManagementView',
        permission: 'product:list',
        sort: 1,
        visible: true,
        status: 'active',
        children: [
          {
            id: 'btn-prod-add',
            parentId: 'menu-prod-mgmt',
            title: '商品新增',
            type: 'button',
            permission: 'product:add',
            sort: 1,
            visible: true,
            status: 'active'
          },
          {
            id: 'btn-prod-edit',
            parentId: 'menu-prod-mgmt',
            title: '商品编辑',
            type: 'button',
            permission: 'product:edit',
            sort: 2,
            visible: true,
            status: 'active'
          },
          {
            id: 'btn-prod-del',
            parentId: 'menu-prod-mgmt',
            title: '商品删除',
            type: 'button',
            permission: 'product:delete',
            sort: 3,
            visible: true,
            status: 'active'
          },
          {
            id: 'btn-prod-status',
            parentId: 'menu-prod-mgmt',
            title: '上下架控制',
            type: 'button',
            permission: 'product:status',
            sort: 4,
            visible: true,
            status: 'active'
          }
        ]
      },
      {
        id: 'menu-order-mgmt',
        parentId: 'menu-ecommerce',
        title: '订单管理',
        icon: 'ShoppingCart',
        type: 'menu',
        path: '/ecommerce/orders',
        component: 'OrderManagementView',
        permission: 'order:list',
        sort: 2,
        visible: true,
        status: 'active',
        children: [
          {
            id: 'btn-order-ship',
            parentId: 'menu-order-mgmt',
            title: '订单发货',
            type: 'button',
            permission: 'order:ship',
            sort: 1,
            visible: true,
            status: 'active'
          },
          {
            id: 'btn-order-refund',
            parentId: 'menu-order-mgmt',
            title: '退款审核',
            type: 'button',
            permission: 'order:refund',
            sort: 2,
            visible: true,
            status: 'active'
          },
          {
            id: 'btn-order-export',
            parentId: 'menu-order-mgmt',
            title: '订单导出',
            type: 'button',
            permission: 'order:export',
            sort: 3,
            visible: true,
            status: 'active'
          }
        ]
      },
      {
        id: 'menu-customer-mgmt',
        parentId: 'menu-ecommerce',
        title: '商城会员管理',
        icon: 'UserCheck',
        type: 'menu',
        path: '/ecommerce/customers',
        component: 'UserManagementView',
        permission: 'user:list',
        sort: 3,
        visible: true,
        status: 'active',
        children: [
          {
            id: 'btn-user-status',
            parentId: 'menu-customer-mgmt',
            title: '会员冻结/解冻',
            type: 'button',
            permission: 'user:status',
            sort: 1,
            visible: true,
            status: 'active'
          },
          {
            id: 'btn-user-add',
            parentId: 'menu-customer-mgmt',
            title: '创建会员档案',
            type: 'button',
            permission: 'user:add',
            sort: 2,
            visible: true,
            status: 'active'
          }
        ]
      }
    ]
  },
  {
    id: 'menu-marketing',
    parentId: null,
    title: '营销与促销中心',
    icon: 'BadgePercent',
    type: 'directory',
    path: '/marketing',
    sort: 3,
    visible: true,
    status: 'active',
    children: [
      {
        id: 'menu-coupons',
        parentId: 'menu-marketing',
        title: '优惠券中心',
        icon: 'Ticket',
        type: 'menu',
        path: '/marketing/coupons',
        component: 'CouponManagementView',
        permission: 'marketing:coupon:list',
        sort: 1,
        visible: true,
        status: 'active'
      },
      {
        id: 'menu-flash-sales',
        parentId: 'menu-marketing',
        title: '秒杀与拼团',
        icon: 'Zap',
        type: 'menu',
        path: '/marketing/flash-sales',
        component: 'FlashSaleManagementView',
        permission: 'marketing:flash:list',
        sort: 2,
        visible: true,
        status: 'active'
      }
    ]
  },
  {
    id: 'menu-inventory',
    parentId: null,
    title: '仓储进销存 (WMS)',
    icon: 'Boxes',
    type: 'directory',
    path: '/inventory',
    sort: 4,
    visible: true,
    status: 'active',
    children: [
      {
        id: 'menu-stock',
        parentId: 'menu-inventory',
        title: '调拨与出入库',
        icon: 'Warehouse',
        type: 'menu',
        path: '/inventory/stock',
        component: 'WarehouseStockView',
        permission: 'inventory:stock:list',
        sort: 1,
        visible: true,
        status: 'active'
      },
      {
        id: 'menu-suppliers',
        parentId: 'menu-inventory',
        title: '供应商与采购',
        icon: 'Truck',
        type: 'menu',
        path: '/inventory/suppliers',
        component: 'SupplierManagementView',
        permission: 'inventory:supplier:list',
        sort: 2,
        visible: true,
        status: 'active'
      }
    ]
  },
  {
    id: 'menu-finance',
    parentId: null,
    title: '财务结算中心',
    icon: 'Coins',
    type: 'directory',
    path: '/finance',
    sort: 5,
    visible: true,
    status: 'active',
    children: [
      {
        id: 'menu-transactions',
        parentId: 'menu-finance',
        title: '资金流水对账',
        icon: 'DollarSign',
        type: 'menu',
        path: '/finance/transactions',
        component: 'TransactionReconciliationView',
        permission: 'finance:transaction:list',
        sort: 1,
        visible: true,
        status: 'active'
      },
      {
        id: 'menu-invoices',
        parentId: 'menu-finance',
        title: '发票与税务',
        icon: 'FileText',
        type: 'menu',
        path: '/finance/invoices',
        component: 'InvoiceManagementView',
        permission: 'finance:invoice:list',
        sort: 2,
        visible: true,
        status: 'active'
      }
    ]
  },
  {
    id: 'menu-analytics',
    parentId: null,
    title: '经营分析与BI',
    icon: 'LineChart',
    type: 'directory',
    path: '/analytics',
    sort: 6,
    visible: true,
    status: 'active',
    children: [
      {
        id: 'menu-analytics-overview',
        parentId: 'menu-analytics',
        title: '经营大屏',
        icon: 'LineChart',
        type: 'menu',
        path: '/analytics/overview',
        component: 'AnalyticsOverviewView',
        permission: 'analytics:overview:view',
        sort: 1,
        visible: true,
        status: 'active'
      },
      {
        id: 'menu-analytics-products',
        parentId: 'menu-analytics',
        title: '商品动销榜',
        icon: 'BarChart3',
        type: 'menu',
        path: '/analytics/products',
        component: 'ProductAnalyticsView',
        permission: 'analytics:product:view',
        sort: 2,
        visible: true,
        status: 'active'
      }
    ]
  },
  {
    id: 'menu-content',
    parentId: null,
    title: '内容与客户运营',
    icon: 'MessageSquare',
    type: 'directory',
    path: '/content',
    sort: 7,
    visible: true,
    status: 'active',
    children: [
      {
        id: 'menu-banners',
        parentId: 'menu-content',
        title: '轮播海报',
        icon: 'Image',
        type: 'menu',
        path: '/content/banners',
        component: 'BannerManagementView',
        permission: 'content:banner:list',
        sort: 1,
        visible: true,
        status: 'active'
      },
      {
        id: 'menu-reviews',
        parentId: 'menu-content',
        title: '客户评价',
        icon: 'MessageSquare',
        type: 'menu',
        path: '/content/reviews',
        component: 'ReviewManagementView',
        permission: 'content:review:list',
        sort: 2,
        visible: true,
        status: 'active'
      }
    ]
  },
  {
    id: 'menu-system-rbac',
    parentId: null,
    title: '权限与系统管理',
    icon: 'ShieldCheck',
    type: 'directory',
    path: '/system',
    sort: 3,
    visible: true,
    status: 'active',
    children: [
      {
        id: 'menu-roles',
        parentId: 'menu-system-rbac',
        title: '角色管理',
        icon: 'Shield',
        type: 'menu',
        path: '/system/roles',
        component: 'RoleManagementView',
        permission: 'system:role:list',
        sort: 1,
        visible: true,
        status: 'active',
        children: [
          {
            id: 'btn-role-add',
            parentId: 'menu-roles',
            title: '新增角色',
            type: 'button',
            permission: 'system:role:add',
            sort: 1,
            visible: true,
            status: 'active'
          },
          {
            id: 'btn-role-edit',
            parentId: 'menu-roles',
            title: '编辑角色',
            type: 'button',
            permission: 'system:role:edit',
            sort: 2,
            visible: true,
            status: 'active'
          },
          {
            id: 'btn-role-del',
            parentId: 'menu-roles',
            title: '删除角色',
            type: 'button',
            permission: 'system:role:delete',
            sort: 3,
            visible: true,
            status: 'active'
          }
        ]
      },
      {
        id: 'menu-menu-mgmt',
        parentId: 'menu-system-rbac',
        title: '菜单管理',
        icon: 'Menu',
        type: 'menu',
        path: '/system/menus',
        component: 'MenuManagementView',
        permission: 'system:menu:list',
        sort: 2,
        visible: true,
        status: 'active',
        children: [
          {
            id: 'btn-menu-add',
            parentId: 'menu-menu-mgmt',
            title: '新增菜单项',
            type: 'button',
            permission: 'system:menu:add',
            sort: 1,
            visible: true,
            status: 'active'
          },
          {
            id: 'btn-menu-edit',
            parentId: 'menu-menu-mgmt',
            title: '编辑菜单',
            type: 'button',
            permission: 'system:menu:edit',
            sort: 2,
            visible: true,
            status: 'active'
          }
        ]
      },
      {
        id: 'menu-sys-users',
        parentId: 'menu-system-rbac',
        title: '系统用户',
        icon: 'Users',
        type: 'menu',
        path: '/system/users',
        component: 'SystemUserManagementView',
        permission: 'system:user:list',
        sort: 3,
        visible: true,
        status: 'active',
        children: [
          {
            id: 'btn-sysuser-add',
            parentId: 'menu-sys-users',
            title: '新建系统账号',
            type: 'button',
            permission: 'system:user:add',
            sort: 1,
            visible: true,
            status: 'active'
          },
          {
            id: 'btn-sysuser-edit',
            parentId: 'menu-sys-users',
            title: '分配角色',
            type: 'button',
            permission: 'system:user:role',
            sort: 2,
            visible: true,
            status: 'active'
          }
        ]
      },
      {
        id: 'menu-auth-mgmt',
        parentId: 'menu-system-rbac',
        title: '授权管理',
        icon: 'KeyRound',
        type: 'menu',
        path: '/system/auth',
        component: 'AuthorizationView',
        permission: 'system:auth:assign',
        sort: 4,
        visible: true,
        status: 'active'
      },
      {
        id: 'menu-data-perms',
        parentId: 'menu-system-rbac',
        title: '数据权限',
        icon: 'Lock',
        type: 'menu',
        path: '/system/data-permissions',
        component: 'DataPermissionView',
        permission: 'system:data:rule',
        sort: 5,
        visible: true,
        status: 'active'
      }
    ]
  },
  {
    id: 'menu-system-settings',
    parentId: null,
    title: '系统与参数设置',
    icon: 'Settings',
    type: 'directory',
    path: '/system-settings',
    sort: 4,
    visible: true,
    status: 'active',
    children: [
      {
        id: 'menu-settings-config',
        parentId: 'menu-system-settings',
        title: '参数配置',
        icon: 'Settings',
        type: 'menu',
        path: '/settings',
        component: 'SettingsView',
        permission: 'system:config:view',
        sort: 1,
        visible: true,
        status: 'active'
      },
      {
        id: 'menu-login-logs',
        parentId: 'menu-system-settings',
        title: '登录记录',
        icon: 'LogIn',
        type: 'menu',
        path: '/settings/login-logs',
        component: 'LoginLogManagementView',
        permission: 'system:audit:login',
        sort: 2,
        visible: true,
        status: 'active'
      },
      {
        id: 'menu-operation-logs',
        parentId: 'menu-system-settings',
        title: '操作审计',
        icon: 'ClipboardCheck',
        type: 'menu',
        path: '/settings/operation-logs',
        component: 'OperationLogManagementView',
        permission: 'system:audit:operation',
        sort: 3,
        visible: true,
        status: 'active'
      }
    ]
  }
];

export const initialSystemUsers: SystemUser[] = [
  {
    id: 'sys-user-1',
    username: 'admin',
    realName: '陈志远 (主管理员)',
    avatar: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&auto=format&fit=crop&q=80',
    deptId: 'dept-1',
    deptName: '总部管理中心',
    roles: ['role-super-admin'],
    roleNames: ['超级管理员'],
    phone: '13800138000',
    email: 'admin@company.com',
    status: 'active',
    createdAt: '2023-01-01',
    lastLoginTime: '2023-10-24 09:30:15',
    lastLoginIp: '192.168.1.100 (上海总部内网)',
    dataScope: 'all'
  },
  {
    id: 'sys-user-2',
    username: 'ops_li',
    realName: '李雅静 (运营主管)',
    avatar: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150&auto=format&fit=crop&q=80',
    deptId: 'dept-1-1',
    deptName: '电商运营中心',
    roles: ['role-ops-manager'],
    roleNames: ['电商运营主管'],
    phone: '13911223344',
    email: 'yj.li@company.com',
    status: 'active',
    createdAt: '2023-03-15',
    lastLoginTime: '2023-10-24 08:45:20',
    lastLoginIp: '180.168.22.10 (上海移动)',
    dataScope: 'dept_and_sub'
  },
  {
    id: 'sys-user-3',
    username: 'order_zhao',
    realName: '赵晨 (履约主管)',
    avatar: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&auto=format&fit=crop&q=80',
    deptId: 'dept-1-1-2',
    deptName: '订单履约与仓储组',
    roles: ['role-order-specialist'],
    roleNames: ['订单履约专员'],
    phone: '13766554433',
    email: 'c.zhao@company.com',
    status: 'active',
    createdAt: '2023-04-12',
    lastLoginTime: '2023-10-23 18:20:00',
    lastLoginIp: '180.168.22.15 (上海移动)',
    dataScope: 'dept'
  },
  {
    id: 'sys-user-4',
    username: 'cs_sun',
    realName: '孙雨涵 (售后专员)',
    avatar: 'https://images.unsplash.com/photo-1517841905240-472988babdf9?w=150&auto=format&fit=crop&q=80',
    deptId: 'dept-1-1-3',
    deptName: '客户服务支持组',
    roles: ['role-cs-agent'],
    roleNames: ['售后客服专员'],
    phone: '13677889900',
    email: 'yh.sun@company.com',
    status: 'active',
    createdAt: '2023-05-22',
    lastLoginTime: '2023-10-24 10:12:40',
    lastLoginIp: '116.228.89.5 (电信专线)',
    dataScope: 'self'
  },
  {
    id: 'sys-user-5',
    username: 'finance_zheng',
    realName: '郑伟明 (财务总监)',
    avatar: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=150&auto=format&fit=crop&q=80',
    deptId: 'dept-1-3',
    deptName: '财务与风控审计中心',
    roles: ['role-finance-auditor'],
    roleNames: ['财务风控审计员'],
    phone: '13599887766',
    email: 'wm.zheng@company.com',
    status: 'active',
    createdAt: '2023-06-05',
    lastLoginTime: '2023-10-24 09:05:10',
    lastLoginIp: '192.168.1.108 (财务专用内网)',
    dataScope: 'all'
  },
  {
    id: 'sys-user-6',
    username: 'store_zhou',
    realName: '周浩然 (上海店长)',
    deptId: 'dept-1-2-1',
    deptName: '上海静安旗舰店',
    roles: ['role-store-manager'],
    roleNames: ['区域门店店长'],
    phone: '13344556677',
    email: 'hr.zhou@company.com',
    status: 'active',
    createdAt: '2023-07-15',
    lastLoginTime: '2023-10-23 21:10:00',
    lastLoginIp: '218.1.72.4 (门店光纤)',
    dataScope: 'custom'
  },
  {
    id: 'sys-user-7',
    username: 'test_temp',
    realName: '黄立新 (实习运营)',
    deptId: 'dept-1-1-1',
    deptName: '商品与类目组',
    roles: ['role-order-specialist'],
    roleNames: ['订单履约专员'],
    phone: '13122334455',
    email: 'lx.huang@company.com',
    status: 'inactive',
    createdAt: '2023-09-01',
    lastLoginTime: '2023-09-20 14:00:00',
    lastLoginIp: '116.228.89.12',
    dataScope: 'self'
  }
];

export const initialDataRules: DataRule[] = [
  {
    id: 'rule-1',
    ruleName: '全部订单数据查看规则',
    module: 'orders',
    moduleName: '订单履约数据',
    roleId: 'role-super-admin',
    roleName: '超级管理员',
    scopeType: 'all',
    fieldMasks: [],
    filterCondition: '无条件过滤 (全量放行)',
    status: 'active',
    updatedAt: '2023-10-20'
  },
  {
    id: 'rule-2',
    ruleName: '运营部订单及子部门数据权限',
    module: 'orders',
    moduleName: '订单履约数据',
    roleId: 'role-ops-manager',
    roleName: '电商运营主管',
    scopeType: 'dept_and_sub',
    fieldMasks: ['customer_id_card'],
    filterCondition: 'dept_id IN (本部门及子部门树)',
    status: 'active',
    updatedAt: '2023-10-18'
  },
  {
    id: 'rule-3',
    ruleName: '客服专员仅本人经手工单与手机脱敏',
    module: 'orders',
    moduleName: '订单履约数据',
    roleId: 'role-cs-agent',
    roleName: '售后客服专员',
    scopeType: 'self',
    fieldMasks: ['customer_phone_mask', 'customer_id_card'],
    filterCondition: 'handler_id = current_user_id',
    status: 'active',
    updatedAt: '2023-10-22'
  },
  {
    id: 'rule-4',
    ruleName: '门店店长多店自提核销数据隔离',
    module: 'orders',
    moduleName: '订单履约数据',
    roleId: 'role-store-manager',
    roleName: '区域门店店长',
    scopeType: 'custom',
    customDeptIds: ['dept-1-2-1', 'dept-1-2-2'],
    fieldMasks: ['profit_cost_margin'],
    filterCondition: 'store_id IN (上海静安店, 杭州西湖店)',
    status: 'active',
    updatedAt: '2023-10-15'
  },
  {
    id: 'rule-5',
    ruleName: '财务部资金与流水全量审计规则',
    module: 'finance',
    moduleName: '财务与结算流水',
    roleId: 'role-finance-auditor',
    roleName: '财务风控审计员',
    scopeType: 'all',
    fieldMasks: [],
    filterCondition: 'payment_status = "SUCCESS"',
    status: 'active',
    updatedAt: '2023-10-12'
  },
  {
    id: 'rule-6',
    ruleName: '商品库类目编辑与成本价保护',
    module: 'products',
    moduleName: '商品类目与库存',
    roleId: 'role-ops-manager',
    roleName: '电商运营主管',
    scopeType: 'dept_and_sub',
    fieldMasks: ['supply_cost_price'],
    filterCondition: 'category_status = 1',
    status: 'active',
    updatedAt: '2023-10-19'
  }
];

// Tree structured permissions for the Authorization View
export interface PermissionNode {
  id: string;
  label: string;
  key: string;
  type: 'module' | 'menu' | 'action';
  description?: string;
  children?: PermissionNode[];
}

export const permissionTreeData: PermissionNode[] = [
  {
    id: 'perm-dashboard',
    label: '工作台模块 (Dashboard)',
    key: 'dashboard',
    type: 'module',
    children: [
      {
        id: 'perm-dash-view',
        label: '查看核心指标与趋势看板',
        key: 'dashboard:view',
        type: 'action',
        description: '允许查看今日销售额、订单量及近30天走势'
      },
      {
        id: 'perm-dash-todo',
        label: '处理待办待审核事项',
        key: 'dashboard:todo',
        type: 'action',
        description: '允许处理预警与快捷退款待办'
      }
    ]
  },
  {
    id: 'perm-ecommerce',
    label: '电商业务中心 (E-Commerce)',
    key: 'ecommerce',
    type: 'module',
    children: [
      {
        id: 'perm-prod',
        label: '商品管理 (Products)',
        key: 'product',
        type: 'menu',
        children: [
          { id: 'perm-prod-list', label: '商品列表查看', key: 'product:list', type: 'action' },
          { id: 'perm-prod-add', label: '发布新商品', key: 'product:add', type: 'action' },
          { id: 'perm-prod-edit', label: '修改商品资料/价格/库存', key: 'product:edit', type: 'action' },
          { id: 'perm-prod-del', label: '删除商品', key: 'product:delete', type: 'action' },
          { id: 'perm-prod-status', label: '上架/下架商品状态变更', key: 'product:status', type: 'action' }
        ]
      },
      {
        id: 'perm-order',
        label: '订单管理 (Orders)',
        key: 'order',
        type: 'menu',
        children: [
          { id: 'perm-order-list', label: '订单列表查询', key: 'order:list', type: 'action' },
          { id: 'perm-order-detail', label: '查看订单详情与收件信息', key: 'order:detail', type: 'action' },
          { id: 'perm-order-ship', label: '订单发货与物流单号录入', key: 'order:ship', type: 'action' },
          { id: 'perm-order-refund', label: '退款申请审核与原路退还', key: 'order:refund', type: 'action' },
          { id: 'perm-order-export', label: '批量导出订单报表(Excel)', key: 'order:export', type: 'action' }
        ]
      },
      {
        id: 'perm-customer',
        label: '商城会员管理 (Members)',
        key: 'user',
        type: 'menu',
        children: [
          { id: 'perm-user-list', label: '会员列表与消费总额统计', key: 'user:list', type: 'action' },
          { id: 'perm-user-detail', label: '会员画像与订单历史穿透', key: 'user:detail', type: 'action' },
          { id: 'perm-user-status', label: '会员账户冻结/解冻惩罚', key: 'user:status', type: 'action' },
          { id: 'perm-user-add', label: '手动开通线下会员卡', key: 'user:add', type: 'action' }
        ]
      }
    ]
  },
  {
    id: 'perm-system',
    label: '系统与权限安全中心 (RBAC)',
    key: 'system',
    type: 'module',
    children: [
      {
        id: 'perm-role-mgmt',
        label: '角色管理 (Roles)',
        key: 'system:role',
        type: 'menu',
        children: [
          { id: 'perm-role-list', label: '查询角色列表', key: 'system:role:list', type: 'action' },
          { id: 'perm-role-add', label: '创建系统角色', key: 'system:role:add', type: 'action' },
          { id: 'perm-role-edit', label: '修改角色信息与状态', key: 'system:role:edit', type: 'action' },
          { id: 'perm-role-del', label: '注销/删除角色', key: 'system:role:delete', type: 'action' }
        ]
      },
      {
        id: 'perm-menu-mgmt-node',
        label: '菜单管理 (Menus)',
        key: 'system:menu',
        type: 'menu',
        children: [
          { id: 'perm-menu-list', label: '查询菜单配置树', key: 'system:menu:list', type: 'action' },
          { id: 'perm-menu-add', label: '新增路由菜单/按钮项', key: 'system:menu:add', type: 'action' },
          { id: 'perm-menu-edit', label: '编辑菜单层级与权限字', key: 'system:menu:edit', type: 'action' },
          { id: 'perm-menu-del', label: '删除菜单节点', key: 'system:menu:delete', type: 'action' }
        ]
      },
      {
        id: 'perm-sysuser-mgmt',
        label: '系统用户管理 (Admin Users)',
        key: 'system:user',
        type: 'menu',
        children: [
          { id: 'perm-sysuser-list', label: '查询员工后台账号', key: 'system:user:list', type: 'action' },
          { id: 'perm-sysuser-add', label: '新建后台账号', key: 'system:user:add', type: 'action' },
          { id: 'perm-sysuser-role', label: '分配所属部门与角色', key: 'system:user:role', type: 'action' },
          { id: 'perm-sysuser-reset', label: '重置登录密码与两步验证', key: 'system:user:reset', type: 'action' }
        ]
      },
      {
        id: 'perm-auth-node',
        label: '授权与权限矩阵 (Permissions)',
        key: 'system:auth',
        type: 'menu',
        children: [
          { id: 'perm-auth-assign', label: '配置角色-功能权限映射表', key: 'system:auth:assign', type: 'action' },
          { id: 'perm-auth-test', label: '权限模拟器与在线鉴权测试', key: 'system:auth:test', type: 'action' }
        ]
      },
      {
        id: 'perm-data-node',
        label: '数据权限管控 (Data Scopes)',
        key: 'system:data',
        type: 'menu',
        children: [
          { id: 'perm-data-rule', label: '配置行级/列级数据隔离规则', key: 'system:data:rule', type: 'action' },
          { id: 'perm-data-mask', label: '敏感字段脱敏(手机号/证件)', key: 'system:data:mask', type: 'action' }
        ]
      }
    ]
  }
];
