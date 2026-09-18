# shop-admin

Henfon 商城后端模块化单体工程。

运行环境要求：Java 21、Maven 3.8+。

## 模块

| 模块 | 职责 |
| --- | --- |
| `shop-boot` | Spring Boot 启动、Web 配置、基础接口 |
| `shop-common` | 统一响应、业务异常、请求链路标识 |
| `shop-identity` | 管理员、会员、角色、菜单、数据权限 |
| `shop-catalog` | 商品、类目、SKU、商品媒体 |
| `shop-trade` | 购物车、订单、售后 |
| `shop-inventory` | 仓库、供应商、库存、库存流水 |
| `shop-marketing` | 优惠券、秒杀、促销活动 |
| `shop-payment` | 支付、退款、发票、财务流水 |
| `shop-content` | Banner、评价、运营内容 |
| `shop-reporting` | 首页指标、经营分析、商品分析 |
| `shop-integration` | MinIO、RocketMQ 及第三方系统适配 |

## 本地配置

公共配置位于 `shop-boot/src/main/resources/application.yml`，本机开发连接信息位于 `shop-boot/src/main/resources/application-dev.yml`：

- MySQL 8：`127.0.0.1:3306/henfon-shop`，用户 `root`，密码 `123456`
- Redis：`127.0.0.1:6379`，用户 `default`，密码 `123456`
- MinIO：`http://127.0.0.1:9000`，默认账号 `minioadmin`
- RocketMQ NameServer：`127.0.0.1:9876`

所有连接信息都支持通过环境变量覆盖，生产环境不要使用默认密码。

开发环境默认关闭 RocketMQ 消费监听器，因此未启动 NameServer 时后端仍可正常启动；启动 RocketMQ 后设置 `SHOP_ROCKETMQ_CONSUMER_ENABLED=true` 即可开启 28 个领域事件监听器。Outbox 发布失败会自动重试，站内通知轮询兜底不受影响。

### 配置环境

项目提供三套 Spring Boot 环境配置：

- `dev`：开发环境，默认激活，连接本机 `henfon-shop`，开启 DEBUG 日志和 SQL 输出。
- `test`：测试环境，默认连接独立的 `henfon-shop-test` 数据库，Redis 使用 DB 1。
- `prod`：生产环境，不提供数据库、Redis、MinIO、RocketMQ 和 JWT 密钥默认值，必须由部署平台注入环境变量。

通过环境变量或启动参数切换环境：

```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
java -jar shop-boot/target/shop-boot-0.0.1-SNAPSHOT.jar

# 或者
java -jar shop-boot/target/shop-boot-0.0.1-SNAPSHOT.jar --spring.profiles.active=test
```

生产环境至少需要配置：`SHOP_MYSQL_URL`、`SHOP_MYSQL_USERNAME`、`SHOP_MYSQL_PASSWORD`、`SHOP_REDIS_HOST`、`SHOP_REDIS_PASSWORD`、`ROCKETMQ_NAME_SERVER`、`SHOP_JWT_SECRET`、`MINIO_ENDPOINT`、`MINIO_ACCESS_KEY` 和 `MINIO_SECRET_KEY`。

