import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const api = readFileSync(new URL('../src/api/adminApi.ts', import.meta.url), 'utf8');
const login = readFileSync(new URL('../src/components/auth/AdminLogin.tsx', import.meta.url), 'utf8');
const products = readFileSync(new URL('../src/components/products/ProductManagementView.tsx', import.meta.url), 'utf8');
const orders = readFileSync(new URL('../src/components/orders/OrderManagementView.tsx', import.meta.url), 'utf8');

// 管理端当前无浏览器运行时依赖，先用源码契约冒烟保障核心流程入口未被误删。
assert.match(api, /login|auth/i);
assert.match(api, /auth\/refresh/);
assert.match(api, /refreshAdminToken/);
assert.match(api, /catalog\/products/);
assert.match(api, /trade\/orders/);
assert.match(login, /登录|username|password/);
assert.match(products, /Product|商品/);
assert.match(orders, /发货|shipping|shipment/i);

console.log('shop-web 管理流程冒烟检查通过：登录 → 商品 → 订单/发货入口均存在');
