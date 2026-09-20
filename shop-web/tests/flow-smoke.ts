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
// 登录页保持单栏居中：不要回到「左侧蓝渐变品牌块 + 网点/模糊光斑」那版装饰。
assert.doesNotMatch(login, /<aside/);
assert.doesNotMatch(login, /blur-\[/);
assert.doesNotMatch(login, /radial-gradient/);
// 背景动态元素只是装饰层：不进无障碍树、不接管指针，且系统开启「减少动效」时不跑视差。
assert.match(login, /aria-hidden="true" className="pointer-events-none fixed inset-0 -z-10 overflow-hidden"/);
assert.match(login, /login-bg-grid/);
assert.match(login, /prefers-reduced-motion: reduce/);
// 背景照片：路径必须走 BASE_URL 前缀（部署到子路径下不会 404），且必须有白纱压住可见度，
// 否则照片会跟卡片争视觉。图片本身是 JPEG，体积守住上限，防止有人塞一张几 MB 的原图进来。
assert.match(login, /url\(\$\{import\.meta\.env\.BASE_URL\}login-bg\.jpg\)/);
assert.match(login, /from-white\/66 via-white\/52 to-white\/76/);
assert.match(login, /from-white\/55 via-white\/20 to-white\/55/);
const loginBg = readFileSync(new URL('../public/login-bg.jpg', import.meta.url));
assert.equal(loginBg[0], 0xff);
assert.equal(loginBg[1], 0xd8);
assert.ok(loginBg.length > 20 * 1024 && loginBg.length < 400 * 1024, `登录页背景图体积异常：${Math.round(loginBg.length / 1024)}KB`);
// 背景动画定义在 index.css：网格漂移一个周期正好一个格距（32px），否则循环处会跳一下；
// 流线终点必须落在视口外（起点 -128px、终点 100vw + 128px），否则会看见它凭空出现。
const indexCss = readFileSync(new URL('../src/index.css', import.meta.url), 'utf8');
assert.match(indexCss, /\.login-bg-grid \{/);
assert.match(indexCss, /background-size: 32px 32px/);
assert.match(indexCss, /@keyframes login-grid-drift \{/);
assert.match(indexCss, /translate3d\(-32px, -32px, 0\)/);
assert.match(indexCss, /@keyframes login-line-sweep \{/);
assert.match(indexCss, /translate3d\(calc\(100vw \+ 256px\), 0, 0\)/);
assert.match(login, /className="w-full max-w-\[400px\]"/);
// 登录页不再外露演示账号与说明文案（用户明确要求去掉）。
assert.doesNotMatch(login, /演示账号|一键填入|DEMO_ACCOUNT/);
assert.doesNotMatch(login, /使用后台账号登录/);
// 「记住密码」必须真的读写成对（门户那个「30天内免登录」就是只 setState 的摆设，别在这里重演）。
assert.match(login, /REMEMBERED_PASSWORD_KEY = 'henfon\.admin\.remembered-password'/);
assert.match(login, /useState\(\(\) => localStorage\.getItem\(REMEMBERED_PASSWORD_KEY\)/);
assert.match(login, /localStorage\.setItem\(REMEMBERED_PASSWORD_KEY, password\)/);
assert.match(login, /localStorage\.removeItem\(REMEMBERED_PASSWORD_KEY\)/);
assert.doesNotMatch(login, /记住用户名/);
// 标题「管理员登录」已去掉（品牌名用 h1 承担页面标题语义），验证码提示直接说「验证码」。
assert.doesNotMatch(login, /管理员登录/);
assert.match(login, /placeholder="请输入验证码"/);
assert.doesNotMatch(login, /请输入右侧字符/);
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
    // 纯装饰层（aria-hidden + pointer-events-none 的整屏背景）不是弹层，不需要锁滚动。
    if (source.includes('pointer-events-none fixed inset-0')) return false;
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