每项配置的作用、值从哪里取（邮箱授权码、微信支付证书与密钥、快递 100 账号、高德 Key 等），以及不配会出什么现象，见仓库根目录 [README 的配置说明](../README.md#配置说明)。

## 构建和启动

```powershell
mvn -DskipTests compile
mvn -DskipTests package
java -jar shop-boot/target/shop-boot-0.0.1-SNAPSHOT.jar
```

健康检查：`GET http://127.0.0.1:8080/actuator/health`，免鉴权。其它接口都要 JWT，未登录会拿到 401 与 `AUTH_REQUIRED`，`/api/health` 同样在鉴权范围内。

## 演示数据与媒体

用 `db/schema/henfon-shop.sql` 建出来的是空库，跑起来门户没有商品图、管理端没有订单。`db/seed/` 放了一份可直接导入的快照：

- `demo-data.sql`：27 张业务表的 `INSERT IGNORE` 快照，覆盖商品、SKU、商品媒体、库存与流水、营销活动、会员与地址收藏、购物车、订单与物流、售后、发票、站内通知、登录日志。
- `media/`：399 张图片，按 `2026-09-01/xxx.jpg` 的日期目录存放，其中 2 张没有被任何记录引用，留着备用。
- `import-demo.mjs`：上传脚本，把 `media/` 下的文件传到 MinIO 的 `shop` 桶，并补回对象键里的 `media/` 前缀。

### 导入

MinIO 先起来，媒体才传得进去。在 `shop-admin` 目录执行：

```powershell
mysql -uroot -p --default-character-set=utf8mb4 henfon-shop < db/seed/demo-data.sql
node db/seed/import-demo.mjs
```

`import-demo.mjs` 只用 Node 原生能力，需要 Node 18+（用到全局 `fetch`），不需要 `npm install`。

凭据必须和后端一致。脚本默认连 `http://127.0.0.1:9000`、用 `minioadmin / minioadmin`，与 `application-dev.yml` 的默认值相同；如果 MinIO 是 `docker compose` 起的，账号密码来自 `.env` 的 `MINIO_ACCESS_KEY` 和 `MINIO_SECRET_KEY`，要先设成同样的值，否则会 403：

```powershell
$env:MINIO_ACCESS_KEY = "henfon-minio"
$env:MINIO_SECRET_KEY = "change-me-minio-secret"
node db/seed/import-demo.mjs
```

可覆盖的环境变量：`MINIO_ENDPOINT`、`MINIO_BUCKET`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`。桶不存在时脚本会自己创建。

重复执行是安全的：图片已存在就跳过，加 `--force` 覆盖重传；SQL 全部是 `INSERT IGNORE`，不会覆盖已有数据。想一条命令做完，加 `--sql`：

```powershell
node db/seed/import-demo.mjs --sql
```

`--sql` 会调用 mysql 客户端，需要它在 `PATH` 里。Windows 上 MySQL 客户端一般不在 `PATH`，用 `MYSQL_BIN` 指定绝对路径、`MYSQL_PWD` 传密码、`MYSQL_DB` 指定库名（默认 `henfon-shop`）：

```powershell
$env:MYSQL_PWD = "123456"
$env:MYSQL_BIN = "C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe"
node db/seed/import-demo.mjs --sql
```

### 验证

MinIO 控制台 `http://127.0.0.1:9001` 的 `shop` 桶下应能看到 `media/` 目录，`media/` 前缀下的对象数与本地 `db/seed/media/` 的文件数一致。库里核对几行关键数据：

```sql
SELECT COUNT(*) FROM catalog_product;         -- 394
SELECT COUNT(*) FROM catalog_product_media;   -- 391
SELECT COUNT(*) FROM trade_order;             -- 15
```

启动后端、打开门户首页，商品图、轮播图、评价图都应正常显示。

### 图片地址的存放约定

所有图片字段都按这条约定处理，新增带图的功能时照做：

- 库里存**对象键**（`media/2026-09-01/xxx.jpg`），不存可访问地址。
- 上传接口 `POST /api/admin/storage/upload` 返回的 `url` 是 24 小时过期的 MinIO 预签名地址，直接入库过一天就会 403；要入库的是同一次返回的 `objectKey`。
- 读取时由后端重新签发地址。统一入口是 `shop-integration` 的 `ImageReferenceResolver`：`accessUrl` 处理单值，`resignJsonArray` 处理以 JSON 数组存的多个地址；写入侧对应 `normalizeReference` 与 `normalizeJsonArray`。新增模块直接注入它，不要再复制一份实现。
- 现有先例：内容模块的海报与评价图、交易模块的售后凭证、支付模块的发票附件、身份模块的管理员与会员头像，读写两侧都已按此处理。
- 后台列表要展示缩略图又不能把签名地址写回库时，用 `@TableField(exist = false)` 的瞬态字段（先例是 `ContentBanner.imageAccessUrl`）。

`demo-data.sql` 里的地址已全部归一化成对象键，没有 http 残留，换任何 endpoint 都能直接用。

### 有意不导出的表

`sys_oper_log`（约 1.4 万行操作日志）、`trade_event_outbox`、`export_task`（产物已过期）、`content_email_delivery` 不导出。部门、角色、菜单和 `admin` 账号由 `IdentityDataInitializer` 在应用启动时自建，也不在快照里。

### 重新生成快照

本地数据变了、想刷新这份快照时，用 `demo-seed-package` 技能：它包含导出 MinIO 媒体和转出归一化 SQL 的两个脚本，以及需要导哪些表的范围参考，产物与本目录下的文件保持同一格式。手工重做的话，注意导出后必须把预签名地址替换成对象键，并核对 SQL 引用的对象键与 `media/` 下的文件双向无缺口。

## 测试与质量基线

执行后端单元测试、JWT 安全回归测试和订单状态机性能基线：

```powershell
mvn -pl shop-identity,shop-trade,shop-boot -am test
```

`shop-boot` 中的外部依赖冒烟测试默认跳过，不会要求开发机启动 MySQL、Redis、RocketMQ 或 MinIO。测试环境准备好依赖后，设置 `SHOP_IT_ENABLED=true` 再执行上述命令即可；主机、端口通过 `SHOP_TEST_*` 环境变量覆盖，支付回调地址通过 `SHOP_TEST_PAYMENT_CALLBACK_URL` 配置。性能基线默认要求 10 万次状态转换在 5 秒内完成，可使用 `-Dshop.performance.maxMillis=10000` 调整阈值。集成测试仅验证依赖连通性，真实数据库读写、消息收发和支付签名联调需在隔离测试环境执行。

身份认证接口：

- `POST /api/admin/auth/login`：管理员登录，JSON 请求体 `{ "username": "admin", "password": "123456" }`，返回 `accessToken`、30 天 `refreshToken` 和令牌有效期。
- `POST /api/admin/auth/refresh`：使用登录返回的 `refreshToken` 换取新的访问令牌，刷新令牌轮换后旧令牌立即失效。
- `GET /api/admin/auth/me`：携带 `Authorization: Bearer <token>` 获取当前用户和权限

门户会员认证接口：

- `POST /api/portal/auth/register`：账号密码注册，JSON 请求体 `{ "username": "alice@example.com", "password": "123456", "nickname": "Alice", "email": "alice@example.com", "phone": "13800138000" }`。`email` 为必填（登录凭证与密码找回通道），`phone` 选填
- `POST /api/portal/auth/login`：用户名、手机号或邮箱登录，JSON 请求体 `{ "account": "alice@example.com", "password": "123456" }`
- `POST /api/portal/auth/password-reset/request`：按 `{ "email": "alice@example.com" }` 发送一次性重置令牌；`POST /api/portal/auth/password-reset/confirm` 用 `{ "token": "...", "newPassword": "..." }` 完成重置
- `POST /api/portal/auth/refresh`：使用登录返回的 `refreshToken` 换取新的访问令牌，刷新令牌轮换后旧令牌立即失效。
- `POST /api/portal/auth/logout`：退出并失效当前访问令牌，可在请求体中传入 `{ "refreshToken": "..." }` 删除刷新令牌。
- 登录返回的 `accessToken` 应在后续门户请求中通过 `Authorization: Bearer <token>` 携带；会员、购物车、订单和会员优惠券接口会校验 JWT 主体，不再信任客户端伪造的 `memberId`。Refresh Token 和访问令牌黑名单依赖 Redis，请确保 Redis 已启动并正确配置。

首次启动会自动初始化平台根部门、超级管理员角色、系统菜单权限和开发环境管理员账号：`admin / 123456`。生产环境必须立即修改密码并覆盖 `SHOP_JWT_SECRET`。

身份权限接口前缀：`/api/admin/system`，当前已提供系统用户分页/新增/修改/删除、部门、角色、菜单、数据规则查询与保存，以及用户角色、角色菜单、角色数据规则关系替换接口。接口已通过 `@PreAuthorize` 按菜单权限编码进行校验。

管理端前端通过 `/api/admin/auth/menus` 获取当前用户可见菜单，通过 `/api/admin/system/menus` 获取菜单配置树；角色、系统用户和数据权限页面的保存操作会回写对应身份权限接口，不依赖前端写死的菜单数据。

会员管理接口前缀：`/api/admin/member`，`GET /users` 支持关键字、等级、状态分页查询，`PUT /users/{id}/status?status=0|1` 用于冻结或解冻会员，操作需要 `member:user:query` 或 `member:user:status` 权限。

商品目录接口前缀：`/api/admin/catalog`，支持商品分页查询、保存、逻辑删除和启用类目查询；交易订单基础接口前缀：`/api/admin/trade`，支持订单分页和订单明细查询；库存接口前缀：`/api/admin/inventory`，支持库存台账分页、初始化和增减调整，订单创建/取消/发货分别自动执行库存预占/释放/扣减。门户交易接口支持订单详情、物流轨迹、会员取消订单和确认收货，售后接口位于 `/api/portal/trade/after-sales`，支持申请、查询和取消；后台售后审核接口位于 `/api/admin/trade/after-sales`。门户运费试算接口为 `POST /api/portal/trade/freight/quote`，后台运费模板接口为 `GET/PUT /api/admin/trade/freight/template`，支持首重、续重、包邮门槛、偏远地区附加费和商品重量；订单创建时后端会再次复算运费并保存订单快照。支付基础接口位于 `/api/portal/payment`：`POST /orders/{orderId}` 创建支付单、`GET /orders/{paymentNo}` 查询状态、`PUT /orders/{paymentNo}/close` 关闭支付单；支付平台回调入口为 `POST /api/wx/pay/notify`，当前已实现回调幂等、订单状态联动和 Outbox 事件记录，真实微信 V3 验签待接入。退款单接口位于 `/api/admin/payment/refunds`，支持按订单校验可退金额、幂等创建和查询；退款回调入口为 `POST /api/wx/pay/refund/notify`。创建订单可传 `idempotencyKey` 防止重复提交，服务端会校验明细小计、运费与应付金额一致性。订单状态变更统一经过状态机校验，超时任务会自动关闭待付款订单并释放库存，订单状态事件通过 Outbox 定时投递 RocketMQ。商品目录、交易和库存表结构分别位于 `db/init/003_catalog_tables.sql`、`db/init/004_trade_tables.sql`、`db/init/006_inventory_tables.sql`、`db/init/007_trade_idempotency.sql`；会员地址默认唯一约束位于 `db/init/008_member_address_default.sql`，订单事件 Outbox 位于 `db/init/009_trade_event_outbox.sql`，售后并发约束和查询索引位于 `db/init/010_trade_after_sale_indexes.sql`，支付单表位于 `db/init/011_payment_order_tables.sql`，退款单表位于 `db/init/012_payment_refund_tables.sql`，运费模板和商品计重字段位于 `db/init/032_trade_freight_template.sql`。

数据库表结构位于 `db/init/002_identity_tables.sql`，可重复执行（使用 `IF NOT EXISTS`）。

## 模块依赖约束

业务模块只能依赖 `shop-common` 和明确声明的领域接口，不允许跨模块直接访问 Repository 或数据库表。跨模块异步协作用 RocketMQ 领域事件，关键业务操作先完成本地事务，再通过 Outbox/事务消息发布事件。
