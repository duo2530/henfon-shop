# Henfon 商城管理端

管理端前端基于 Vite、React 和 TypeScript，默认访问本地后端 `http://127.0.0.1:8080`。

## 开发

需要 Node.js 18+（建议使用当前 LTS）。

```powershell
npm install
Copy-Item .env.example .env.local
npm run dev
```

开发服务器默认地址为 `http://localhost:3000`。后端地址可通过 `.env.local` 中的 `VITE_API_BASE_URL` 覆盖。

## 构建与检查

```powershell
npm run lint       # TypeScript 类型检查
npm test           # 无障碍及核心流程冒烟测试
npm run build      # 生产构建
npm run preview    # 预览 dist
```

管理端接口、账号和依赖服务说明见仓库根目录 [README](../README.md) 及 [API 契约](../docs/API契约.md)。
