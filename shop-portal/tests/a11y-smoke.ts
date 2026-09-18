import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const app = readFileSync(new URL('../src/App.tsx', import.meta.url), 'utf8');
const navbar = readFileSync(new URL('../src/components/Navbar.tsx', import.meta.url), 'utf8');
const productCard = readFileSync(new URL('../src/components/ProductCard.tsx', import.meta.url), 'utf8');
const couponBanner = readFileSync(new URL('../src/components/CouponCenterBanner.tsx', import.meta.url), 'utf8');
const couponModal = readFileSync(new URL('../src/components/CouponCenterModal.tsx', import.meta.url), 'utf8');
const couponCenter = readFileSync(new URL('../src/components/CouponCenter.tsx', import.meta.url), 'utf8');
const userProfile = readFileSync(new URL('../src/components/UserProfileModal.tsx', import.meta.url), 'utf8');
const index = readFileSync(new URL('../index.html', import.meta.url), 'utf8');

assert.match(index, /<html lang="zh-CN">/);
assert.match(app, /href="#portal-main-content"/);
assert.match(app, /id="portal-main-content"[^>]*tabIndex=\{-1\}/);
assert.match(app, /aria-pressed=\{isCompareMode\}/);
assert.match(navbar, /aria-label="搜索商品、品牌或型号"/);
assert.match(navbar, /aria-expanded=\{isSearchFocused\}/);
assert.match(productCard, /onKeyDown=\{\(event\) =>/);
assert.match(productCard, /role="button"/);
// 类目树常驻左侧，窄屏走抽屉：抽屉要有可访问名称，顶部要有对应的打开按钮。
assert.ok(app.includes('lg:grid-cols-[220px_minmax(0,1fr)]'));
assert.match(app, /aria-label="全部分类"/);
assert.match(navbar, /aria-label="打开全部分类"/);
// 左栏是浮层形态、抽屉是展开形态，两种进入方式的语义不能混用。
assert.match(app, /variant="flyout"/);
// 轮播位是常驻运营位：只随搜索态隐藏，不能因为切了类目就消失。
const heroIndex = app.indexOf('<HeroBanner');
assert.ok(heroIndex > 0, 'App.tsx 里应当渲染 HeroBanner');
const heroGate = app.slice(Math.max(0, heroIndex - 200), heroIndex);
assert.match(heroGate, /\{!searchQuery && \(/);
assert.ok(!heroGate.includes('ALL_CATEGORY_ID'), '轮播位的渲染条件不应耦合类目选择');
// 筛选工具条分主行（结果 + 排序/视图）与筛选行两段：原先 7 个控件挤一行，在 988px 主内容里必定换行错位。
assert.ok(app.includes('筛选行：属性筛选与对比工具'), '筛选工具条应为主行 + 筛选行两段结构');
// 左栏占掉 220px 后 lg（1024）主内容只剩约 730px，商品网格在 lg 排 4 列会把卡片压到 170px 左右。
assert.ok(app.includes('xl:grid-cols-4'), '商品网格应在 xl 断点才排 4 列');
assert.ok(!app.includes('lg:grid-cols-4'), '商品网格不应在 lg 断点排 4 列');
// 券墙搬进弹层后：锚点跟着首页横幅走，弹层要有可访问名称，并且键盘能退出。
assert.match(couponBanner, /id="coupon-center-section"/);
assert.match(couponBanner, /aria-label="领券中心"/);
assert.match(couponModal, /aria-modal="true"/);
assert.match(couponModal, /aria-label="领券中心"/);
assert.match(couponModal, /aria-label="关闭领券中心"/);
assert.match(couponModal, /event\.key === 'Escape'/);
// 券墙容器是 5%~10% 的琥珀半透明底，弹层外壳必须垫一层不透明底色；
// 少了它，深色文字会直接压在 bg-black/60 的遮罩上。
const dialogIndex = couponModal.indexOf('role="dialog"');
assert.ok(dialogIndex > 0, 'CouponCenterModal 里应当有 role="dialog" 容器');
assert.match(
  couponModal.slice(dialogIndex, dialogIndex + 400),
  /bg-white/,
  '券弹层外壳需要不透明底色，否则半透明券墙会透出遮罩导致文字看不清',
);

// 券型（cash / discount / shipping）与物流状态（SHIPPED / IN_TRANSIT…）在库里都是英文枚举，
// 界面必须经过中文映射，不能直接渲染原值。
assert.match(couponCenter, /couponTagLabel\(coupon\.tag\)/);
assert.match(userProfile, /couponTagLabel\(c\.tag\)/);
assert.match(app, /logisticsStatusLabel\(event\.logisticsStatus\)/);

console.log('shop-portal 无障碍静态检查通过：跳过链接、中文语言、搜索 ARIA、左侧类目树（浮层与抽屉两态）、轮播位常驻、商品区栅格、领券横幅与券弹层（含不透明底色、英文枚举中文映射）已覆盖');
