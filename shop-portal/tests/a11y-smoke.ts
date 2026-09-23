import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
/** 仓库 core.autocrlf=true 时工作区文件带 CRLF，断言里按 LF 锚定会失配，读取时统一归一。 */
const readSource = (path: URL) => readFileSync(path, 'utf8').replace(/\r\n/g, '\n');
import { parsePortalRoute } from '../src/utils/portalRoute';

const app = readSource(new URL('../src/App.tsx', import.meta.url));
const navbar = readSource(new URL('../src/components/Navbar.tsx', import.meta.url));
const productCard = readSource(new URL('../src/components/ProductCard.tsx', import.meta.url));
const couponBanner = readSource(new URL('../src/components/CouponCenterBanner.tsx', import.meta.url));
const couponModal = readSource(new URL('../src/components/CouponCenterModal.tsx', import.meta.url));
const couponCenter = readSource(new URL('../src/components/CouponCenter.tsx', import.meta.url));
const userProfile = readSource(new URL('../src/components/UserProfilePage.tsx', import.meta.url));
const heroBanner = readSource(new URL('../src/components/HeroBanner.tsx', import.meta.url));
const categoryRail = readSource(new URL('../src/components/CategoryRail.tsx', import.meta.url));
const index = readSource(new URL('../index.html', import.meta.url));

