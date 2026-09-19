import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { GUIDE_GROUPS, GUIDE_SECTIONS, MENU_PERMISSIONS } from '../src/guide/guideSections';

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

// 全屏弹层逐个核对：新页面漏接滚动锁时这里会失败。
// ExportCenter 是按钮旁的下载下拉（透明点击层，不是弹层），唯一不接入的文件。
const componentsDir = new URL('../src/components', import.meta.url);
const componentsRoot = fileURLToPath(componentsDir);
const unlockedModals = readdirSync(componentsDir, { withFileTypes: true, recursive: true })
  .filter((entry) => entry.isFile() && entry.name.endsWith('.tsx'))
  .map((entry) => join(entry.parentPath ?? entry.path, entry.name))
  .filter((file) => {
    const source = readFileSync(file, 'utf8');
    return source.includes('fixed inset-0') && !source.includes('useBodyScrollLock');
  })
  .map((file) => relative(componentsRoot, file).replace(/\\/g, '/'));
assert.deepEqual(unlockedModals, ['layout/ExportCenter.tsx']);

// 操作指南走独立页面入口：guide.html + guide-main 挂载，顶栏只留一个指向它的新标签页链接。
const guideHtml = readFileSync(new URL('../guide.html', import.meta.url), 'utf8');
const guideMain = readFileSync(new URL('../src/guide-main.tsx', import.meta.url), 'utf8');
const topNavbar = readFileSync(new URL('../src/components/layout/TopNavbar.tsx', import.meta.url), 'utf8');
assert.match(guideHtml, /src="\/src\/guide-main\.tsx"/);
assert.match(guideMain, /OperationsGuidePage/);
assert.match(topNavbar, /guide\.html/);
assert.match(topNavbar, /target="_blank"/);

// 指南内容与菜单口径同源：带菜单路由的章节必须和权限速查表逐字对上，否则一边改了另一边会漂。
const menuByRoute = new Map(MENU_PERMISSIONS.map(([, route, permission]) => [route, permission]));
assert.equal(new Set(MENU_PERMISSIONS.map(([, route]) => route)).size, MENU_PERMISSIONS.length, '权限速查表路由重复');
assert.equal(new Set(MENU_PERMISSIONS.map(([, , code]) => code)).size, MENU_PERMISSIONS.length, '权限速查表权限编码重复');
assert.equal(new Set(GUIDE_SECTIONS.map((section) => section.id)).size, GUIDE_SECTIONS.length, '指南章节 id 重复');
assert.deepEqual(
  GUIDE_SECTIONS.filter((section) => !GUIDE_GROUPS.includes(section.group as (typeof GUIDE_GROUPS)[number])),
  [],
  '指南章节的分组不在 GUIDE_GROUPS 里',
);
assert.deepEqual(
  GUIDE_SECTIONS.filter((section) => !section.summary).map((section) => section.id),
  [],
  '指南章节缺 summary',
);
const routeMismatch = GUIDE_SECTIONS.filter(
  (section) => section.route?.startsWith('/') && menuByRoute.get(section.route) !== section.permission,
).map((section) => `${section.title}: ${section.route} -> ${section.permission}`);
assert.deepEqual(routeMismatch, [], '指南章节的菜单路由与权限编码和速查表不一致');

// 商品挂在三级类目上：按类目筛选必须展开子树，商品列表必须按页取全量。
const adminContext = readFileSync(new URL('../src/context/AdminContext.tsx', import.meta.url), 'utf8');
assert.match(products, /collectCategorySubtreeIds/);
assert.match(adminContext, /listAllCatalogProducts/);

// 类目停用按父链隐藏：管理端要标注「随上级停用」、置灰开关，并在停用有下级的类目时二次确认。
const categoryView = readFileSync(new URL('../src/components/products/CategoryManagementView.tsx', import.meta.url), 'utf8');
assert.match(categoryView, /collectDisabledBranchIds/);
assert.match(categoryView, /随上级停用/);
assert.match(categoryView, /子类目会一并从门户导航/);

console.log('shop-web 管理流程冒烟检查通过：登录 → 商品 → 订单/发货 → 经营报表入口均存在，全屏弹层滚动锁、类目父链隐藏与操作指南独立入口已接入');
