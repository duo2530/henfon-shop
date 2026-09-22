import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const app = readFileSync(new URL('../src/App.tsx', import.meta.url), 'utf8');
const sidebar = readFileSync(new URL('../src/components/layout/Sidebar.tsx', import.meta.url), 'utf8');
const navbar = readFileSync(new URL('../src/components/layout/TopNavbar.tsx', import.meta.url), 'utf8');
const index = readFileSync(new URL('../index.html', import.meta.url), 'utf8');

assert.match(index, /<html lang="zh-CN">/);
assert.match(app, /href="#main-content-canvas"/);
assert.match(app, /id="main-content-canvas"[^>]*tabIndex=\{-1\}/);
assert.match(sidebar, /aria-label="后台主导航"/);
assert.match(sidebar, /aria-expanded=\{expanded\}/);
assert.match(sidebar, /aria-current=\{active \? 'page' : undefined\}/);
assert.match(navbar, /aria-label="打开管理员菜单"/);
assert.match(navbar, /aria-haspopup="menu"/);

// 顶栏铃铛未读数：数量要同时进视觉角标与无障碍标签，且角标不能闪烁。
assert.match(navbar, /aria-label=\{notificationUnreadCount > 0 \? `通知中心，\$\{notificationUnreadCount\} 条未读` : '通知中心'\}/);
assert.match(navbar, /min-w-\[16px\] h-4 px-1 rounded-full bg-red-500 text-white/);
assert.match(navbar, /notificationUnreadCount > 99 \? '99\+' : notificationUnreadCount/);
assert.doesNotMatch(navbar, /animate-pulse/);

// 操作指南改成独立页面（新标签页打开），顶栏不再留弹框。
const guideHtml = readFileSync(new URL('../guide.html', import.meta.url), 'utf8');
const guidePage = readFileSync(new URL('../src/guide/OperationsGuidePage.tsx', import.meta.url), 'utf8');
const viteConfig = readFileSync(new URL('../vite.config.ts', import.meta.url), 'utf8');

assert.match(navbar, /href=\{`\$\{import\.meta\.env\.BASE_URL\}guide\.html`\}/);
assert.match(navbar, /target="_blank"/);
assert.match(navbar, /rel="noopener noreferrer"/);
assert.match(navbar, /aria-label="在新标签页打开电商运营系统操作指南"/);
assert.doesNotMatch(navbar, /helpModalOpen|关闭操作指南|我知道了/);

assert.match(guideHtml, /<html lang="zh-CN">/);
assert.match(guideHtml, /<script type="module" src="\/src\/guide-main\.tsx">/);
assert.match(viteConfig, /guide: path\.resolve\(__dirname, 'guide\.html'\)/);

// 指南页面自身的结构语义：h1、目录导航、正文容器、表格标题都要有。
assert.match(guidePage, /<h1 className=/);
assert.match(guidePage, /<main id="guide-main"/);
assert.match(guidePage, /aria-label="指南目录"/);
assert.match(guidePage, /<caption className="sr-only">/);

// 指南内容与菜单口径的一致性在 flow-smoke 里按数据核对，这里只守页面语义。

// 客服工作台：左侧坐席栏与右侧会话面板各自成滚动容器。会话面板靠 `flex-1 min-h-0` 占满高度，
// 少了 min-h-0 输入框就会被推到首屏之外（flex 子项默认最小高度是内容高度）。
const workbench = readFileSync(new URL('../src/components/content/AiAgentWorkbenchView.tsx', import.meta.url), 'utf8');
const schedule = readFileSync(new URL('../src/components/content/AiAgentScheduleView.tsx', import.meta.url), 'utf8');

assert.match(workbench, /lg:h-\[calc\(100vh-56px-4rem\)\]/);
assert.ok(workbench.includes('lg:flex-1 lg:min-h-0'), '坐席栏在 lg 以上要能独立滚动');
assert.ok(workbench.includes('flex-1 min-h-0 overflow-y-auto'), '消息列表要占满剩余高度并自己滚动');
assert.ok(workbench.includes('permission="ai:agent:serve"'), '回复等接单动作按权限点收口');
assert.match(workbench, /aria-label="回复内容"/);

