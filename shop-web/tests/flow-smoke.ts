import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const api = readFileSync(new URL('../src/api/adminApi.ts', import.meta.url), 'utf8');
const login = readFileSync(new URL('../src/components/auth/AdminLogin.tsx', import.meta.url), 'utf8');
const products = readFileSync(new URL('../src/components/products/ProductManagementView.tsx', import.meta.url), 'utf8');
const orders = readFileSync(new URL('../src/components/orders/OrderManagementView.tsx', import.meta.url), 'utf8');
const analytics = readFileSync(new URL('../src/components/analytics/AnalyticsOverviewView.tsx', import.meta.url), 'utf8');

// 管理端当前无浏览器运行时依赖，先用源码契约冒烟保障核心流程入口未被误删。
assert.match(api, /login|auth/i);
assert.match(api, /auth\/refresh/);
assert.match(api, /refreshAdminToken/);
assert.match(api, /catalog\/products/);
assert.match(api, /trade\/orders/);
assert.match(login, /登录|username|password/);
assert.match(products, /Product|商品/);
assert.match(orders, /发货|shipping|shipment/i);
assert.match(analytics, /getReportingDashboardMetrics/);
assert.match(analytics, /getReportingSalesTrend/);
assert.doesNotMatch(analytics, /trafficHourly|channelShare|funnelData/);
assert.doesNotMatch(analytics, /246,880|18,430|128,400|5\.0%|248\.50/);

const detailModal = readFileSync(new URL('../src/components/products/ProductDetailModal.tsx', import.meta.url), 'utf8');
const scrollLock = readFileSync(new URL('../src/hooks/useBodyScrollLock.ts', import.meta.url), 'utf8');

// 弹层打开期间必须锁住底层文档滚动，否则滚到底后底层页面会跟着滚。
assert.match(products, /useBodyScrollLock/);
assert.match(detailModal, /useBodyScrollLock/);
assert.match(scrollLock, /body\.style\.overflow = 'hidden'/);

// 商品挂在三级类目上：按类目筛选必须展开子树，商品列表必须按页取全量。
const adminContext = readFileSync(new URL('../src/context/AdminContext.tsx', import.meta.url), 'utf8');
assert.match(products, /collectCategorySubtreeIds/);
assert.match(adminContext, /listAllCatalogProducts/);

console.log('shop-web 管理流程冒烟检查通过：登录 → 商品 → 订单/发货 → 经营报表入口均存在，弹层滚动锁在详情与商品弹层均已接入');
