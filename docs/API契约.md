# Henfon Shop API 契约

> 版本：v1（2026-08-31）。本文档依据当前 `shop-admin` 控制器、`shop-web` 与 `shop-portal` API client 整理，作为联调基线；新增接口须同步更新本文档及 OpenAPI/Knife4j 定义。

## 1. 通用约定

- 根路径：`/api`；管理端路径以 `/admin` 开头，门户路径以 `/portal` 开头。
- 编码：UTF-8，`Content-Type: application/json`；文件上传使用 `multipart/form-data`。
- 认证：管理端接口携带 `Authorization: Bearer <管理员JWT>`；会员接口携带会员 JWT。会员 `memberId` 必须与令牌主体一致，服务端不得信任 URL 中的其他会员 ID。管理员连续登录失败达到阈值后账号短期锁定。
- 幂等：创建订单、支付、领取优惠券等写操作建议携带 `Idempotency-Key`；重复请求返回同一业务结果。
- 链路：请求可携带 `X-Request-Id`，响应 `requestId` 原样返回（未提供时由网关/过滤器生成）。

## 2. 统一响应结构

所有 JSON 接口返回：

```json
{"code":"0","message":"success","data":{},"requestId":"req-20260831-001"}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | string | `0` 表示成功；失败为稳定的大写下划线错误码 |
| `message` | string | 面向调用方的提示，不包含堆栈或敏感信息 |
| `data` | object/array/null | 业务数据；无数据成功时为 `null` |
| `requestId` | string | 请求链路标识，用于日志检索 |

HTTP 状态：成功 2xx；参数/业务错误 400；认证失败 401；无权限 403；资源不存在 404；未预期异常 500（`SYSTEM_ERROR`）。

## 3. 分页契约

分页查询统一使用查询参数 `current`（页码，从 1 开始，默认 1）和 `size`（每页条数，默认 20，最大 200；库存盘点导入等批量接口另行限制）。返回 MyBatis-Plus `IPage` 兼容结构：

```json
{"current":1,"size":20,"total":42,"pages":3,"records":[]}
```

`records` 为当前页数据；前端不得以 `records.length` 代替 `total`。筛选参数缺省表示不筛选，排序字段必须使用接口白名单。

## 4. 时间、金额及基础类型

- 时间统一 ISO-8601：`yyyy-MM-dd'T'HH:mm:ssXXX`（示例 `2026-08-31T10:20:30+08:00`）；仅日期使用 `yyyy-MM-dd`，时区为 `Asia/Shanghai`。
- 金额使用 JSON 字符串，人民币元，固定两位小数（如 `"199.90"`），后端 `BigDecimal`，禁止浮点数；数量、库存、积分使用整数。
- ID 使用 JSON 数字（Long）；前端如存在精度风险应按字符串保存。状态字段使用后端定义的整数/枚举值，不自行转换。

## 5. 错误码

通用错误码：`VALIDATION_ERROR`（参数校验）、`AUTH_INVALID`/`AUTH_REFRESH_INVALID`/`MEMBER_AUTH_INVALID`（凭证无效）、`AUTH_DISABLED`/`MEMBER_AUTH_DISABLED`（账号禁用）、`AUTH_RATE_LIMITED`（登录限流）、`AUTH_LOCKED`（管理员连续失败锁定）、`AUTH_PASSWORD_INVALID`/`AUTH_PASSWORD_UNCHANGED`（密码修改失败）、`MEMBER_PASSWORD_RESET_INVALID`（邮箱重置令牌无效或过期）、`MEMBER_AUTH_REQUIRED`、`MEMBER_ID_MISMATCH`、`MARKETING_COUPON_CATEGORY_MISMATCH`（优惠券适用类目不匹配）、`*_NOT_FOUND`（资源不存在）、`*_CONCURRENT[_UPDATE]`（乐观锁冲突）、`*_STATUS_INVALID`（非法状态流转）、`SYSTEM_ERROR`。各领域错误码沿用代码中 `BusinessException` 的稳定字符串前缀（如 `TRADE_`、`INVENTORY_`、`PAYMENT_`、`MARKETING_`、`CATALOG_`、`CONTENT_`）；客户端应按 `code` 分支，不能匹配中文 `message`。

## 6. 现有模块主要接口

### shop-boot / integration

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/health` | 健康检查 |
| POST | `/api/admin/storage/upload` | MinIO 上传（multipart，支持 JPG/PNG/WEBP/GIF/MP4/PDF，单文件不超过 10MB） |
| DELETE | `/api/admin/storage?objectKey=` | 删除对象 |
| POST | `/api/portal/storage/upload` | 会员评价图片上传（需会员 JWT，multipart） |

### shop-identity（认证、会员、RBAC、审计）

