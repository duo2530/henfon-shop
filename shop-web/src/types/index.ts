export type NavigationTab = 
  | 'dashboard' 
  // 电商核心
  | 'products' 
  | 'categories'
  | 'orders' 
  | 'users' 
  // 营销推广
  | 'coupons'
  | 'flash_sales'
  // 仓储进销存
  | 'inventory_stock'
  | 'inventory_suppliers'
  // 财务结算
  | 'finance_transactions'
  | 'finance_invoices'
  // 数据分析
  | 'analytics_overview'
  | 'analytics_products'
  // 内容与运营
  | 'content_banners'
  | 'content_reviews'
  | 'content_member_addresses'
  // 客服中心
  | 'ai_tickets'
  | 'ai_knowledge'
  | 'ai_agent'
  | 'ai_agent_schedule'
  | 'ai_agent_stats'
  // 权限与系统
  | 'system_users' 
  | 'roles' 
  | 'menus' 
  | 'authorization' 
  | 'data_permissions' 
  | 'settings'
  | 'login_logs'
  | 'operation_logs';

export type ProductStatus = 'active' | 'inactive';
/**
 * 商品类目标识，直接沿用后台 catalog_category.category_code。
 * 类目是一棵树且可以增删，因此不再收窄为固定字面量联合，避免新类目被静默映射成错误分类。
 */
export type ProductCategory = string;

export interface Product {
  id: string;
  name: string;
  productCode?: string;
  /** 后端商品类目主键，新增/编辑商品时用于提交真实类目关系。 */
  categoryId?: number;
  category: ProductCategory;
  categoryName: string;
  price: number;
  originalPrice?: number;
  costPrice?: number;
  stock: number;
  safetyStock?: number;
  status: ProductStatus;
  imageUrl: string;
  sku: string;
  salesCount: number;
  createdAt: string;
  description?: string;
  tags?: string[];
}

export type OrderStatus = 'pending_payment' | 'pending_shipment' | 'shipped' | 'completed' | 'cancelled' | 'refunding' | 'refunded';

export interface OrderItem {
  productId: string;
  productName: string;
  price: number;
  quantity: number;
  imageUrl: string;
  sku?: string;
}

export interface LogisticsStep {
  time: string;
  title: string;
  desc: string;
  status: 'completed' | 'current' | 'pending';
}

export interface Order {
  id: string;
  orderNumber: string;
  createdAt: string;
  customerName: string;
  customerPhone: string;
  customerAvatar?: string;
  amount: number;
  paymentMethod: 'wechat' | 'alipay' | 'card';
  status: OrderStatus;
  /** 服务端独立审核状态：pending/approved/rejected。 */
  auditStatus?: 'pending' | 'approved' | 'rejected';
  auditRemark?: string;
  items: OrderItem[];
  shippingAddress: string;
  trackingNumber?: string;
  shippingCarrier?: string;
  notes?: string;
  buyerMessage?: string;
  sellerNote?: string;
  flagColor?: 'red' | 'yellow' | 'green' | 'blue' | 'purple';
  discountAmount?: number;
  freightAmount?: number;
  invoiceTitle?: string;
  taxId?: string;
  refundReason?: string;
  refundAmount?: number;
  refundStatus?: 'none' | 'pending' | 'approved' | 'rejected';
  logisticsSteps?: LogisticsStep[];
  /** 服务端订单乐观锁版本，用于备注与标旗并发更新。 */
  version?: number;
}

export type UserStatus = 'active' | 'suspended';

export interface User {
  id: string;
  userCode: string;
  name: string;
  phone: string;
  avatar?: string;
  email: string;
  registeredAt: string;
  totalSpent: number;
  orderCount: number;
  status: UserStatus;
  tier: 'regular' | 'silver' | 'gold' | 'platinum';
  lastActive: string;
  balance?: number;
  points?: number;
  tags?: string[];
  notes?: string;
  growthValue?: number;
}

export interface Role {
  id: string;
  roleKey: string;
  roleName: string;
  roleSort: number;
  status: 'active' | 'inactive';
  dataScope: 'all' | 'custom' | 'dept' | 'dept_and_sub' | 'self';
  customDeptIds?: string[];
  permissionKeys: string[];
  userCount: number;
  createdAt: string;
  description: string;
}

export type MenuType = 'directory' | 'menu' | 'button';

export interface MenuItem {
  id: string;
  parentId: string | null;
  title: string;
  icon?: string;
  type: MenuType;
  path?: string;
  component?: string;
  permission?: string;
  sort: number;
  visible: boolean;
  status: 'active' | 'inactive';
  children?: MenuItem[];
}

export interface SystemUser {
  id: string;
  username: string;
  realName: string;
  avatar?: string;
  deptId: string;
  deptName: string;
  roles: string[];
  roleNames: string[];
  phone: string;
  email: string;
  status: 'active' | 'inactive';
  createdAt: string;
  lastLoginTime: string;
  lastLoginIp: string;
  dataScope: 'all' | 'custom' | 'dept' | 'dept_and_sub' | 'self';
}

export interface Department {
  id: string;
  parentId: string | null;
  name: string;
  code: string;
  leader: string;
  phone: string;
  status: 'active' | 'inactive';
  sort: number;
  children?: Department[];
}

export interface DataRule {
  id: string;
  ruleName: string;
  module: 'orders' | 'products' | 'users' | 'finance' | 'inventory';
  moduleName: string;
  roleId: string;
  roleName: string;
  scopeType: 'all' | 'dept_and_sub' | 'dept' | 'self' | 'custom';
  customDeptIds?: string[];
  fieldMasks: string[];
  filterCondition?: string;
  status: 'active' | 'inactive';
  updatedAt: string;
}

export interface TodoItem {
  id: string;
  title: string;
  subtitle: string;
  type: 'refund' | 'stock_alert' | 'message';
  count?: number;
  urgent: boolean;
  linkTab?: NavigationTab;
}

export interface DailySalesData {
  date: string;
  fullDate?: string;
  sales: number; // 订单金额 (¥)
  volume: number; // 商品销量 (件)
  orders: number; // 订单笔数 (单)
  avgOrderValue?: number;
}
