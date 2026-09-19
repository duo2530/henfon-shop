import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const app = readFileSync(new URL('../src/App.tsx', import.meta.url), 'utf8');
const navbar = readFileSync(new URL('../src/components/Navbar.tsx', import.meta.url), 'utf8');
const productCard = readFileSync(new URL('../src/components/ProductCard.tsx', import.meta.url), 'utf8');
const couponBanner = readFileSync(new URL('../src/components/CouponCenterBanner.tsx', import.meta.url), 'utf8');
const couponModal = readFileSync(new URL('../src/components/CouponCenterModal.tsx', import.meta.url), 'utf8');
const couponCenter = readFileSync(new URL('../src/components/CouponCenter.tsx', import.meta.url), 'utf8');
const userProfile = readFileSync(new URL('../src/components/UserProfileModal.tsx', import.meta.url), 'utf8');
const heroBanner = readFileSync(new URL('../src/components/HeroBanner.tsx', import.meta.url), 'utf8');
const categoryRail = readFileSync(new URL('../src/components/CategoryRail.tsx', import.meta.url), 'utf8');
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
assert.ok(app.includes('lg:grid-cols-[260px_minmax(0,1fr)]'));
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
// 运营图是浅底商品实拍，压暗到 30% 垫在深色底上只会糊成一团灰，图与字互相拖累。
// 现在改成白底展台：文案在左用深色字，商品图在右整幅展示不压暗。
assert.doesNotMatch(heroBanner, /opacity-30/, '轮播图不应再被压暗');
assert.doesNotMatch(heroBanner, /bg-zinc-900 text-white/, '轮播容器不应再是深色底 + 白字');
assert.match(heroBanner, /object-cover/);
assert.match(heroBanner, /sm:w-\[46%\]/, '文案区必须与图片分列：横向渐隐盖不住会铺到 94% 宽的副标题');
// 手机端文案占满整行，只能靠纵向白罩把垂直居中那一段压亮；这层罩到 sm 就必须撤掉。
assert.match(heroBanner, /bg-gradient-to-t[^"]*sm:hidden/);
// 左箭头要贴在图片列左缘，落在 left-4 会压在标题与正文上。
assert.match(heroBanner, /left-\[calc\(46%\+0\.75rem\)\]/, '左箭头应避开左侧文案区');
// 左右箭头原先是没有可访问名称的裸按钮。
assert.match(heroBanner, /aria-label="上一张"/);
assert.match(heroBanner, /aria-label="下一张"/);
// 左栏卡片 padding 是 p-4，浮层 left 偏移必须同步为 1rem，否则浮层会往左压进左栏一截。
assert.ok(categoryRail.includes('left-[calc(100%+1rem)]'), '左栏浮层偏移应与卡片 padding 对齐');
// 筛选工具条分主行（结果 + 排序/视图）与筛选行两段：原先 7 个控件挤一行，在 988px 主内容里必定换行错位。
assert.ok(app.includes('筛选行：属性筛选与对比工具'), '筛选工具条应为主行 + 筛选行两段结构');
// 左栏占掉 260px 后 lg（1024）主内容只剩约 708px，商品网格在 lg 排 4 列会把卡片压到 170px 以下。
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

// 商品详情接口必须把 VIDEO 媒体取出来交给 videoUrl：详情弹层的播放器只认这个字段，
// 而图片位是按 <img> 渲染的，把 mp4 混进 images 只会得到一个加载失败的破图。
const portalApi = readFileSync(new URL('../src/api/portalApi.ts', import.meta.url), 'utf8');
assert.match(portalApi, /product\.videoUrl\s*=/, '详情接口应把视频地址写入 videoUrl，而不是丢弃视频媒体');
assert.match(portalApi, /toLowerCase\(\) === 'video'/);

// 轮播的商品跳转手里只有一个 ID，商品不一定落在当前分页的商品列表里，
// 必须交给 openProductById 用详情接口补全；不能落回本地演示商品 ——
// 演示商品与线上商品只是 ID 撞号，落回去会弹出完全不相干的商品，
// 还会把演示数据里的外链视频一起渲进详情弹层（表现为视频加载超时 + play() 被打断）。
assert.match(app, /const openProductById = async/, 'App.tsx 应提供按 ID 打开商品的入口');
assert.match(app, /function createProductStub\(/, 'App.tsx 应提供只有 ID 的商品占位对象');
const bannerProductIndex = app.indexOf("if (linkType === 'PRODUCT')");
assert.ok(bannerProductIndex > 0, 'App.tsx 里应当有 Banner 的商品跳转分支');
const bannerProductBranch = app.slice(bannerProductIndex, bannerProductIndex + 300);
assert.ok(bannerProductBranch.includes('openProductById(target)'), 'Banner 商品跳转应走按 ID 打开的入口');
assert.ok(!bannerProductBranch.includes('PRODUCTS'), 'Banner 商品跳转不得回落到本地演示商品');

// 只有占位对象时（标题为空）不能把空骨架铺开；play() 因 pause()/卸载抛出的 AbortError 属正常中断，不能当错误刷控制台。
const quickView = readFileSync(new URL('../src/components/ProductQuickView.tsx', import.meta.url), 'utf8');
assert.match(quickView, /!props\.product\.title/);
assert.match(quickView, /function playVideoSafely\(/);
assert.match(quickView, /error\.name === 'AbortError'/);

console.log('shop-portal 无障碍静态检查通过：跳过链接、中文语言、搜索 ARIA、左侧类目树（浮层与抽屉两态）、轮播位常驻、商品区栅格、领券横幅与券弹层（含不透明底色、英文枚举中文映射）、商品详情视频位与轮播商品跳转无演示数据兜底已覆盖');