- 管理认证：`POST /api/admin/auth/login`、`POST /api/admin/auth/refresh`、`POST /api/admin/auth/password`、`POST /api/admin/auth/logout`、`GET /api/admin/auth/me`、`GET /api/admin/auth/menus`。登录返回 8 小时访问令牌和 30 天刷新令牌；刷新请求体为 `{ "refreshToken": "..." }`，刷新令牌使用 Redis 原子消费并一次性轮换，旧令牌立即失效。修改密码请求体为 `{ "oldPassword": "...", "newPassword": "..." }`，新密码长度 6～64 位；退出接口允许在访问令牌过期时调用，会将当前 JWT（如仍有效）加入黑名单，并删除请求体中的刷新令牌。
- 会员认证：`POST /api/portal/auth/login|register|refresh|logout`；邮箱找回密码申请 `POST /api/portal/auth/password-reset/request`（请求 `{ "email": "buyer@example.com" }`，无论邮箱是否存在均返回统一成功响应），确认 `POST /api/portal/auth/password-reset/confirm`（请求 `{ "token": "...", "newPassword": "..." }`，令牌 Redis 短时有效且原子消费，仅可使用一次）。
- 会员门户：`GET /api/portal/member/profile|addresses|favorites|compare/history`；地址 `POST /addresses`、`PUT/DELETE /addresses/{id}`、`PUT /addresses/{id}/default`；收藏 `POST /favorites/{productId}/toggle`；对比历史 `POST /compare/history`。
- 会员管理：`GET /api/admin/member/users`（分页）、`POST /users`（后台会员建档）、`PUT /users/{id}`、`PUT /users/{id}/status`、`PUT /users/{id}/assets`、标签 `GET/PUT /tags`、`PUT /tags/{id}`、`PUT /users/{id}/tags`。
- 系统管理：`/api/admin/system/users|depts|roles|menus|data-rules` 的分页/创建/更新/删除，以及用户角色、角色菜单、角色数据规则的 `GET/PUT` 关联接口。
- 审计：`GET /api/admin/audit/login-logs`、`GET /api/admin/audit/operation-logs`（均分页）。

### shop-catalog（商品、SKU、类目）

- 门户：`GET /api/portal/catalog/products`（商品关键词、SKU 编码/名称/属性关键字、类目、价格区间、排序、分页）、`GET /products/{id}`、`GET /categories`。
- 管理：`GET/POST /api/admin/catalog/products`、`DELETE /products/{id}`；SKU `GET/POST /products/{productId}/skus`、`DELETE /products/{productId}/skus/{skuId}`；内容 `GET/PUT /products/{productId}/content`；类目 `GET /categories|categories/manage`、`POST /categories`、`DELETE /categories/{id}`。

### shop-inventory（仓库、库存、盘点、供应商）

- 仓库/库存：`GET /api/admin/inventory/warehouses|warehouses/enabled`、`PUT /warehouses/{id}/status`；`GET /stocks`、`GET /stocks/warnings`、`GET /locks`（按订单/状态查询库存锁定流水，状态 0=已预占、1=已释放、2=已扣减）、`PUT /stocks/{stockId}/adjust`。
- 供应商：`GET/POST /api/admin/inventory/suppliers`、`PUT /suppliers/{id}/status`、`DELETE /suppliers/{id}`。
- 盘点：`GET /api/admin/inventory/stocktakes`、`GET /stocktakes/{id}/items`、`POST /stocktakes`、`POST /stocktakes/{id}/complete`；导入明细一次最多 5000 条。

### shop-trade（购物车、订单、物流、售后）

- 购物车：`GET /api/portal/trade/cart`、`POST /items`、`PUT /items/{id}`、`DELETE /items/{id}`。
- 门户订单：`GET /api/portal/trade/orders`（分页）、`GET /orders/{id}`、`GET /orders/{id}/logistics`、`POST /orders`、`PUT /orders/{id}/cancel|confirm`。
- 门户运费：`POST /api/portal/trade/freight/quote` 按购物车商品、收货地区和商品金额返回首重/续重、包邮门槛及偏远地区附加费；创建订单时服务端会再次复算并拒绝篡改或过期运费。
- 管理订单：`GET /api/admin/trade/orders`（分页）、`GET /orders/{id}/items|logistics`、`POST /orders/{id}/logistics`、`POST /orders/{id}/logistics/sync`、`POST /orders/batch-ship`、`PUT /orders/{id}/logistics/{logisticsId}`、`PUT /orders/{id}/ship|cancel|remark|refund|audit`、`PUT /orders/{id}/audit/approve|reject`。订单审核仅允许已支付待发货订单，使用 `version` 乐观锁并记录审核人、时间和备注；未审核通过的订单不能发货。`logistics/sync` 调用已配置的物流服务商（当前为快递100）并将轨迹幂等写入现有物流表；未配置服务商时返回 `TRADE_LOGISTICS_SYNC_FAILED`。已发货订单会在服务商启用后由后台定时任务按批次自动同步。`batch-ship` 请求体为 `{shipments:[{orderId,logisticsCompany,trackingNo}]}`，单次最多100笔，任一订单失败则整批事务回滚。备注接口请求体为 `{sellerRemark, flagColor, version}`，标旗支持 `red/yellow/green/blue/purple` 或 `null` 清空，并通过 `version` 乐观锁校验。
- 管理运费：`GET/PUT /api/admin/trade/freight/template` 查询和保存默认运费模板，支持承运商、首重/续重、包邮门槛、偏远地区附加费及地区关键词。
- 售后：门户 `GET/POST /api/portal/trade/after-sales`、`DELETE /after-sales/{id}`；申请可携带最多 9 个 `evidenceUrls`（先通过需登录的 `POST /api/portal/storage/upload` 上传 JPG/PNG/WEBP/GIF 图片，单文件不超过 10MB，再提交返回的 `url`），管理 `GET /api/admin/trade/after-sales`（分页）、`PUT /{id}/approve|reject|return-received`；退货退款审核通过后由 `return-received` 确认入库并触发原路退款。
- Outbox 运维：`GET /api/admin/trade/outbox/dead-events`（按事件类型、主题、聚合 ID 分页筛选）、`POST /api/admin/trade/outbox/{eventId}/retry`（仅允许死信事件人工重试，需 `trade:outbox:query/retry` 权限）。

