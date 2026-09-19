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
│  └─ db/                              # 数据库脚本与演示数据
│     ├─ init/                         # 逐版增量迁移脚本（001~054）
│     ├─ schema/henfon-shop.sql        # 完整结构基线，一条命令建好 60 张表
│     ├─ migration/                    # 迁移脚本的版本校验
│     └─ seed/                         # 演示数据：27 张表快照 + 399 张图片 + 导入脚本
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

门户组件：`HeroBanner`、`Navbar`（首页导航与轮播）、`ProductCard`、`ProductQuickView`（列表与快速查看）、`CartDrawer`（购物车抽屉）、`CheckoutModal`、`AmapAddressPicker`（结算与地址）、`PaymentModal`、`OrderSuccessModal`、`InvoiceModal`（支付与开票）、`OrdersPage`、`OrderTracking`（订单与物流）、`AuthModal`、`UserProfileModal`（账号资料）、`WishlistModal`、`CompareModal`、`CompareChartsView`、`CompareFloatingBar`（收藏与对比）、`CouponCenter`（领券中心）。

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
| 履约与物流 | `OrdersPage`、`OrderTracking` | 订单管理（发货、批量发货）、系统设置（物流字典） | `shop-trade`、`shop-integration` | [订单履约](docs/screenshots/admin-orders.png) · [系统设置](docs/screenshots/admin-settings.png) |
| 会员与营销 | `AuthModal`、`UserProfileModal`、`WishlistModal`、`CompareModal` | 会员管理、优惠券、秒杀 | `shop-identity`、`shop-marketing` | [会员登录](docs/screenshots/portal-login.png) · [领券中心](docs/screenshots/portal-coupon-center.png) · [优惠券](docs/screenshots/admin-coupons.png) · [秒杀](docs/screenshots/admin-flash-sales.png) |
| 售后与评价 | `OrdersPage`（售后申请） | 订单管理、评价管理 | `shop-trade`、`shop-content` | [客户评价](docs/screenshots/admin-content-reviews.png) |
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

用 `shop-admin/db/schema/henfon-shop.sql` 建库。它自带 `CREATE DATABASE` 和 `USE`，一条命令建好 60 张表，外加默认仓库、物流承运商字典、配送模板这三份基础数据（共 6 行）：

```powershell
mysql -uroot -p --default-character-set=utf8mb4 < shop-admin/db/schema/henfon-shop.sql
```

`shop-admin/db/init/` 是逐版增量迁移脚本，给已有环境升级用，新环境不需要逐个执行——其中 `045` 和 `046` 用了 MariaDB 专有的 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`，MySQL 8.4 解析不了，从零顺序执行会停在 `045`，后面 9 个脚本都不再执行，建出来的库只有 55 张表、比完整结构少 69 个列；即使让 mysql 跳过错误继续跑，这两个脚本负责的 6 个列也一样加不上。schema 文件头已记录这几处已知差异，并按原始意图补齐了对应的列。需要校验迁移脚本有没有被改动或遗漏时跑：

```powershell
powershell -ExecutionPolicy Bypass -File shop-admin/db/migration/verify-migrations.ps1
```

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

## 演示数据

新环境建出来的库只有表结构和基础数据，商品、订单、会员都是空的，页面上一张图也没有。`shop-admin/db/seed/` 里备了一份快照，导入后门户和管理端直接就是有图有数据的状态：

| 文件 | 内容 |
| --- | --- |
| `demo-data.sql` | 27 张业务表的 `INSERT IGNORE` 快照（0.33 MB），覆盖商品、SKU、商品媒体、库存与流水、营销活动、会员与地址收藏、购物车、订单与物流、售后、发票、站内通知和登录日志 |
| `media/` | 399 张图片，目录结构与库里的对象键一一对应 |
| `import-demo.mjs` | 零依赖上传脚本，把 `media/` 传到 MinIO 的 `shop` 桶 |

先把 MinIO 起起来（`docker compose up -d minio`），然后在 `shop-admin` 目录执行：

```powershell
mysql -uroot -p --default-character-set=utf8mb4 henfon-shop < db/seed/demo-data.sql
node db/seed/import-demo.mjs
```

第二行需要 Node 18+，不装任何 npm 包。凭据要对得上：脚本默认连 `http://127.0.0.1:9000`、用 `minioadmin / minioadmin`，而 `docker compose` 起的 MinIO 账号密码取的是 `.env` 里的 `MINIO_ACCESS_KEY` 和 `MINIO_SECRET_KEY`（示例值是 `henfon-minio`），两者不一样会报 403。用 compose 起的就先把这两个变量设成同样的值：

```powershell
$env:MINIO_ACCESS_KEY = "henfon-minio"
$env:MINIO_SECRET_KEY = "change-me-minio-secret"
node db/seed/import-demo.mjs
```

