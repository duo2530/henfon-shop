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
console.log('shop-web 无障碍静态检查通过：跳过链接、主导航语义、菜单状态、管理员菜单标签、铃铛未读角标与操作指南独立页面已覆盖');
