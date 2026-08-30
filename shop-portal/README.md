<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/09e85562-ad18-4769-b005-af70c84f48f1

## Run Locally

**Prerequisites:**  Node.js


1. Install dependencies:
   `npm install`
2. Set the `GEMINI_API_KEY` in [.env.local](.env.local) to your Gemini API key
3. Run the app:
   `npm run dev`

## Henfon 商城联调说明

- 门户默认请求 `http://localhost:8080`，可通过 `VITE_API_BASE_URL` 覆盖。
- 会员登录后会携带 JWT 请求购物车、地址、订单列表和订单详情接口。
- 订单弹窗会加载服务端订单明细及物流轨迹，并支持取消订单、确认收货；后端不可用时保留本地演示数据。
- 提交前可执行 `npm run lint` 校验门户 TypeScript 类型。
