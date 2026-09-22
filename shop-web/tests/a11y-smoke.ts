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
const stats = readFileSync(new URL('../src/components/content/AiAgentStatsView.tsx', import.meta.url), 'utf8');
const knowledge = readFileSync(new URL('../src/components/content/AiKnowledgeView.tsx', import.meta.url), 'utf8');
const addresses = readFileSync(new URL('../src/components/content/MemberAddressView.tsx', import.meta.url), 'utf8');
const menuAdapter = readFileSync(new URL('../src/navigation/menuAdapter.ts', import.meta.url), 'utf8');
const appShell = readFileSync(new URL('../src/App.tsx', import.meta.url), 'utf8');
const roleList = readFileSync(new URL('../src/components/rbac/RoleManagementView.tsx', import.meta.url), 'utf8');
const authorization = readFileSync(new URL('../src/components/rbac/AuthorizationView.tsx', import.meta.url), 'utf8');

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

// 等待与接待列表要有买家头像：认人靠图形比靠名字快，没有头像时也要有占位而不是空白。
assert.ok(workbench.includes('const MemberAvatar'), '头像要抽成一个组件，两个列表共用同一套降级');
assert.ok(
  workbench.includes('src={avatarUrl}') && workbench.includes('alt=""'),
  '买家头像对读屏没有信息量——名字已经在旁边了，标成空避免重复朗读',
);
assert.ok(
  workbench.includes('trimmed.slice(0, 1)') && workbench.includes('rounded-full'),
  '没有头像时用显示名首字占位，一整排空圆点会让人以为图没加载出来',
);

// 排班格子要用底色区分三种状态，只换边框扫一眼看不出来。
assert.ok(
  /border-blue-200 bg-blue-50/.test(schedule) && /border-amber-200 bg-amber-50/.test(schedule),
  '已排班与已停用要有各自的颜色',
);
assert.ok(
  /border-dashed border-slate-200 bg-slate-50/.test(schedule),
  '未排班保持虚线空底，与"有班次"一眼可分',
);

// 统计详情：列表行的客服名要能点进详情，详情页要能返回，返回后统计窗口还在（days 留在同一个组件）。
assert.ok(stats.includes('setDetailAgentId(row.agentId)'), '列表行要能进详情');
assert.ok(stats.includes('返回列表'), '详情要有返回入口，不能只能靠浏览器后退');
assert.ok(stats.includes('getAiAgentStatsDetail'), '详情走独立接口取数');
assert.ok(stats.includes('aria-label="按天接待量趋势"'), '趋势图要有可读名称');
assert.ok(stats.includes('scheduledHours'), '详情要带上排班工时，接待量与排班要能对着看');

// 知识库导出：按钮要有可见文字，文件名带日期，中文靠 BOM 才不会在 Excel 里乱码。
assert.ok(knowledge.includes('导出'), '导出按钮要有可见文字');
assert.ok(knowledge.includes('exportAiFaqs('), '导出走服务端，不在前端拼当前页数据');
assert.ok(knowledge.includes('.csv`'), '落地文件名要带 .csv');

// 知识库：答案截断后要能在悬停时看到全文，启停做成开关而不是文字按钮。
assert.ok(
  /<span className="line-clamp-2" title=\{item\.answer\}>/.test(knowledge),
  '标准答案截断后要把全文挂在 title 上，否则只能点开编辑框才看得到',
);
assert.ok(knowledge.includes('role="switch"') && knowledge.includes('aria-checked={item.enabled === 1}'),
  '启用列要是开关：状态能被读屏读出，也不必进编辑框改一个字段');
assert.ok(knowledge.includes('setAiFaqEnabled('), '开关走独立接口，不要把整条记录回写给保存接口');

// 收货地址：写操作按权限点收口，默认地址要标得出来。
assert.ok(
  addresses.includes('permission="member:address:save"'),
  '改地址单独一个权限点，看得到地址的角色不一定能改',
);
assert.ok(addresses.includes('setDefaultMemberAddress('), '要有设为默认的入口');
assert.ok(addresses.includes('aria-labelledby="member-address-editor-title"'), '编辑弹框要有可访问名称');

// 角色权限配置页：它没有自己的菜单，靠权限点放行；从哪一行进来就定位到哪一行。
assert.ok(menuAdapter.includes("authorization: 'system:role-menu:save'"),
  '权限配置页不建菜单，改按角色菜单权限点放行，否则菜单树里永远查不到它');
assert.ok(menuAdapter.includes('export function hasTabAccess'), '从属页面要有独立的放行判断，不能混在菜单树判定里');
assert.ok(appShell.includes('hasTabAccess(authorizedMenuItems, currentTab, permissions)'),
  '渲染分支也要按放行判断，只放开跳转会渲染不出来');
assert.ok(roleList.includes('openRoleAuthorization(role.id)'),
  '配置功能权限要把角色带过去，否则默认落在列表第一个角色上改错人');
assert.ok(authorization.includes('initialRoleId'), '权限配置页要能接收指定角色');

console.log('shop-web 无障碍静态检查通过：跳过链接、主导航语义、菜单状态、管理员菜单标签、铃铛未读角标、操作指南独立页面、客服工作台与会话面板滚动容器、客服排班表说明、工作台发送商品卡片（入口带可见文字、服务端搜索、卡片回显、切会话收起、模态弹框）、等待与接待列表头像、排班三态配色、统计详情进出返回、知识库导出、知识库答案悬停与启停开关、收货地址管理、角色权限配置页放行与定位已覆盖');
