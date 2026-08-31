import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const app = readFileSync(new URL('../src/App.tsx', import.meta.url), 'utf8');
const navbar = readFileSync(new URL('../src/components/Navbar.tsx', import.meta.url), 'utf8');
const productCard = readFileSync(new URL('../src/components/ProductCard.tsx', import.meta.url), 'utf8');
const index = readFileSync(new URL('../index.html', import.meta.url), 'utf8');

assert.match(index, /<html lang="zh-CN">/);
assert.match(app, /href="#portal-main-content"/);
assert.match(app, /id="portal-main-content"[^>]*tabIndex=\{-1\}/);
assert.match(app, /aria-pressed=\{isCompareMode\}/);
assert.match(navbar, /aria-label="搜索商品、品牌或型号"/);
assert.match(navbar, /aria-expanded=\{isSearchFocused\}/);
assert.match(productCard, /onKeyDown=\{\(event\) =>/);
assert.match(productCard, /role="button"/);

console.log('shop-portal 无障碍静态检查通过：跳过链接、中文语言、搜索 ARIA 和键盘入口已覆盖');
