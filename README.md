# Henfon Shop

> 一个包含商城管理端、用户门户和模块化后端的电商业务系统。

![JDK](https://img.shields.io/badge/JDK-21-3776AB)
![Spring%20Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-6DB33F)
![React](https://img.shields.io/badge/React-19-61DAFB)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6)
![MySQL](https://img.shields.io/badge/MySQL-8.4-4479A1)
![Redis](https://img.shields.io/badge/Redis-7-DC382D)

## 项目简介

商品展示、会员认证、购物车、订单履约、支付退款、库存管理和运营分析都在同一套系统里。

仓库分三部分：

| 模块 | 目录 | 说明 |
| --- | --- | --- |
| 业务后端 | `shop-admin` | Java 21 / Spring Boot 3.4.5 模块化单体，11 个 Maven 模块，统一提供认证、商品、交易、支付、库存、营销、内容、报表与集成能力 |
| 商城管理端 | `shop-web` | React 19 管理后台，面向商品运营、仓储、客服、财务和系统管理员 |
| 用户门户 | `shop-portal` | React 19 顾客端，提供商品浏览、注册登录、购物车、下单支付、订单物流、售后、收藏与商品对比 |

两个前端共用同一套后端服务和数据。管理端的业务数据以服务端接口和数据库为准，接口失败或返回空时展示错误或空状态，不用本地演示数据兜底。

几个比较明显的特点：后端按业务域拆成 11 个 Maven 模块，不是把代码堆在一个包里；交易链路覆盖得比较全，含订单审核、超时关单、库存预占、售后与物流；数据库按编号脚本逐版演进并带迁移校验；对 MySQL、Redis、RocketMQ、MinIO 有依赖，适合做接近真实链路的本地联调。

## 项目结构

```text
henfon-shop
├─ shop-admin/                         # Java 21 / Spring Boot 后端
│  ├─ shop-common/                     # 统一响应体、异常体系、请求 ID 与通用工具
│  ├─ shop-identity/                   # 管理员、会员、认证、RBAC、数据权限、审计
│  ├─ shop-catalog/                    # 商品、SKU、类目、商品内容与商品审核
│  ├─ shop-trade/                      # 购物车、订单、订单审核、售后、物流与 Outbox
│  ├─ shop-inventory/                  # 多仓库存、预占释放、预警、供应商、采购与盘点
│  ├─ shop-marketing/                  # 优惠券、秒杀与秒杀预约
│  ├─ shop-payment/                    # 支付单、微信支付、退款、发票与资金对账
│  ├─ shop-content/                    # 轮播图、评价、站内通知与邮件投递
│  ├─ shop-reporting/                  # 销售、商品与会员经营分析、事件投影
│  ├─ shop-integration/                # 快递物流查询、对象存储等外部集成
│  ├─ shop-boot/                       # Spring Boot 启动模块、全局配置与健康检查
│  └─ db/                              # 数据库初始化脚本与迁移校验脚本
├─ shop-web/                           # React 管理端
│  ├─ src/components/                  # 按业务域分组的页面组件
│  ├─ src/context/                     # 管理端全局状态
│  └─ tests/                           # 无障碍与流程冒烟测试
├─ shop-portal/                        # React 用户门户
│  ├─ src/components/                  # 商品、购物车、结算、支付、订单等组件
│  └─ tests/                           # 组件、流程与无障碍冒烟测试
├─ docs/                               # 接口、部署、开发与上线文档
│  └─ screenshots/                     # README 引用的运行截图（管理端 23 + 门户 8）
├─ docker-compose.yml                  # 本地完整环境编排
├─ .env.example                        # Docker 环境变量示例
└─ README.md
```

模块的职责划分以 `shop-admin/pom.xml` 中声明的为准。

- `shop-common`：统一响应体、业务异常、请求 ID 过滤器、通用基础设施
- `shop-identity`：管理员与会员账号、认证登录、RBAC 角色权限、数据权限、审计日志
- `shop-catalog`：商品、SKU、类目、商品内容与审核
- `shop-trade`：购物车、订单、订单审核、售后申请、物流与 Outbox 事件表
- `shop-inventory`：仓库、库存、预占与释放、库存预警、供应商、采购与盘点
- `shop-marketing`：优惠券模板与领取核销、秒杀活动与预约
- `shop-payment`：支付单、微信支付 V3、退款、发票、资金对账
- `shop-content`：轮播图、商品评价与回复、站内通知与邮件投递
- `shop-reporting`：销售趋势、商品排行、会员分析、事件投影
- `shop-integration`：快递 100 物流查询、RocketMQ 事件发布、MinIO 对象存储
- `shop-boot`：启动模块、全局配置、健康检查接口

商品到支付的主链路在 `shop-catalog`、`shop-trade`、`shop-inventory`、`shop-payment` 四个模块里；`shop-identity` 的会员能力配合 `shop-marketing`、`shop-content` 做会员沉淀与营销触达；`shop-reporting` 基于事件投影产出分析数据；`shop-integration` 封装快递 100、MinIO 与 RocketMQ，`shop-trade` 的 Outbox 负责事件可靠投递。

接口约定：管理端接口统一以 `/api/admin` 开头，用户门户接口统一以 `/api/portal` 开头。完整字段、错误码与接口清单见 [API 契约](docs/API契约.md)。

## 技术栈

后端：JDK 21、Spring Boot 3.4.5、MyBatis-Plus 3.5.7、JWT（jjwt 0.12.6）、Maven 聚合多模块。

管理端与用户门户：React 19、TypeScript 5、Vite 6、Tailwind CSS 4，图表用 Recharts，图标用 Lucide，动效用 motion。门户另外用了 qrcode（支付二维码）和高德地图 JS API（地址选点）。

数据与中间件：MySQL 8.4、Redis 7、RocketMQ 5.3.1、MinIO。

## 当前已实现的能力

- 管理员登录、会员注册登录、JWT 令牌刷新与黑名单
- RBAC 角色权限、菜单权限、数据权限规则
- 登录日志与操作审计日志
- 商品、SKU、类目与商品内容审核
- 购物车、订单创建、订单审核、订单备注、超时关单
- 微信支付 V3（Native 扫码）、退款、发票与资金对账
- 多仓库存、库存预占与释放、库存预警
- 供应商、采购与库存盘点
- 优惠券领取核销、秒杀活动与秒杀预约
- 轮播图、商品评价与评价回复、站内通知与邮件投递
- 物流查询（快递 100）与物流承运商字典
- 会员资料、地址、收藏、商品对比记录
- 销售趋势、商品排行、会员分析与 CSV 报表导出
- 订单 Outbox 事件、RocketMQ 异步投递与失败重试审计

门户组件：`HeroBanner`、`Navbar`（首页导航与轮播）、`ProductCard`、`ProductQuickView`（列表与快速查看）、`CartDrawer`（购物车抽屉）、`CheckoutModal`、`AmapAddressPicker`（结算与地址）、`PaymentModal`、`OrderSuccessModal`、`InvoiceModal`（支付与开票）、`OrdersModal`、`OrderTracking`（订单与物流）、`AuthModal`、`UserProfileModal`（账号资料）、`WishlistModal`、`CompareModal`、`CompareChartsView`、`CompareFloatingBar`（收藏与对比）、`CouponCenter`（领券中心）。

## 页面截图

截图取自本机实际运行环境（后端 `8080` + 管理端 `3000` + 门户 `3001`），由浏览器逐页真实访问后截取，未做后期处理。页面内容都来自服务端接口——商品数、订单号、优惠券进度、地图渲染都是真实数据。门户的演示开关 `VITE_DEMO_MODE` 默认关闭，没有前端 mock 兜底。

### 管理端（shop-web，23 张）

#### 登录页

![管理端登录页](docs/screenshots/admin-login.png)

#### 工作台

实时汇总今日销售额、订单数、用户数、商品总数，并给出销售趋势图、待办事项与最近订单。

![管理端工作台](docs/screenshots/admin-dashboard.png)

#### 电商运营中心

商品与库存管理（SPU/SKU、售价与成本毛利、安全库存预警、批量上下架）、订单履约与售后、商品类目树、客户与会员资产。

![商品与库存管理](docs/screenshots/admin-products.png)

![订单履约与售后管理](docs/screenshots/admin-orders.png)

![商品类目维护](docs/screenshots/admin-categories.png)

![客户与会员中心](docs/screenshots/admin-members.png)

#### 营销与促销中心

优惠券模板与核销统计、限时秒杀活动管理。

![营销卡券与优惠体系](docs/screenshots/admin-coupons.png)

![限时秒杀活动管理](docs/screenshots/admin-flash-sales.png)

#### 仓储进销存

多仓库存台账、调拨与出入库流水、供应商档案与采购管理。

![仓库进销存与调拨](docs/screenshots/admin-inventory-stock.png)

![供应商档案与采购管理](docs/screenshots/admin-inventory-suppliers.png)

#### 财务结算中心

资金流水与交易对账、发票与税务管理。

![财务结算与资金对账](docs/screenshots/admin-finance-transactions.png)

![发票与税务管理](docs/screenshots/admin-finance-invoices.png)

#### 经营分析与 BI

经营全景与流量大屏（销售额与订单趋势、支付渠道分析）、商品销售排行与动销分析。

![经营全景与流量大屏](docs/screenshots/admin-analytics-overview.png)

![商品销售排行与动销分析](docs/screenshots/admin-analytics-products.png)

#### 内容与客户运营

轮播海报与页面装修、客户评价与晒单管理。

![轮播海报与页面装修](docs/screenshots/admin-content-banners.png)

![客户评价与晒单管理](docs/screenshots/admin-content-reviews.png)

#### 系统与参数设置

系统参数配置、管理员登录记录、关键请求的操作审计。

![系统设置](docs/screenshots/admin-settings.png)

![登录记录](docs/screenshots/admin-login-logs.png)

![操作审计](docs/screenshots/admin-operation-logs.png)

#### 系统管理（RBAC）

系统用户与部门、角色权限、菜单与按钮级权限树、行级数据权限与字段脱敏。

![系统用户管理](docs/screenshots/admin-system-users.png)

![角色权限管理](docs/screenshots/admin-roles.png)

![系统菜单管理](docs/screenshots/admin-menus.png)

![数据权限与行级隔离管控](docs/screenshots/admin-data-permissions.png)

### 用户门户（shop-portal，8 张）

#### 首页

类目导航、Banner 轮播、优惠券提示条与商品瀑布流。

![用户门户首页](docs/screenshots/portal-home.png)

#### 商品列表

商品检索、SKU 筛选、对比模式、现货过滤与排序切换。

![商品列表与筛选](docs/screenshots/portal-products.png)

#### 商品详情（快速预览）

规格与版本选择、库存实时数量、评价数与累计销量、加入购物车。

![商品详情快速预览](docs/screenshots/portal-product-detail.png)

#### 领券中心

优惠券按「全场通用 / 大额神券 / 品类专享」分组，展示剩余比例与有效期。

![领券中心](docs/screenshots/portal-coupon-center.png)

#### 购物车

多商品勾选、数量增减、优惠券选择与实时金额计算。

![购物车抽屉](docs/screenshots/portal-cart.png)

#### 结算下单

收货地址、商品清单、配送时间选择与支付方式确认。

![确认订单并结算](docs/screenshots/portal-checkout.png)

#### 新增收货地址（高德地图选点）

集成高德地图 Web JS API，支持地址搜索与地图点选自动回填地区。

![新增收货地址与高德地图选点](docs/screenshots/portal-address-map.png)

#### 会员登录

密码登录与邮箱注册两个入口，找回密码走邮箱一次性令牌。

![会员登录](docs/screenshots/portal-login.png)

### 页面与业务关系

| 业务阶段 | 门户组件 | 管理端页面 | 支撑模块 | 截图 |
| --- | --- | --- | --- | --- |
| 浏览与引流 | `HeroBanner`、`ProductCard`、`CouponCenter` | 商品管理、轮播图、优惠券 | `shop-catalog`、`shop-marketing`、`shop-content` | [门户首页](docs/screenshots/portal-home.png) · [商品列表](docs/screenshots/portal-products.png) · [商品管理](docs/screenshots/admin-products.png) |
| 购物车与下单 | `CartDrawer`、`CheckoutModal`、`AmapAddressPicker` | 订单管理 | `shop-trade`、`shop-catalog`、`shop-inventory` | [购物车](docs/screenshots/portal-cart.png) · [结算](docs/screenshots/portal-checkout.png) · [地址选点](docs/screenshots/portal-address-map.png) |
| 支付与开票 | `PaymentModal`、`InvoiceModal` | 发票管理、资金对账 | `shop-payment` | [发票与税务](docs/screenshots/admin-finance-invoices.png) · [资金对账](docs/screenshots/admin-finance-transactions.png) |
| 履约与物流 | `OrdersModal`、`OrderTracking` | 订单管理（发货、批量发货）、系统设置（物流字典） | `shop-trade`、`shop-integration` | [订单履约](docs/screenshots/admin-orders.png) · [系统设置](docs/screenshots/admin-settings.png) |
| 会员与营销 | `AuthModal`、`UserProfileModal`、`WishlistModal`、`CompareModal` | 会员管理、优惠券、秒杀 | `shop-identity`、`shop-marketing` | [会员登录](docs/screenshots/portal-login.png) · [领券中心](docs/screenshots/portal-coupon-center.png) · [优惠券](docs/screenshots/admin-coupons.png) · [秒杀](docs/screenshots/admin-flash-sales.png) |
| 售后与评价 | `OrdersModal`（售后申请） | 订单管理、评价管理 | `shop-trade`、`shop-content` | [客户评价](docs/screenshots/admin-content-reviews.png) |
| 经营复盘 | — | 工作台、经营分析、资金对账 | `shop-reporting`、`shop-payment` | [工作台](docs/screenshots/admin-dashboard.png) · [经营大屏](docs/screenshots/admin-analytics-overview.png) · [商品动销榜](docs/screenshots/admin-analytics-products.png) |

## 环境要求

| 环境 | 版本或建议 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| JDK | 21 | 必须 | 后端编译与运行 |
| Maven | 3.8+ | 必须 | 后端构建与测试 |
| Node.js | 18+，建议 LTS | 必须 | 两个前端的开发与构建 |
| MySQL | 8.x | 必须 | 业务数据存储 |
| Redis | 7.x | 必须 | 令牌、缓存与限流 |
| Docker Desktop | 最新稳定版 | 推荐 | 本地一键准备后端与依赖服务 |
| MinIO | 按需 | 可选 | 商品媒体、评价凭证等对象存储 |
| RocketMQ | 5.x | 建议 | Outbox、通知与报表异步事件 |

## 快速开始

### 1. 准备基础环境

装好 JDK 21、Maven 3.8+、Node.js 18+（建议 LTS），以及 Docker Desktop（推荐）或本地的 MySQL 8、Redis 7。

### 2. 初始化数据库

MySQL 首次启动会按 `shop-admin/db/init` 的编号顺序执行初始化脚本。手动导入时逐个执行该目录下的 SQL，并确认后端配置的库名与实际一致（默认 `henfon-shop`）。

### 3. 启动后端与基础设施

在仓库根目录执行：

```powershell
Copy-Item .env.example .env
# 编辑 .env，替换其中所有 change-me 配置
docker compose up -d --build
```

不想用 Docker 的话，本地准备好 MySQL、Redis（按需加 MinIO、RocketMQ）后执行：

```powershell
cd shop-admin
mvn -DskipTests compile
mvn -DskipTests package
java -jar shop-boot/target/shop-boot-0.0.1-SNAPSHOT.jar
```

开发配置默认走 `dev` Profile，可以用 `SPRING_PROFILES_ACTIVE` 或 `--spring.profiles.active` 在 `dev`、`test`、`prod` 之间切换。

### 4. 启动两个前端

```powershell
# 管理端
cd shop-web
npm install
Copy-Item .env.example .env.local
npm run dev

# 新开一个终端启动用户门户
cd shop-portal
npm install
Copy-Item .env.example .env.local
npm run dev -- --port 3001
```

两个前端默认都用 Vite 的 `3000` 端口，所以第二个进程必须显式指定端口。

### 5. 访问地址

| 模块 | 地址 |
| --- | --- |
| 后端 API | `http://localhost:8080` |
| 管理端 | `http://localhost:3000` |
| 用户门户 | `http://localhost:3001` |
| MinIO API / 控制台 | `http://localhost:9000` / `http://localhost:9001` |
| RocketMQ NameServer | `localhost:9876` |

业务接口需要鉴权，未登录直接访问会返回统一 JSON 错误体（比如访问 `/api/health` 返回 `AUTH_REQUIRED`），这是预期行为。

### 6. 默认账号

开发初始化数据提供管理员账号 `admin / 123456`，仅限本地联调，首次登录后应立即改密码。

建议按 MySQL → Redis → RocketMQ（按需）→ MinIO（按需）→ `shop-admin` → `shop-web` → `shop-portal` 的顺序启动。

## 配置说明

### 后端

配置文件在 `shop-admin/shop-boot/src/main/resources/` 下，按 Profile 分 `application.yml`、`application-dev.yml`、`application-test.yml`、`application-prod.yml`。未显式指定 `SPRING_PROFILES_ACTIVE` 时默认用 `dev`。

服务本身：端口 `8080`（`SHOP_SERVER_PORT`），上下文路径 `/`（`SHOP_CONTEXT_PATH`），上传限制单文件 20MB、单请求 50MB，健康与指标端点是 `/actuator/health`、`/actuator/info`、`/actuator/metrics`。

**MySQL**：开发环境默认 `127.0.0.1:3306`，库名 `henfon-shop`，账号 `root / 123456`。对应 `SHOP_MYSQL_URL`、`SHOP_MYSQL_USERNAME`、`SHOP_MYSQL_PASSWORD`。

**Redis**：开发环境默认 `127.0.0.1:6379`，密码 `123456`，逻辑库 `0`。如果你的本地 Redis 没有密码，需要自己调整。对应 `SHOP_REDIS_HOST`、`SHOP_REDIS_PORT`、`SHOP_REDIS_PASSWORD`、`SHOP_REDIS_DATABASE`。

**RocketMQ**：NameServer 默认 `127.0.0.1:9876`。开发环境消费监听器默认关闭（`SHOP_ROCKETMQ_CONSUMER_ENABLED=false`），免得本地没有 MQ 时反复报错；要联调订单 Outbox、通知或报表事件时，先起 RocketMQ 再把这个变量改成 `true`。

**MinIO**：默认 `http://127.0.0.1:9000`，账号密钥都是 `minioadmin`，bucket 为 `shop`。不用文件上传能力时可以不启动。

**JWT**：开发环境有默认密钥，过期时间 8 小时（`SHOP_JWT_EXPIRATION_SECONDS=28800`）。生产环境必须替换 `SHOP_JWT_SECRET`。

**邮件与通知**：开发环境通过 163 邮箱的 SMTP 发送找回密码邮件和业务通知，站内通知与邮件开关由 `SHOP_EMAIL_NOTIFICATION_ENABLED` 控制。SMTP 账号和授权码建议全部走环境变量。

**微信支付**：开发环境已配置微信支付 V3，默认按 `NATIVE`（网页二维码）启用，参数包括 AppID、商户号、商户证书序列号、API v3 密钥、商户私钥路径、平台公钥 ID 与公钥路径。两个回调地址必须是微信服务器能访问到的公网 HTTPS 地址，并且与商户平台配置一致：支付通知 `/api/wx/pay/notify/v3`，退款通知 `/api/wx/pay/refund/notify/v3`。暂时不调微信接口就把 `SHOP_WECHAT_PAY_ENABLED` 改成 `false`。

**物流查询**：已接入快递 100 查询接口（`SHOP_KUAIDI100_*`），不联动时可以关掉。

### 管理端（shop-web）

关键文件是 `package.json`、`vite.config.ts`、`.env.example`。开发端口 `3000`，接口地址通过 `VITE_API_BASE_URL` 指定，要指向实际后端地址（例如 `http://127.0.0.1:8080`）。

### 用户门户（shop-portal）

关键文件同上。开发端口默认 `3000`，与后端联调时建议用 `--port 3001`。除 `VITE_API_BASE_URL` 外还有三项：

- `VITE_AMAP_KEY` / `VITE_AMAP_SECURITY_CODE`：地址搜索与地图选点必需
- `VITE_DEMO_MODE`：仅用于本地演示，生产环境必须保持 `false` 或不配置

改完 `.env.local` 要重启 Vite 才会生效。

## 测试与构建

```powershell
# 后端单元与集成测试
cd shop-admin
mvn -pl shop-identity,shop-trade,shop-boot -am test

# 管理端类型检查与冒烟测试
cd ..\shop-web
npm run lint
npm test

# 用户门户类型检查与冒烟测试
cd ..\shop-portal
npm run lint
npm test
```

后端依赖外部服务的集成测试默认跳过，依赖服务可用时设 `SHOP_IT_ENABLED=true` 再执行。两个前端都可以用 `npm run build` 出生产构建产物。

## 生产部署

仓库里已有 `application-prod.yml`、`application-test.yml` 和 Docker 相关文件，`docker-compose.yml` 以 `prod` Profile 启动后端，但没有配套的生产部署文档、脚本或演示环境地址。

需要正式上线时，还要补这几块：Nginx 反向代理与前端静态资源部署、JAR 部署与进程守护、MySQL / Redis / RocketMQ / MinIO 的生产参数与高可用、微信支付正式环境参数与回调域名、日志监控备份与灾备策略（可参考 [灾备与故障演练](docs/灾备与故障演练.md)）。

## 安全提示

`application-dev.yml` 里直接写入了可用于真实联调的敏感值：邮件授权码、微信支付 API v3 密钥与商户信息、物流查询密钥、JWT 默认密钥。这些是开发环境凭证，仓库一旦外传就可能被滥用。

建议这样处理：

1. 把开发配置里的敏感值全部改成只从环境变量读取，不留可用默认值
2. 对已经进版本库的凭证做一次轮换（邮箱授权码、微信支付密钥、物流平台密钥、JWT 密钥）
3. 生产环境由部署平台注入真实配置，不要用示例值或开发值
4. `.env` 与 `.env.local` 不要提交到仓库

## 注意事项

### 数据库脚本只增不改

`shop-admin/db/init` 下的脚本是版本基线，改已有脚本会让历史环境和新环境不一致。结构变更请新增编号脚本，然后跑一次迁移校验：

```powershell
powershell -ExecutionPolicy Bypass -File shop-admin/db/migration/verify-migrations.ps1
```

### 两个前端默认端口冲突

管理端和门户默认都是 `3000`，同时启动时第二个进程必须显式指定端口，例如 `npm run dev -- --port 3001`。

### 微信支付回调必须是公网 HTTPS

本地开发要用内网穿透提供地址，并保证与微信商户平台上配的一致，否则收不到支付和退款通知。

### 管理端不允许 mock 兜底

管理端的业务数据以服务端接口和数据库为准，接口失败或返回空时应该展示错误或空状态，不允许回退到演示数据。

### 改完环境变量要重启前端

Vite 只在开发服务启动时读 `.env.local`。

## 常见问题

**后端连不上 MySQL 或 Redis**：先看 MySQL、Redis 是否启动，当前 Profile 用的库名、端口、账号密码是否和实际一致，用 Docker Compose 时 `.env` 里的 `change-me` 是否都替换了。

**前端请求接口失败**：依次确认后端已启动、`.env.local` 里的 `VITE_API_BASE_URL` 指向正确、改完环境变量后重启过 Vite、本地端口没被占用。

**RocketMQ 或 MinIO 连接失败**：只联调商品和订单基础流程时，可以先关掉不需要的异步消费者或文件功能；需要完整事件、文件上传或物流链路时再启动对应服务，并保证地址是后端能访问到的。

**商品图片或评价凭证上传失败**：检查 MinIO 是否启动、endpoint 后端能否访问、bucket 是否存在。

**支付功能跑不通**：微信支付依赖真实商户环境，本地至少要满足公网可访问的 HTTPS 回调地址、有效的商户证书与私钥、与商户平台一致的参数。只做基础开发可以先关掉支付。

## 项目文档

- [API 契约](docs/API契约.md)
- [部署指南](docs/部署指南.md)
- [开发计划](docs/开发计划.md)
- [管理端真实数据改造方案](docs/管理端真实数据改造方案.md)
- [UAT 上线检查清单](docs/UAT上线检查清单.md)
- [灾备与故障演练](docs/灾备与故障演练.md)
- [文档目录](docs/文档目录.md)
- [数据库迁移说明](shop-admin/db/migration/README.md)

## 许可证

本项目的许可证与使用范围以仓库维护者的正式声明为准。
