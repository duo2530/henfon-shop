/**
 * 门户整页视图的路由。
 *
 * 门户没有引入路由库，视图状态靠 `location.hash` 标识：刷新、分享链接、浏览器前进后退都能回到同一屏，
 * 而且构建产物直接交给静态托管即可，不需要服务端配合 rewrite。
 *
 * 解析规则只有一条要记住：**只认 `#/` 开头的 hash**。
 * 页面里的正文锚点（例如跳过导航用的 `#portal-main-content`）改写的是同一个 `location.hash`，
 * 若一并当成路由解析，用户点一次跳过链接就会被踢回首页。这类 hash 返回 null，
 * 由调用方保持当前视图不变。
 */

import { ORDER_STATUS_FILTERS } from './orderDisplay';
import type { OrderStatusFilter } from './orderDisplay';

/** 商品详情页的页签，同时也是查询串 `?tab=` 的取值。 */
export type ProductTab = 'details' | 'specs' | 'reviews';

const PRODUCT_TABS: readonly ProductTab[] = ['details', 'specs', 'reviews'];

function readProductTab(value: string | null): ProductTab | undefined {
  return PRODUCT_TABS.includes(value as ProductTab) ? (value as ProductTab) : undefined;
}

/**
 * 个人中心的子视图，同时也是路径段 `/account/<section>` 的取值。
 *
 * `profile` 是缺省值，地址上不给它单独留一段（`#/account` 就是它），
 * 这样同一个画面只有一种地址；带 `profile` 的地址仍能解析，只是归一化后与前者相同。
 */
export type AccountSection = 'profile' | 'coupons' | 'reviews' | 'tickets';

const ACCOUNT_SECTIONS: readonly AccountSection[] = ['profile', 'coupons', 'reviews', 'tickets'];

function readAccountSection(value: string | undefined): AccountSection | undefined {
  return ACCOUNT_SECTIONS.includes(value as AccountSection) ? (value as AccountSection) : undefined;
}

/** 订单中心的状态页签取值就是 `ORDER_STATUS_FILTERS` 的 key，直接复用那份清单，不另抄一份。 */
function readOrderStatus(value: string | null): OrderStatusFilter | undefined {
  // `all` 是缺省值，不进地址：同一个画面只留一种地址。
  if (!value || value === 'all') return undefined;
  return ORDER_STATUS_FILTERS.some((item) => item.key === value) ? (value as OrderStatusFilter) : undefined;
}

/**
 * 订单 ID 进地址前先过一道白名单。
 *
 * 服务端订单 ID 是数字串、本地演示订单是 `ord-preset-001` 这类，两种都得留；
 * 但地址栏的内容是外部输入，不做限制就等于把任意文本塞进 `location.hash` 再回读。
 */
function readOrderId(value: string | null): string | undefined {
  if (!value) return undefined;
  return /^[A-Za-z0-9_-]{1,40}$/.test(value) ? value : undefined;
}

/** 高亮定位用的商品主键：非正整数一律当没带。 */
function readAccountProductId(value: string | null): number | undefined {
  const parsed = Number(value);
  return value && Number.isInteger(parsed) && parsed > 0 ? parsed : undefined;
}

/**
 * 一条整页视图路由。
 *
 * 消费方一律按 `view` 分支判断，所以新增视图只需往这个联合类型里加成员，
 * 已有分支不受影响；需要参数的新视图（如商品详情）把参数直接挂在自己的成员上。
 */
export type PortalRoute =
  | { view: 'home' }
  | {
      view: 'orders';
      /** 左栏选中的状态页签，缺省 `all`。 */
      status?: OrderStatusFilter;
      /** 右栏选中的订单；窄屏也靠它判断停在列表还是详情。 */
      order?: string;
    }
  | { view: 'wishlist' }
  | { view: 'compare' }
  | {
      view: 'product';
      /** 商品 ID，统一成 `prod-<数字>` 一种写法。 */
      productId: string;
      /** 进页时选中的页签，订单评价入口会带上 `reviews`。 */
      tab?: ProductTab;
      /** 订单入口带入的已购规格，作为评价表单默认值。 */
      variant?: string;
    }
  | {
      view: 'account';
      /** 子视图；`profile` 是缺省值，等价于不写。 */
      section?: AccountSection;
      /** 「我的评价」里要高亮定位的评价所属商品主键。 */
      productId?: number;
    };

/** 路由 hash 的前缀。正文锚点不带这个前缀，两者因此可以区分。 */
export const PORTAL_ROUTE_PREFIX = '#/';

