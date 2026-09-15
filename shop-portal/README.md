# Henfon 商城用户门户

用户门户前端基于 Vite、React 和 TypeScript，默认访问本地后端 `http://localhost:8080`，支持商品浏览、会员认证、购物车、地址、订单、售后和物流查询。

## 开发

需要 Node.js 18+（建议使用当前 LTS）。

```powershell
npm install
Copy-Item .env.example .env.local
npm run dev
```

开发服务器默认地址为 `http://localhost:3000`。如果管理端已占用该端口，可执行 `npm run dev -- --port 3001`。后端地址通过 `VITE_API_BASE_URL` 配置；地址搜索和地图选点需要填写 `VITE_AMAP_KEY` 与 `VITE_AMAP_SECURITY_CODE`。

`VITE_DEMO_MODE=true` 可在没有后端时启用演示数据，仅限本地开发，生产环境必须保持 `false`。

## 构建与检查

```powershell
npm run lint       # TypeScript 类型检查
npm test           # 组件、核心流程及无障碍冒烟测试
npm run build      # 生产构建
npm run preview    # 预览 dist
```

门户认证、订单和支付接口说明见仓库根目录 [README](../README.md) 及 [API 契约](../docs/API契约.md)。