### shop-marketing（优惠券、秒杀）

- 门户：`GET /api/portal/marketing/coupons|member-coupons`、`GET /api/portal/marketing/flash-sales`、`POST /coupons/{couponId}/claim`、`POST /coupons/redeem|rollback`。秒杀查询只返回当前时间窗口内启用且仍有可售库存的活动，并携带活动价、限购和剩余库存；领取时校验单会员领取上限和发行总量；核销时服务端校验优惠券适用类目；订单取消可主动回滚，订单全额退款成功事件会自动幂等回滚已核销优惠券。
- 管理优惠券：`GET/POST /api/admin/marketing/coupons`（分页/保存）、`PUT /{id}/status`、`DELETE /{id}`。
- 管理秒杀：`GET /api/admin/marketing/flash-sales`、`GET /{id}/items`、`POST /`、`PUT /{id}/status`、`DELETE /{id}`。

### shop-content（Banner、评价、通知）

- 门户：`GET /api/portal/content/banners`、`GET /products/{productId}/reviews`、`GET /products/{productId}/reviews/page`（分页）、`POST /products/{productId}/reviews`（仅已支付且未取消订单购买过该商品的会员可提交，支持最多 9 个 `imageUrls`）；通知 `GET /api/portal/content/notifications`（分页）、`PUT /{notificationId}/read`、`PUT /read-all`。
- 管理 Banner：`GET/POST /api/admin/content/banners`、`PUT /{id}/status`、`DELETE /{id}`；评价 `GET /api/admin/content/reviews`（分页）、`PUT /{id}/status`、`PUT /{id}/reply`。

### shop-payment（支付、退款、发票）

- 门户支付：`POST /api/portal/payment/orders/{orderId}`、`GET /orders/{paymentNo}`、`PUT /orders/{paymentNo}/close`。
- 微信回调：`POST /api/wx/pay/notify`、`POST /api/wx/pay/refund/notify`（联调 DTO）；生产 V3 原始回调使用 `POST /api/wx/pay/notify/v3`、`POST /api/wx/pay/refund/notify/v3`，服务端先校验 `Wechatpay-Timestamp/Nonce/Signature` 再解密并执行幂等状态更新。
- 管理退款：`POST /api/admin/payment/refunds`、`GET /refunds/{refundNo}`；发票 `GET /api/admin/payment/invoices`（分页）、`PUT /invoices/{invoiceNo}/status`；资金对账 `GET /api/admin/payment/reconciliation`（支持关键字、流水类型、状态和分页筛选）。
- 门户发票：`POST/GET /api/portal/payment/invoices/orders/{orderId}`。

### shop-reporting（经营报表）

- `GET /api/admin/reporting/overview`（兼容 `/dashboard/metrics`）：指定日期经营指标。
- `GET /api/admin/reporting/sales-trend`（兼容 `/dashboard/sales-trend`）：按日期范围返回销售额、订单数、销量；日期范围需符合服务端上限。
- `GET /api/admin/reporting/product-ranking`（兼容 `/products/ranking`）：按已支付订单查询商品销量排行，支持日期范围和 Top N。
- `GET /api/admin/reporting/member-analysis`（兼容 `/members/analysis`）：返回会员总数、新增、活跃、复购率、客单价及等级分布。
- `GET /api/admin/reporting/export`：导出 `PRODUCT_RANKING`、`MEMBER_ANALYSIS` 或 `SALES_TREND` CSV 报表，响应包含 UTF-8 BOM。

## 7. 兼容与变更

新增字段保持向后兼容；删除或改名字段须先废弃并至少保留一个版本周期。错误码、状态值、金额精度和分页字段属于契约 breaking change，必须更新前端 client、示例和变更记录。