跑完可以去 MinIO 控制台 `http://localhost:9001` 看 `shop` 桶里有没有 `media/` 目录，或者直接打开门户首页看商品图。重复执行不会产生脏数据：已存在的图片会跳过（加 `--force` 覆盖重传），SQL 全部是 `INSERT IGNORE`。脚本还支持 `--sql` 顺带导入快照，这时需要 mysql 客户端在 `PATH` 里，或用 `MYSQL_BIN` 指定绝对路径、`MYSQL_PWD` 传密码。

参数、验证方式和图片地址的存放约定见 [shop-admin 的说明](shop-admin/README.md#演示数据与媒体)。

## 配置说明

### 后端

配置文件在 `shop-admin/shop-boot/src/main/resources/` 下，按 Profile 分 `application.yml`、`application-dev.yml`、`application-test.yml`、`application-prod.yml`。未显式指定 `SPRING_PROFILES_ACTIVE` 时默认用 `dev`。每个值都写成 `${环境变量:默认值}`，所以同一个 jar 换环境只改环境变量，不用改代码。

服务本身：端口 `8080`（`SHOP_SERVER_PORT`），上下文路径 `/`（`SHOP_CONTEXT_PATH`），上传限制单文件 20MB、单请求 50MB（`SHOP_UPLOAD_MAX_FILE_SIZE`、`SHOP_UPLOAD_MAX_REQUEST_SIZE`），健康与指标端点是 `/actuator/health`、`/actuator/info`、`/actuator/metrics`。

`application-dev.yml` 里已经填了可用的本机默认值（含邮箱授权码、微信支付联调参数、快递 100 测试账号），直接 `java -jar` 起就能跑。走 `docker compose` 时后端是 `prod` profile，这些默认值一律不生效，必须在 `.env` 里补齐——两条路径要改的变量不一样，下面分开标注。

下面按必需程度排，标了「必配」的不配好，几乎每个接口都会报错，其余按用到的功能取舍。

#### MySQL（必配）

| 变量 | 说明 |
| --- | --- |
| `SHOP_MYSQL_URL` | JDBC 连接串。dev 默认 `jdbc:mysql://127.0.0.1:3306/henfon-shop`，带 `characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true` |
| `SHOP_MYSQL_USERNAME` / `SHOP_MYSQL_PASSWORD` | dev 默认 `root` / `123456` |

库要先建好（见「快速开始」第 2 步），账号需要有该库的读写权限。

URL 里的参数不要删：`characterEncoding=utf8`、`serverTimezone=Asia/Shanghai` 关系到中文和时间，`allowPublicKeyRetrieval=true` 是 MySQL 8 默认认证插件 `caching_sha2_password` 在不开 SSL 时的必要条件，去掉会报 `Public Key Retrieval is not allowed`。

`prod` 下 `SHOP_MYSQL_URL` 没有默认值，必须给完整连接串（含上面那串参数），只填 host 是起不来的。

连不上数据库时后端不一定会立刻退出：dev 的 HikariCP 配了 `initialization-fail-timeout: -1`，启动阶段不校验连接，之后每条涉及数据库的接口才报错。所以「后端起来了但接口全错」先查这一项。

#### Redis（必配）

`SHOP_REDIS_HOST`、`SHOP_REDIS_PORT`、`SHOP_REDIS_USERNAME`、`SHOP_REDIS_PASSWORD`、`SHOP_REDIS_DATABASE`，dev 默认 `127.0.0.1:6379`、库 `0`、密码 `123456`。

本机 Redis 没设密码时要把 `SHOP_REDIS_PASSWORD` 显式置空，留着默认的 `123456` 会连不上。刷新令牌、访问令牌黑名单、找回密码令牌、登录失败计数都放在 Redis，连不上表现为「登录成功但刷新令牌立刻失效、重新登录也留不住会话」。

如果 Redis 上还跑着别的应用，`SHOP_REDIS_DATABASE` 换一个库号比自己改 key 前缀省事。

#### JWT（必配）

`SHOP_JWT_SECRET`：dev 内置了一个占位串，生产必须换成随机长值。生成一条：

```powershell
$bytes = New-Object byte[] 48
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

有效期由 `SHOP_JWT_EXPIRATION_SECONDS` 决定（dev 8 小时、prod 2 小时），签发方 `SHOP_JWT_ISSUER` 默认 `henfon-shop`。轮换密钥会让所有已签发的访问令牌立即失效，用户需要重新登录，这是预期行为。

#### 邮件：找回密码与业务通知（按需）

用到两处：门户「忘记密码」发一次性重置令牌，和订单、售后等事件触发的会员通知邮件。走标准 SMTP，163、QQ 邮箱、企业邮箱都行，但密码栏填的不是邮箱登录密码，是邮件平台单独签发的授权码。

以 163 邮箱为例：

1. 用浏览器登录邮箱网页版，进「设置 → POP3/SMTP/IMAP」，开启「SMTP 服务」。
2. 按提示用手机发短信做验证，通过后页面会显示一串 16 位授权码。只显示一次，先记下来。
3. 在配置或环境变量里填这几项：
   - `SHOP_MAIL_HOST`（163 是 `smtp.163.com`）、`SHOP_MAIL_PORT`（默认 `25`）
   - `SHOP_MAIL_USERNAME`：完整邮箱地址
   - `SHOP_MAIL_PASSWORD`：上一步的授权码
   - `SHOP_EMAIL_FROM`：发件人地址，必须和 `SHOP_MAIL_USERNAME` 相同，用别的地址发会被 163 拒收
   - `SHOP_EMAIL_SUBJECT_PREFIX`：邮件主题前缀，默认 `Henfon商城`
4. 打开通知开关 `SHOP_EMAIL_NOTIFICATION_ENABLED=true`（dev 默认 `true`，prod 默认 `false`）。
5. 重启后端，在门户点「忘记密码」提交自己的邮箱，能收到邮件就算通了。

换 QQ 邮箱、企业邮箱同理：host 换成各自的 SMTP 地址，授权码换成该平台生成的，其他不变。

端口与加密：dev 默认走 `25` + `SHOP_MAIL_SMTP_STARTTLS=true`（STARTTLS 方式）。163 的 SMTP 只提供 `25`（明文）和 `465`/`994`（隐式 SSL）这几个端口，如果所在网络封了 25，就得改用 465；而 465 需要 `mail.smtp.ssl.enable=true` 属性，当前配置只把 auth 和 starttls 暴露成了环境变量，这种情况要直接改 `application-dev.yml`。用 QQ 邮箱、企业邮箱的话支持 `587`，把 `SHOP_MAIL_PORT` 设成 587 即可。

`SHOP_MEMBER_PASSWORD_RESET_URL` 是重置链接的前缀，后端会在后面拼 `?resetToken=...`。dev 默认 `http://localhost:3000/`（门户开发端口），部署时改成门户的实际地址，否则邮件里的链接会落到管理端或者打不开。

排查提示：SMTP 没配好不会让后端启动失败，也不会在接口上报错。邮件发送器是通过 `ObjectProvider` 取的，拿不到就静默跳过，只在日志里留一行 debug（`邮件通知已启用但 SMTP 未配置，跳过...`）。「邮件收不到」时先搜这行日志，能直接区分是配置没生效还是被 SMTP 服务端拒了（后者会有 `553` 之类的报错栈）。另外业务通知只发给有合法邮箱的会员，会员没填邮箱也会静默跳过。

#### MinIO：商品图、评价图、售后凭证（按需）

`MINIO_ENDPOINT`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`、`MINIO_BUCKET`（默认 `shop`）。只跑商品、订单这类不动文件的流程，可以不启动。

桶不用手工建，第一次上传时后端会自己创建。文件地址是端到端签名地址、24 小时过期，库里存的是对象键而不是签名地址，所以换 endpoint 或换密钥后旧图会整体 403——修法是让后端和导入脚本的 `MINIO_*` 保持一致。

用 `docker compose` 起 MinIO 时，它的账号密码取自 `.env` 的 `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY`（示例值是 `henfon-minio`），而代码默认是 `minioadmin`，两边不一致会 403。

#### RocketMQ：Outbox 与异步事件（按需）

`ROCKETMQ_NAME_SERVER`（默认 `127.0.0.1:9876`）、`ROCKETMQ_PRODUCER_GROUP`、`ROCKETMQ_SEND_TIMEOUT`、`ROCKETMQ_RETRY_TIMES`。

dev 默认把全部消费监听器摘掉（`SHOP_ROCKETMQ_CONSUMER_ENABLED=false`），所以本机没装 MQ 也能正常启动，代价是通知、报表投影这类副作用只能靠数据库轮询兜底（导出等 1 分钟出结果是这个原因，不是故障）。要联调完整事件链，先起 RocketMQ，再把 `SHOP_ROCKETMQ_CONSUMER_ENABLED` 改成 `true`。

`prod` profile 下监听器无条件注册，这个开关不起作用，所以生产环境必须真的能连上 NameServer。

#### 微信支付 V3（按需）

参数分两处取：微信商户平台和公众号/小程序后台。

1. 商户平台拿到商户号，10 位数字，填 `SHOP_WECHAT_PAY_MERCHANT_ID`（不是 AppID）。
2. 公众号或小程序后台拿到 AppID 填 `SHOP_WECHAT_PAY_APP_ID`，并在商户平台把 AppID 与商户号关联，否则下单会报 AppID 与 mchid 不匹配。
3. 「账户中心 → API 安全」申请商户 API 证书，下载 `apiclient_key.pem`，绝对路径填 `SHOP_WECHAT_PAY_MERCHANT_PRIVATE_KEY_PATH`；同一页面能看到证书序列号，填 `SHOP_WECHAT_PAY_MERCHANT_SERIAL_NUMBER`。
4. 同一页面设置 APIv3 密钥，32 位，填 `SHOP_WECHAT_PAY_API_V3_KEY`。商户号、APIv3 密钥、证书私钥三者不匹配时下单会报签名错误。
5. 验签材料二选一：下载平台证书（`SHOP_WECHAT_PAY_PLATFORM_CERTIFICATE_PATH`），或者用平台公钥（`SHOP_WECHAT_PAY_PUBLIC_KEY_ID` + `SHOP_WECHAT_PAY_PUBLIC_KEY_PATH`）。dev 默认走平台公钥。
6. 回调地址必须是微信服务器能访问到的公网 HTTPS，本地开发用内网穿透：`SHOP_WECHAT_PAY_NOTIFY_URL` 填 `/api/wx/pay/notify/v3`，`SHOP_WECHAT_PAY_REFUND_NOTIFY_URL` 填 `/api/wx/pay/refund/notify/v3`。这两个路径是代码里写死的原始通知入口（带验签和 AES-GCM 解密），填成旧的 `/api/wx/pay/notify` 或者 localhost 都收不到通知。

支付场景由 `SHOP_WECHAT_PAY_MODE` 决定，门户当前按 `NATIVE` 扫码设计，不要填小写或中文。

`SHOP_WECHAT_PAY_ENABLED=false` 时不会伪造支付成功：下单和退款会直接抛「微信支付未启用」的异常。也就是说商品浏览、购物车、下单这些不用改支付配置就能跑，但「支付成功」那一段必须有真实商户参数才能走通。

#### 快递 100 物流查询（按需）

`SHOP_KUAIDI100_CUSTOMER` 和 `SHOP_KUAIDI100_KEY` 需要注册快递 100 开放平台账号，在个人中心申请「实时查询」服务后拿到，然后打开 `SHOP_KUAIDI100_ENABLED`。dev 默认启用并内置了测试账号；prod 默认关闭且没有默认值。

`SHOP_LOGISTICS_ALERT_ENABLED` 与 `SHOP_LOGISTICS_ALERT_WEBHOOK_URL` 是物流异常告警的预留开关，默认关闭。

#### 开票平台回调（预留）

`SHOP_INVOICE_CALLBACK_TOKEN` 已经在 `application-prod.yml` 里声明，但当前没有任何代码读取它——第三方开票平台还没对接，回调固定返回 403。发票的申请、审核、置开票中、上传 PDF、门户下载这条链路本身是通的。

### 管理端（shop-web）

关键文件是 `package.json`、`vite.config.ts`、`.env.example`。开发端口 `3000`，接口地址通过 `VITE_API_BASE_URL` 指定，要指向实际后端地址（例如 `http://127.0.0.1:8080`）。

### 用户门户（shop-portal）

关键文件同上。开发端口默认 `3000`，与后端联调时建议用 `--port 3001`。除 `VITE_API_BASE_URL` 外还有三项：

- `VITE_AMAP_KEY` / `VITE_AMAP_SECURITY_CODE`：地址搜索与地图选点必需。在高德开放平台控制台创建应用后添加 Key，「服务平台」选 Web端(JS API)，同时会生成配套的安全密钥，两个值都要填。不填只是「新增收货地址」里的搜索和选点不可用，其余流程不受影响。
- `VITE_DEMO_MODE`：仅用于本地演示，生产环境必须保持 `false` 或不配置

两个 `shop-web/.env.example` 和 `shop-portal/.env.example` 顶部还留着脚手架自带的 `GEMINI_API_KEY`、`APP_URL` 两行，本项目没有用到，复制成 `.env.local` 时可以删掉。

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

**导入演示数据后页面还是没有图**：先确认 `import-demo.mjs` 跑完时没有失败项，再到 MinIO 控制台看 `shop` 桶里有没有 `media/` 前缀的对象。库里存的是对象键（形如 `media/2026-09-01/xxx.jpg`），不是能直接打开的图片地址——后端读取时才用当前配置的 MinIO endpoint 和密钥签发访问地址。所以换了 endpoint、桶名或密钥，图片会整体 403，把后端与导入脚本的 `MINIO_*` 环境变量对齐即可。

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