assert.match(index, /<html lang="zh-CN">/);
assert.match(app, /href="#portal-main-content"/);
assert.match(app, /id="portal-main-content"[^>]*tabIndex=\{-1\}/);
// 跳过导航链接写的是 location.hash，整页视图路由也读 location.hash。
// 路由解析必须只认 `#/` 前缀、把正文锚点当成「不是路由」，否则键盘用户点一次跳过链接
// 就会被踢回首页 —— 无障碍功能反过来把页面状态弄丢。
assert.equal(parsePortalRoute('#portal-main-content'), null);
// 光靠解析兜住还不够：跳过链接真跳过去仍会改写 location.hash，把整页视图的地址冲掉，
// 此时刷新就掉回首页。链接自己拦下默认跳转、手动移焦点，双保险。
assert.match(app, /onClick=\{\(event\) => \{\s*event\.preventDefault\(\);\s*document\.getElementById\('portal-main-content'\)\?\.focus\(\);/);
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

// 个人中心也是整页视图：遮罩、滚动锁、Escape 关闭、关闭按钮这些弹层语义都得去掉，
// 否则键盘用户会停在一个「按了没反应」的控件上。
assert.doesNotMatch(userProfile, /useBodyScrollLock/);
assert.doesNotMatch(userProfile, /'Escape'/);
assert.doesNotMatch(userProfile, /aria-modal/);
assert.match(userProfile, /<h1 className="text-xl font-bold text-zinc-900">个人中心<\/h1>/);
assert.match(userProfile, /aria-label="个人中心分栏"/);
assert.match(userProfile, /aria-label="面包屑"/);
// 三个入口（顶栏头像、订单「查看」、商品页「查看我的评价」）统一走路由，App 里不该再有个人中心弹层。
assert.doesNotMatch(app, /UserProfileModal/);
assert.match(app, /import \{ UserProfilePage \}/);
assert.match(app, /route\.view === 'account' && currentUser/);
// 未登录直接敲 #/account 要回首页并拉起登录，不能停在一张空账号页上。
const accountGateIndex = app.indexOf("if (route.view !== 'account' || currentUser) return;");
assert.ok(accountGateIndex > 0, 'App.tsx 应有未登录进个人中心的兜底');
assert.match(app.slice(accountGateIndex, accountGateIndex + 320), /navigate\(HOME_ROUTE, \{ replace: true \}\)/);

// 商品详情接口必须把 VIDEO 媒体取出来交给 videoUrl：详情弹层的播放器只认这个字段，
// 而图片位是按 <img> 渲染的，把 mp4 混进 images 只会得到一个加载失败的破图。
const portalApi = readSource(new URL('../src/api/portalApi.ts', import.meta.url));
assert.match(portalApi, /product\.videoUrl\s*=/, '详情接口应把视频地址写入 videoUrl，而不是丢弃视频媒体');
assert.match(portalApi, /toLowerCase\(\) === 'video'/);

// 轮播的商品跳转手里只有一个 ID，商品不一定落在当前分页的商品列表里，
// 必须交给 openProductById 用详情接口补全；不能落回本地演示商品 ——
// 演示商品与线上商品只是 ID 撞号，落回去会弹出完全不相干的商品，
// 还会把演示数据里的外链视频一起渲进详情弹层（表现为视频加载超时 + play() 被打断）。
assert.match(app, /const openProductById = \(/, 'App.tsx 应提供按 ID 进入商品详情的入口');
assert.match(app, /function createProductStub\(/, 'App.tsx 应提供只有 ID 的商品占位对象');
const bannerProductIndex = app.indexOf("if (linkType === 'PRODUCT')");
assert.ok(bannerProductIndex > 0, 'App.tsx 里应当有 Banner 的商品跳转分支');
const bannerProductBranch = app.slice(bannerProductIndex, bannerProductIndex + 300);
assert.ok(bannerProductBranch.includes('openProductById(target)'), 'Banner 商品跳转应走按 ID 打开的入口');
assert.ok(!bannerProductBranch.includes('PRODUCTS'), 'Banner 商品跳转不得回落到本地演示商品');

// 只有占位对象时（标题为空）不能把空骨架铺开；play() 因 pause()/卸载抛出的 AbortError 属正常中断，不能当错误刷控制台。
const quickView = readSource(new URL('../src/components/ProductDetailPage.tsx', import.meta.url));
assert.match(quickView, /!props\.product\.title/);
assert.match(quickView, /function playVideoSafely\(/);
assert.match(quickView, /error\.name === 'AbortError'/);
// 整页视图不该再有关闭语义：Escape 关页面、关闭按钮、遮罩层都会把键盘用户困在一个
// 「按了没反应」或者「按了就跳出当前状态」的控件上。
assert.doesNotMatch(quickView, /'Escape'/);
assert.doesNotMatch(quickView, /useBodyScrollLock/);

// 客服悬浮入口：只留图标不上屏文字，但无障碍名要保留，未读数也要进可读标签（与顶栏铃铛同口径）。
const supportWidget = readSource(new URL('../src/components/SupportChatWidget.tsx', import.meta.url));
const portalCss = readSource(new URL('../src/index.css', import.meta.url));

assert.ok(
  supportWidget.includes("aria-label={unread > 0 ? `在线客服，${unread} 条未读` : '在线客服'}"),
  '入口按钮的无障碍名要带上未读数',
);
const entryStart = supportWidget.indexOf('className="fixed bottom-6 right-6 z-40 h-12 w-12 rounded-full');
assert.ok(entryStart > 0, '客服入口应是右下角固定定位的圆形按钮');
const entryBlock = supportWidget.slice(entryStart, supportWidget.indexOf('</button>', entryStart));
assert.ok(!entryBlock.includes('>在线客服<'), '入口只保留图标，不再渲染「在线客服」文字');
// 闪烁只在「关窗 + 有未读 / 排队中 / 待评价」时点亮，动画本身定义在全局样式里。
assert.ok(supportWidget.includes("const blink = !open && (unread > 0 || mode === 'WAITING' || ratingTarget !== null);"));
assert.ok(supportWidget.includes("w-5 h-5${blink ? ' animate-blink' : ''}"));
assert.ok(portalCss.includes('--animate-blink: henfon-blink 1.6s ease-in-out infinite;'));
assert.ok(portalCss.includes('@keyframes henfon-blink'));
// 排队提示直接用服务端拼好的那句（含排队人数与预计等待），前端不再重写一份口径。
assert.ok(supportWidget.includes("availability.reason === 'ONLINE'"));
assert.ok(supportWidget.includes('QUEUE_POLL_INTERVAL_MS = 20000'), '排队时按 20 秒刷新可用性');
// 评价弹层：评分是 radiogroup、标签用 pressed 表达选中、未给分不能提交，且必须留「跳过」出口。
assert.ok(supportWidget.includes('role="radiogroup" aria-label="满意度评分"'));
assert.ok(supportWidget.includes('aria-pressed={ratingTags.includes(tag)}'));
assert.ok(supportWidget.includes('disabled={ratingScore < 1 || ratingSubmitting}'));
assert.ok(
  supportWidget.split('dismissRating').length - 1 >= 4,
  '评价弹层的关闭与「以后再说」应共用同一个可跳过出口',
);
// 人工服务结束后回到智能客服：长连接已经断开，聊天记录必须自己读回来。少了这一步，买家在评价
// 弹层上一按，窗口就是一片空白 —— 而这正是「门户端评价后客服窗口全白」那次报障。
// 断言按「恢复会话」这个 effect 的区块定位：returnToAi 里也有同名调用，整文件 includes 会空转。
const resumeStart = supportWidget.indexOf('刷新后把上一次的会话接回来');
const resumeBlock = supportWidget.slice(resumeStart, supportWidget.indexOf('}, [available]);', resumeStart));
assert.ok(resumeStart > 0, '刷新后恢复会话的逻辑应还在');
assert.ok(resumeBlock.includes("if (next === 'AI')"), '会话已回到 AI 时也要走「读回历史」这一支');
assert.ok(resumeBlock.includes('void loadHistory(targetId);'), 'AI 分支里要真的把历史读回来');
assert.ok(
  supportWidget.includes('const inflight = pendingIndex >= 0 ? prev.slice(Math.max(0, pendingIndex - 1)) : [];'),
  '读历史时保留正在生成的那一轮，别把本地气泡冲掉',
);
assert.ok(
  supportWidget.includes('sequence: item.sequence'),
  '历史消息要带上服务端序号，才能与界面上已有的消息对齐去重',
);
// 客服不点「结束服务」时买家不能被困住：会话挂在 HUMAN 上，提问被拦、智能客服也用不了。
// 所以接待中与排队中都要留一个买家自己的出口，走与客服结束相同的收尾。
//
// 断言要锚在「按钮的 JSX 文本节点」上（换行 + 16 空格 + 文案），不能只写 includes('结束咨询')：
// 这个四字串在文件里还出现在错误提示 '结束咨询失败，请稍后重试' 中，位置更靠前，
// 一旦按钮文案被改掉，裸 includes 仍会命中错误提示而假通过。
assert.ok(supportWidget.includes('endPortalAiAgentSession'), '买家要能主动结束人工咨询，不等客服想起来');
assert.ok(
  supportWidget.includes('\n                结束咨询\n'),
  '人工接待中要给出「结束咨询」按钮（锚在按钮文案上，别匹配到错误提示里的同名片段）',
);
assert.ok(
  supportWidget.includes('\n                取消排队\n'),
  '排队等不到人时要能取消，不能只能干等',
);
assert.ok(
  supportWidget.includes('onClick={() => void endHumanSession()}'),
  '两个出口按钮都要真的挂上处理函数，不能只是摆一段文案',
);
assert.ok(
  supportWidget.includes('await endPortalAiAgentSession(conversationId);') &&
    supportWidget.includes('returnToAi(conversationId);'),
  '买家结束后要走与客服结束相同的收尾：交回智能客服并把记录读回来',
);
// 事件类型补齐：服务端多推了 idle（空闲催办），前端不认会让它落到未处理分支。
assert.ok(portalApi.includes("| 'idle'"), '门户要认服务端的 idle 催办事件');
// 商品卡片：卡片由服务端装配，前端只负责渲染与跳转。
assert.ok(portalApi.includes("| 'product'"), '门户要认人工客服推商品的 product 事件');
assert.ok(
  portalApi.includes('cards?: PortalAiProductCard[]'),
  '历史消息与长连接消息都要带上卡片，否则刷新之后卡片就没了',
);
assert.ok(
  supportWidget.includes('const ProductCards: React.FC<{ cards?: PortalAiProductCard[]'),
  '卡片渲染要有独立组件，智能客服与人工客服共用同一个气泡样式',
);
assert.ok(
  supportWidget.includes('onClick={() => onOpen?.(card.productId)}'),
  '整张卡片可点，买家点了直接进商品详情页',
);
assert.ok(
  supportWidget.includes('onOpenProduct,') && supportWidget.includes('onOpen={onOpenProduct}'),
  '跳转能力由 App 注入，客服组件不自己拼路由',
);
// 复制链接：卡片要能带出站外，链接必须是带域名的绝对地址，按钮还得有可见文字。
assert.ok(
  supportWidget.includes('function productShareUrl(productId: number)') &&
    supportWidget.includes('${window.location.origin}${window.location.pathname}#/product/prod-${productId}'),
  '复制出去的链接要带域名，站内 hash 在微信里打不开',
);
assert.ok(
  supportWidget.includes("'复制链接'") && supportWidget.includes("'已复制'"),
  '复制按钮要有可见文字与结果反馈，不能只留一个图标',
);
assert.ok(
  supportWidget.includes('patchMessage(assistantId, { pending: false, cards: event.cards });'),
  '智能客服的卡片随 done 事件下发，不进 delta 正文',
);
assert.ok(
  supportWidget.includes('cards: item.cards'),
  '读回历史与快照时都要把卡片带上',
);
assert.ok(
  app.includes('onOpenProduct={(productId) => openProductById(`prod-${productId}`)}'),
  'App 要把商品 ID 归一成门户路由再跳转',
);

console.log('shop-portal 无障碍静态检查通过：跳过链接、中文语言、搜索 ARIA、左侧类目树（浮层与抽屉两态）、轮播位常驻、商品区栅格、领券横幅与券弹层（含不透明底色、英文枚举中文映射）、商品详情与个人中心整页（无弹层语义、页面级 h1、入口全部走路由）、客服悬浮入口（纯图标入口、条件闪烁、排队文案、评价弹层语义与结束后读回历史）、客服商品卡片（整卡可点、随 done 下发、历史读回带卡片、复制带域名的链接）已覆盖');