export const HOME_ROUTE: PortalRoute = { view: 'home' };

/**
 * 把商品 ID 统一成 `prod-<数字>`。
 *
 * 地址栏、Banner 的 `linkTarget`、订单明细里的写法并不统一（`7` 与 `prod-7` 都有），
 * 归一化之后同一个商品只有一种地址，刷新与分享链接才不会落到两个 URL 上。
 */
export function normalizeProductId(raw: string): string {
  const value = raw.trim();
  return /^\d+$/.test(value) ? `prod-${value}` : value;
}

/**
 * 解析 `location.hash`。
 *
 * 三种输入要分清：空 hash 是站点根地址（首页）；`#/orders` 这类是路由；`#portal-main-content`
 * 这类正文锚点不是路由，必须返回 null 让调用方保持当前视图 —— 否则用户点一次跳过导航链接，
 * 整页视图就被打回首页。
 *
 * @param hash 形如 `#/orders` 的 hash（含前导 `#`）
 * @returns 路由对象；返回 **null** 表示「这不是一条路由地址」，调用方应保持当前视图不变
 */
export function parsePortalRoute(hash: string): PortalRoute | null {
  if (hash === '') return HOME_ROUTE;
  if (!hash.startsWith(PORTAL_ROUTE_PREFIX)) return null;
  const [rawPath, rawQuery = ''] = hash.slice(PORTAL_ROUTE_PREFIX.length).split('?');
  const segments = rawPath.split('/').filter(Boolean);
  const head = segments[0];
  if (!head) return HOME_ROUTE;
  if (head === 'orders') {
    const query = new URLSearchParams(rawQuery);
    const status = readOrderStatus(query.get('status'));
    const order = readOrderId(query.get('order'));
    return {
      view: 'orders',
      ...(status ? { status } : {}),
      ...(order ? { order } : {}),
    };
  }
  if (head === 'wishlist') return { view: 'wishlist' };
  if (head === 'compare') return { view: 'compare' };
  if (head === 'account') {
    const section = readAccountSection(segments[1] ? decodeURIComponent(segments[1]) : undefined);
    // 认不出的子视图落回资料页，而不是回首页：路径主体（个人中心）是认得的。
    const query = new URLSearchParams(rawQuery);
    const productId = section === 'reviews' ? readAccountProductId(query.get('product')) : undefined;
    return {
      view: 'account',
      ...(section && section !== 'profile' ? { section } : {}),
      ...(productId ? { productId } : {}),
    };
  }
  if (head === 'product') {
    const productId = normalizeProductId(segments[1] ? decodeURIComponent(segments[1]) : '');
    // 只有 `#/product` 而没带 ID 是残缺地址，回首页，不停在一个没有商品的商品页上。
    if (!productId) return HOME_ROUTE;
    const query = new URLSearchParams(rawQuery);
    const tab = readProductTab(query.get('tab'));
    const variant = query.get('variant') || undefined;
    return {
      view: 'product',
      productId,
      ...(tab ? { tab } : {}),
      ...(variant ? { variant } : {}),
    };
  }
  // 认不出的路径回首页：地址栏能表达的状态与实际画面要一致，不能留在原地装没事。
  return HOME_ROUTE;
}

/**
 * 生成路由对应的 hash。
 *
 * @returns 首页返回空串 —— 首页就是站点根地址，不该在 URL 上留一个多余的 `#/`。
 */
export function buildPortalHash(route: PortalRoute): string {
  switch (route.view) {
    case 'orders': {
      const query = new URLSearchParams();
      if (route.status && route.status !== 'all') query.set('status', route.status);
      if (route.order) query.set('order', route.order);
      const search = query.toString();
      return `#/orders${search ? `?${search}` : ''}`;
    }
    case 'wishlist':
      return '#/wishlist';
    case 'compare':
      return '#/compare';
    case 'account': {
      // 资料页是缺省子视图，不留路径段；`#/account` 与 `#/account/profile` 归一化后同一地址。
      const path = route.section && route.section !== 'profile' ? `#/account/${route.section}` : '#/account';
      const query = new URLSearchParams();
      if (route.section === 'reviews' && route.productId) query.set('product', String(route.productId));
      const search = query.toString();
      return `${path}${search ? `?${search}` : ''}`;
    }
    case 'product': {
      const query = new URLSearchParams();
      if (route.tab) query.set('tab', route.tab);
      if (route.variant) query.set('variant', route.variant);
      const search = query.toString();
      return `#/product/${encodeURIComponent(route.productId)}${search ? `?${search}` : ''}`;
    }
    default:
      return '';
  }
}
