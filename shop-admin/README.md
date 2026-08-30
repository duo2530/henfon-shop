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

## 构建和启动

```powershell
mvn -DskipTests compile
mvn -DskipTests package
java -jar shop-boot/target/shop-boot-0.0.1-SNAPSHOT.jar
```

基础接口：`GET http://127.0.0.1:8080/api/health`。

身份认证接口：

- `POST /api/admin/auth/login`：管理员登录，JSON 请求体 `{ "username": "admin", "password": "123456" }`
- `GET /api/admin/auth/me`：携带 `Authorization: Bearer <token>` 获取当前用户和权限

门户会员认证接口：

- `POST /api/portal/auth/register`：账号密码注册，JSON 请求体 `{ "username": "alice", "password": "123456", "nickname": "Alice" }`
- `POST /api/portal/auth/login`：用户名、手机号或邮箱登录，JSON 请求体 `{ "account": "alice", "password": "123456" }`
- `POST /api/portal/auth/refresh`：使用登录返回的 `refreshToken` 换取新的访问令牌，刷新令牌轮换后旧令牌立即失效。
- `POST /api/portal/auth/logout`：退出并失效当前访问令牌，可在请求体中传入 `{ "refreshToken": "..." }` 删除刷新令牌。
- 登录返回的 `accessToken` 应在后续门户请求中通过 `Authorization: Bearer <token>` 携带；会员、购物车、订单和会员优惠券接口会校验 JWT 主体，不再信任客户端伪造的 `memberId`。Refresh Token 和访问令牌黑名单依赖 Redis，请确保 Redis 已启动并正确配置。

首次启动会自动初始化平台根部门、超级管理员角色、系统菜单权限和开发环境管理员账号：`admin / 123456`。生产环境必须立即修改密码并覆盖 `SHOP_JWT_SECRET`。

身份权限接口前缀：`/api/admin/system`，当前已提供系统用户分页/新增/修改/删除、部门、角色、菜单、数据规则查询与保存，以及用户角色、角色菜单、角色数据规则关系替换接口。接口已通过 `@PreAuthorize` 按菜单权限编码进行校验。

管理端前端通过 `/api/admin/auth/menus` 获取当前用户可见菜单，通过 `/api/admin/system/menus` 获取菜单配置树；角色、系统用户和数据权限页面的保存操作会回写对应身份权限接口，不依赖前端写死的菜单数据。

会员管理接口前缀：`/api/admin/member`，`GET /users` 支持关键字、等级、状态分页查询，`PUT /users/{id}/status?status=0|1` 用于冻结或解冻会员，操作需要 `member:user:query` 或 `member:user:status` 权限。

商品目录接口前缀：`/api/admin/catalog`，支持商品分页查询、保存、逻辑删除和启用类目查询；交易订单基础接口前缀：`/api/admin/trade`，支持订单分页和订单明细查询；库存接口前缀：`/api/admin/inventory`，支持库存台账分页、初始化和增减调整，订单创建/取消/发货分别自动执行库存预占/释放/扣减。门户交易接口支持订单详情、物流轨迹、会员取消订单和确认收货，创建订单可传 `idempotencyKey` 防止重复提交，服务端会校验明细小计与应付金额一致性。商品目录、交易和库存表结构分别位于 `db/init/003_catalog_tables.sql`、`db/init/004_trade_tables.sql`、`db/init/006_inventory_tables.sql`、`db/init/007_trade_idempotency.sql`；会员地址默认唯一约束位于 `db/init/008_member_address_default.sql`。

数据库表结构位于 `db/init/002_identity_tables.sql`，可重复执行（使用 `IF NOT EXISTS`）。

## 模块依赖约束

业务模块只能依赖 `shop-common` 和明确声明的领域接口，不允许跨模块直接访问 Repository 或数据库表。跨模块异步协作用 RocketMQ 领域事件，关键业务操作先完成本地事务，再通过 Outbox/事务消息发布事件。
