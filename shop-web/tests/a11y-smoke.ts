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

console.log('shop-web 无障碍静态检查通过：跳过链接、主导航语义、菜单状态和管理员菜单标签已覆盖');