// 客服排班：表格要有可读说明，页头明确今天有没有人值班。
assert.match(schedule, /<caption className="sr-only">客服一周排班表，点击格子可编辑班次<\/caption>/);
assert.ok(schedule.includes('今日服务时段 ${todayWindow.start}-${todayWindow.end}，共 ${todayWindow.agents} 位客服排班'));
assert.ok(schedule.includes('今日无排班，买家会看到「今天暂无客服值班」'));

// 一键排班：入口带可见文字；生成结果先出预览，确认才写库。
assert.match(
  schedule,
  /<CalendarPlus className="w-4 h-4" \/>\s*一键排班/,
  '一键排班的入口要带可见文字，与发商品入口同一个道理',
);
assert.match(
  schedule,
  /role="dialog"\s+aria-modal="true"\s+aria-label="一键排班"/,
  '排班弹框要有可读名称与模态语义',
);
assert.ok(schedule.includes('aria-label="用一句话说明怎么排班"'), '自然语言输入要有可读名称');
assert.ok(
  schedule.includes('disabled={!plan || plan.items.length === 0 || applying}'),
  '没出预览就不能点应用：直接写库会把手工调过的班次悄悄覆盖掉',
);
assert.ok(
  schedule.includes('生成预览') && schedule.includes('确认应用'),
  '生成与应用要分成两步',
);

const adminApiSchedule = readFileSync(new URL('../src/api/adminApi.ts', import.meta.url), 'utf8');
assert.ok(
  adminApiSchedule.includes("'/api/admin/ai/agent/schedule/plan/apply'"),
  '应用排班要独立成接口，且回传参数而不是回传计划条目',
);

// 工作台发商品卡片：挑商品要走服务端搜索，卡片由服务端装配。
const adminApi = readFileSync(new URL('../src/api/adminApi.ts', import.meta.url), 'utf8');
assert.ok(adminApi.includes("'snapshot' | 'message' | 'product' |"), '工作台要认推商品的 product 事件');
assert.ok(adminApi.includes('export interface BackendAiProductCard'), '商品卡片类型要与服务端一一对应');
assert.ok(
  adminApi.includes('/api/admin/ai/agent/products?keyword='),
  '挑商品要走服务端搜索，不在前端过滤当前页商品',
);
assert.ok(
  adminApi.includes("'/products'") || adminApi.includes("}/products`"),
  '发送商品要有独立接口，带上商品主键与说明',
);
assert.ok(workbench.includes('aria-label="发送商品"'), '发商品的入口要有可读名称');
assert.match(
  workbench,
  /<Package className="w-4 h-4" \/>\s*发送商品/,
  '发商品的入口要带可见文字：只有一个图标时客服认不出来，会以为没有这个功能',
);
assert.ok(workbench.includes('aria-label="搜索要发送的商品"'), '商品搜索框要有可读名称');
assert.ok(
  workbench.includes('setPickerResults(await searchAiAgentProducts(keyword));'),
  '搜索结果来自服务端，卡片上的图片地址才是换发过的',
);
assert.ok(
  workbench.includes("showToast('商品卡片已发送', 'success');"),
  '发送成功要有反馈，否则客服会重复点',
);
assert.ok(
  workbench.includes('setPickerOpen(false);') && workbench.includes('setPickerResults([]);'),
  '切换会话要收起商品弹框：上一个买家搜出的商品很容易误发给下一个',
);
assert.ok(
  workbench.includes('aria-haspopup="dialog"'),
  '商品入口要声明它会开出弹框，读屏用户才知道点下去是弹层而不是展开的行内区域',
);
assert.match(
  workbench,
  /role="dialog"\s+aria-modal="true"\s+aria-label="发送商品"/,
  '挑商品要用模态弹框，不能挤在输入框上方压掉聊天记录',
);
assert.ok(
  workbench.includes('useBodyScrollLock(pickerOpen)'),
  '弹框打开期间要锁住底层滚动，否则滚商品会把工作台一起带走',
);
assert.ok(
  workbench.includes("event.key === 'Escape'"),
  'Esc 要能关掉商品弹框',
);

console.log('shop-web 无障碍静态检查通过：跳过链接、主导航语义、菜单状态、管理员菜单标签、铃铛未读角标、操作指南独立页面、客服工作台与会话面板滚动容器、客服排班表说明、工作台发送商品卡片（入口带可见文字、服务端搜索、卡片回显、切会话收起、模态弹框）已覆盖');
