# Henfon Shop

> 一个包含商城管理端、用户门户和模块化后端的电商业务系统。

![JDK](https://img.shields.io/badge/JDK-21-3776AB)
![Spring%20Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-6DB33F)
![React](https://img.shields.io/badge/React-19-61DAFB)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6)
![MySQL](https://img.shields.io/badge/MySQL-8.4-4479A1)
![Redis](https://img.shields.io/badge/Redis-7-DC382D)

## 项目亮点

| 模块化单体 | 双端协同 | 交易闭环 | 可本地编排 |
| --- | --- | --- | --- |
| 后端按业务域拆成 11 个 Maven 模块，保留单体部署的开发效率 | 管理端与用户门户共享同一套业务服务与数据 | 覆盖商品、下单、支付、库存、售后、物流与营销 | Docker Compose 一次拉起应用与 MySQL/Redis/MinIO/RocketMQ |

## 快速导航

| 我想了解 | 建议先看 |
| --- | --- |
| 项目由哪些部分组成 | 项目简介 / 项目结构 |
| 项目长什么样 | 页面截图（31 张实拍） |
| 准备本地环境 | 环境要求 |
| 直接启动项目 | 快速开始 / 本地运行步骤 |
| 看两个前端分别做什么 | 双端一览 / 双端说明 |
| 排查启动问题 | 注意事项 / 常见问题 |

## 目录

- [项目简介](#项目简介)
- [页面截图](#页面截图)
- [双端一览](#双端一览)
- [内置能力](#内置能力)
- [适用场景](#适用场景)
- [项目结构](#项目结构)
- [技术栈](#技术栈)
- [功能模块](#功能模块)
- [系统架构](#系统架构)
- [页面与业务关系](#页面与业务关系)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [推荐阅读顺序](#推荐阅读顺序)
- [本地运行前需要确认的配置](#本地运行前需要确认的配置)
- [双端说明](#双端说明)
- [本地运行步骤](#本地运行步骤)
- [开发环境访问地址](#开发环境访问地址)
- [推荐启动路径](#推荐启动路径)
- [当前仓库特点](#当前仓库特点)
- [开发部署说明](#开发部署说明)
- [生产部署说明](#生产部署说明)
- [安全提示](#安全提示)
- [启动顺序建议](#启动顺序建议)
- [项目文档](#项目文档)
- [注意事项](#注意事项)
- [常见问题](#常见问题)
- [补充说明](#补充说明)

## 项目简介

Henfon Shop 面向通用电商场景，把商品展示、会员认证、购物车、订单履约、支付退款、库存管理和运营分析组织在同一套系统中。

当前仓库包含 3 个部分：

| 模块 | 目录 | 说明 |
| --- | --- | --- |
| 业务后端 | `shop-admin` | Java 21 / Spring Boot 3.4.5 模块化单体，统一提供认证、商品、交易、支付、库存、营销、内容、报表与集成能力 |
| 商城管理端 | `shop-web` | React 19 管理后台，面向商品运营、仓储、客服、财务和系统管理员 |
| 用户门户 | `shop-portal` | React 19 顾客端，提供商品浏览、注册登录、购物车、下单支付、订单物流、售后、收藏与商品对比 |

## 页面截图

下面 **31 张截图**全部取自本机实际运行环境（后端 `8080` + 管理端 `3000` + 用户门户 `3001`），
由浏览器自动化逐个页面真实访问后截取，未做任何后期处理。

页面上的内容都来自服务端接口 —— 商品数 393、订单号、优惠券进度、地图渲染等均为真实数据。
用户门户的演示开关 `VITE_DEMO_MODE` 默认关闭，**没有前端 mock 兜底**（见 [注意事项](#注意事项)）。

### 管理端（shop-web，23 张）

#### 登录页

![管理端登录页](docs/screenshots/admin-login.png)

#### 工作台

实时汇总今日销售额、订单数、用户数、商品总数，并给出销售趋势图、待办事项与最近订单。

![管理端工作台](docs/screenshots/admin-dashboard.png)

#### 电商运营中心

商品与库存管理（SPU/SKU、售价与成本毛利、安全库存预警、批量上下架）、订单履约与售后、
商品类目树、客户与会员资产。

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

门户首屏：类目导航、Banner 轮播、优惠券提示条与商品瀑布流。

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

密码登录 / 短信免密 / 新客注册三合一入口。

![会员登录](docs/screenshots/portal-login.png)

## 双端一览

| 端 | 目录 | 面向对象 | 主要职责 |
| --- | --- | --- | --- |
| 业务后端 | `shop-admin` | 两个前端及外部回调 | 认证鉴权、商品与类目、购物车与订单、库存、营销、支付与退款、内容、报表、外部集成 |
| 管理端 | `shop-web` | 商品运营、仓储、客服、财务、管理员 | 商品与类目、订单处理、库存与供应商、营销活动、内容管理、权限与日志、经营报表 |
| 用户门户 | `shop-portal` | 访客与会员 | 商品浏览、注册登录、购物车、地址、下单支付、订单物流、售后、收藏与商品对比 |

## 内置能力

基于当前仓库代码结构，项目已包含以下能力：

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

## 适用场景

当前仓库更适合以下场景使用：

- 电商业务系统的课程设计 / 毕业设计参考
- 前后端分离 + 管理后台 + 顾客端的综合项目实践
- Spring Boot 模块化单体结构学习
- 交易类业务（下单、支付、库存、售后、物流）的完整链路梳理
- 作为二次开发基础，继续扩展营销、内容或报表能力

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

## 技术栈

### 后端

- JDK 21
- Spring Boot 3.4.5
- MyBatis-Plus 3.5.7
- JWT（jjwt 0.12.6）
- Maven（聚合多模块）

### 管理端与用户门户

- React 19
- TypeScript 5
- Vite 6
- Tailwind CSS 4
- Lucide 图标、Recharts 图表、motion 动效
- 用户门户额外使用 qrcode（支付二维码）与高德地图 JS API（地址选点）

### 数据与中间件

- MySQL 8.4
- Redis 7
- RocketMQ 5.3.1
- MinIO（对象存储）

## 功能模块

### 后端业务模块

后端按业务域拆分，`shop-admin/pom.xml` 中声明的模块如下：

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

### 管理端页面分组

根据 `shop-web/src/components` 目录，当前主要页面分组包括：

| 分组 | 主要内容 |
| --- | --- |
| `dashboard` | 工作台、销售趋势图表 |
| `products` | 商品管理、类目管理 |
| `orders` | 订单管理、发货与批量操作 |
| `inventory` | 仓库库存、供应商管理 |
| `marketing` | 优惠券管理、秒杀管理、轮播图管理 |
| `finance` | 发票管理、资金对账 |
| `content` | 评价管理、轮播图管理 |
| `analytics` | 经营总览、商品分析 |
| `rbac` | 角色、系统用户、菜单、授权、数据权限 |
| `settings` | 系统设置、登录日志、操作日志、审计面板 |
| `users` | 会员用户管理 |
| `auth` | 管理员登录 |
| `common` / `layout` | 侧边栏、顶栏、通知抽屉、分页、权限门、对话框 |

### 用户门户页面能力

根据 `shop-portal/src/components` 目录，当前门户包含：

- 首页轮播与导航（`HeroBanner`、`Navbar`）
- 商品列表与快速查看（`ProductCard`、`ProductQuickView`）
- 购物车抽屉（`CartDrawer`）
- 结算与地址（`CheckoutModal`、`AmapAddressPicker`）
- 支付与结果（`PaymentModal`、`OrderSuccessModal`、`InvoiceModal`）
- 订单与物流（`OrdersModal`、`OrderTracking`）
- 账号与资料（`AuthModal`、`UserProfileModal`）
- 收藏与商品对比（`WishlistModal`、`CompareModal`、`CompareChartsView`、`CompareFloatingBar`）
- 优惠券中心（`CouponCenter`）

## 系统架构

当前系统采用「双前端接入 + 统一业务后端 + 外部能力集成」的实现方式，后端是模块化单体，所有业务能力通过 REST API 对外提供。

图中的分层与当前仓库实现关系如下：

| 架构层 | 在本项目中的落地 |
| --- | --- |
| 接入层 | `shop-portal` 面向顾客，`shop-web` 面向运营与后台角色，两者都只通过 REST API 访问后端 |
| 统一业务服务 | `shop-admin` 作为唯一后端，统一提供认证鉴权、业务编排与接口输出 |
| 核心交易服务 | `shop-catalog`、`shop-trade`、`shop-inventory`、`shop-payment` 构成商品到订单到支付的主链路 |
| 会员营销服务 | `shop-identity` 的会员能力，配合 `shop-marketing`、`shop-content` 完成会员沉淀与营销触达 |
| 经营分析服务 | `shop-reporting` 基于事件投影产出销售、商品与会员分析 |
| 集成支撑能力 | `shop-integration` 封装快递 100、MinIO 与 RocketMQ，`shop-trade` 的 Outbox 保证事件可靠投递 |
| 基础设施层 | MySQL、Redis、RocketMQ、MinIO 提供数据、缓存、消息与对象存储 |

接口约定：管理端接口统一以 `/api/admin` 开头，用户门户接口统一以 `/api/portal` 开头。完整字段、错误码与接口清单见 [API 契约](docs/API契约.md)。

## 页面与业务关系

这一部分把上面的架构落到实际业务链路，说明门户页面、管理端页面与后端模块分别参与哪一段业务。

| 业务阶段 | 门户页面 / 组件 | 管理端页面 | 支撑后端模块 | 相关截图 |
| --- | --- | --- | --- | --- |
| 浏览与引流 | `HeroBanner`、`ProductCard`、`CouponCenter` | 商品管理、轮播图管理、优惠券管理 | `shop-catalog`、`shop-marketing`、`shop-content` | [门户首页](docs/screenshots/portal-home.png) · [商品列表](docs/screenshots/portal-products.png) · [商品管理](docs/screenshots/admin-products.png) |
| 购物车与下单 | `CartDrawer`、`CheckoutModal`、`AmapAddressPicker` | 订单管理 | `shop-trade`、`shop-catalog`、`shop-inventory` | [购物车](docs/screenshots/portal-cart.png) · [结算](docs/screenshots/portal-checkout.png) · [地址选点](docs/screenshots/portal-address-map.png) |
| 支付与开票 | `PaymentModal`、`InvoiceModal` | 发票管理、资金对账 | `shop-payment` | [发票与税务](docs/screenshots/admin-finance-invoices.png) · [资金对账](docs/screenshots/admin-finance-transactions.png) |
| 履约与物流 | `OrdersModal`、`OrderTracking` | 订单管理（发货、批量发货）、系统设置（物流字典） | `shop-trade`、`shop-integration` | [订单履约](docs/screenshots/admin-orders.png) · [系统设置](docs/screenshots/admin-settings.png) |
| 会员与营销 | `AuthModal`、`UserProfileModal`、`WishlistModal`、`CompareModal` | 会员用户管理、优惠券管理、秒杀管理 | `shop-identity`、`shop-marketing` | [会员登录](docs/screenshots/portal-login.png) · [领券中心](docs/screenshots/portal-coupon-center.png) · [优惠券中心](docs/screenshots/admin-coupons.png) · [秒杀管理](docs/screenshots/admin-flash-sales.png) |
| 售后与评价 | `OrdersModal`（售后申请） | 订单管理、评价管理 | `shop-trade`、`shop-content` | [客户评价](docs/screenshots/admin-content-reviews.png) |
| 经营复盘 | — | 工作台、经营分析、资金对账 | `shop-reporting`、`shop-payment` | [工作台](docs/screenshots/admin-dashboard.png) · [经营大屏](docs/screenshots/admin-analytics-overview.png) · [商品动销榜](docs/screenshots/admin-analytics-products.png) |

> 完整截图清单见 [页面截图](#页面截图)（管理端 23 张 + 用户门户 8 张）。

## 环境要求

| 环境 | 版本或建议 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| JDK | 21 | 必须 | 后端编译与运行 |
| Maven | 3.8+ | 必须 | 后端构建与测试 |
| Node.js | 18+，建议当前 LTS | 必须 | 两个前端的开发与构建 |
| MySQL | 8.x | 必须 | 业务数据存储 |
| Redis | 7.x | 必须 | 令牌、缓存与限流 |
| Docker Desktop | 最新稳定版 | 推荐 | 用于本地一键准备后端与依赖服务 |
| MinIO | 按需 | 可选 | 商品媒体、评价凭证等对象存储 |
| RocketMQ | 5.x | 建议 | Outbox、通知与报表异步事件 |

## 快速开始

推荐路径：先用 Docker Compose 跑通「后端 + 基础设施」，再分别启动两个前端。确认基础链路可用后，再处理支付、物流等依赖外部环境的链路。

### 1. 准备基础环境

- 安装 JDK 21、Maven 3.8+
- 安装 Node.js 18+（建议 LTS）
- 安装 Docker Desktop（推荐）或本地准备 MySQL 8、Redis 7

### 2. 初始化数据库

MySQL 首次启动会按 `shop-admin/db/init` 中的编号顺序执行初始化脚本。脚本属于版本基线，**不要直接修改已有脚本**。

手动导入时执行该目录下的 SQL 文件，并确认后端配置的数据库名与实际一致（默认 `henfon-shop`）。

### 3. 启动基础设施与后端（Docker 方式）

在仓库根目录执行：

```powershell
Copy-Item .env.example .env
# 编辑 .env，替换其中所有 change-me 配置
docker compose up -d --build
```

### 4. 启动管理端

```powershell
cd shop-web
npm install
Copy-Item .env.example .env.local
npm run dev
```

### 5. 启动用户门户

两个前端默认都用 Vite 的 `3000` 端口，因此第二个进程必须显式指定端口：

```powershell
cd shop-portal
npm install
Copy-Item .env.example .env.local
npm run dev -- --port 3001
```

### 6. 验证访问

| 服务 | 地址 |
| --- | --- |
| 后端 API | `http://localhost:8080` |
| 管理端 | `http://localhost:3000` |
| 用户门户 | `http://localhost:3001` |

### 7. 默认账号

开发初始化数据提供管理员账号 `admin / 123456`，仅限本地联调，首次登录后应立即修改密码。

## 推荐阅读顺序

第一次接手这个仓库，建议按下面顺序阅读和启动：

1. 先看「项目结构」，确认三个部分分别在哪
2. 再看「环境要求」，把 JDK、Node、MySQL、Redis 准备好
3. 然后看「本地运行前需要确认的配置」，先把数据库、Redis、端口对齐
4. 接着看「本地运行步骤」，先起后端，再起管理端，最后起用户门户
5. 最后再处理微信支付、物流查询、邮件通知等依赖外部环境的链路

## 本地运行前需要确认的配置

### 1. 数据库

- 数据库名：`henfon-shop`
- 初始化脚本位置：`shop-admin/db/init`
- 迁移校验脚本：`shop-admin/db/migration/verify-migrations.ps1`

脚本按编号递增，只增不改。提交前建议执行：

```powershell
powershell -ExecutionPolicy Bypass -File shop-admin/db/migration/verify-migrations.ps1
```

### 2. 后端配置

后端配置文件位于：

```text
shop-admin/shop-boot/src/main/resources/application.yml
shop-admin/shop-boot/src/main/resources/application-dev.yml
shop-admin/shop-boot/src/main/resources/application-test.yml
shop-admin/shop-boot/src/main/resources/application-prod.yml
```

未显式指定 `SPRING_PROFILES_ACTIVE` 时默认使用 `dev`。

#### 服务配置

- 端口：`8080`（`SHOP_SERVER_PORT`）
- 上下文路径：`/`（`SHOP_CONTEXT_PATH`）
- 上传限制：单文件 20MB、单请求 50MB

#### MySQL

开发环境默认值：

| 项 | 值 |
| --- | --- |
| host | `127.0.0.1` |
| port | `3306` |
| database | `henfon-shop` |
| username | `root` |
| password | `123456` |

对应环境变量：`SHOP_MYSQL_URL`、`SHOP_MYSQL_USERNAME`、`SHOP_MYSQL_PASSWORD`。

#### Redis

开发环境默认值：`127.0.0.1:6379`，密码 `123456`，逻辑库 `0`。如果你的本地 Redis 没有密码，需要自行调整配置。

对应环境变量：`SHOP_REDIS_HOST`、`SHOP_REDIS_PORT`、`SHOP_REDIS_PASSWORD`、`SHOP_REDIS_DATABASE`。

#### RocketMQ

默认 NameServer：`127.0.0.1:9876`。

**注意**：开发环境消费监听器默认关闭（`SHOP_ROCKETMQ_CONSUMER_ENABLED=false`），这是为了避免本地没有 MQ 时反复报错。需要联调订单 Outbox、通知或报表事件时，先启动 RocketMQ 再把该变量改为 `true`。

#### MinIO

开发环境默认值：

| 项 | 值 |
| --- | --- |
| endpoint | `http://127.0.0.1:9000` |
| access-key | `minioadmin` |
| secret-key | `minioadmin` |
| bucket | `shop` |

暂不使用文件上传能力时，可以不开 MinIO。

#### JWT

开发环境有默认密钥与过期时间（8 小时，`SHOP_JWT_EXPIRATION_SECONDS=28800`）。生产环境必须替换 `SHOP_JWT_SECRET`。

#### 邮件与通知

开发环境通过 SMTP（163 邮箱）发送找回密码邮件与业务通知，站内通知与邮件开关由 `SHOP_EMAIL_NOTIFICATION_ENABLED` 控制。SMTP 账号与授权码建议全部通过环境变量注入。

#### 微信支付

开发环境已配置微信支付 V3，默认按 `NATIVE`（网页二维码）场景启用，参数包括 AppID、商户号、商户证书序列号、API v3 密钥、商户私钥路径、平台公钥 ID 与公钥路径。

两个回调地址必须是**微信服务器可访问的公网 HTTPS 地址**，且要与商户平台配置一致：

- 支付通知：`/api/wx/pay/notify/v3`
- 退款通知：`/api/wx/pay/refund/notify/v3`

如果暂时不想调用微信接口，把 `SHOP_WECHAT_PAY_ENABLED` 改为 `false` 即可。

#### 物流查询

开发环境已接入快递 100 查询接口（`SHOP_KUAIDI100_*`）。不联动物流链路时可以关闭。

### 3. 管理端配置

管理端目录：`shop-web`，关键文件为 `package.json`、`vite.config.ts`、`.env.example`。

- 开发端口：`3000`
- 接口地址通过 `VITE_API_BASE_URL` 指定，需指向实际后端地址（例如 `http://127.0.0.1:8080`）

### 4. 用户门户配置

用户门户目录：`shop-portal`，关键文件同为 `package.json`、`vite.config.ts`、`.env.example`。

- 开发端口：默认 `3000`，与后端联调时建议用 `--port 3001`
- `VITE_API_BASE_URL`：后端地址
- `VITE_AMAP_KEY` / `VITE_AMAP_SECURITY_CODE`：地址搜索与地图选点必需
- `VITE_DEMO_MODE`：仅适用于本地演示，生产环境必须保持 `false` 或不配置

修改 `.env.local` 后需要重启 Vite 开发服务才会生效。

## 双端说明

### 1. 业务后端 shop-admin

后端是系统的核心服务，负责认证与权限、商品与类目、购物车与订单、库存、营销、支付与退款、内容、报表和外部集成。

默认启动信息：

- 端口：`8080`
- 上下文路径：`/`
- 管理端接口前缀：`/api/admin`
- 门户接口前缀：`/api/portal`
- 健康与指标端点：`/actuator/health`、`/actuator/info`、`/actuator/metrics`

适合先启动它的原因：所有前端都依赖后端接口，数据库、Redis、MQ、对象存储的配置问题都会先在这里暴露。

### 2. 管理端 shop-web

管理端面向运营与后台角色，当前覆盖工作台、商品、类目、订单、库存、供应商、营销、内容、财务、权限、日志与经营分析。

默认启动信息：

- 本地端口：`3000`
- 访问地址：`http://localhost:3000`

核心价值：提供后台运营入口，承接商品、订单、库存、营销和报表等管理功能。

适合优先联调的原因：比门户更容易调试，不依赖第三方支付与地图环境。

### 3. 用户门户 shop-portal

用户门户面向顾客，覆盖商品浏览、注册登录、购物车、地址、下单支付、订单物流、售后、收藏与商品对比。

默认启动信息：

- 本地端口：`3000`，与其他前端或后端同时运行时建议 `3001`
- 访问地址：`http://localhost:3001`

核心价值：面向顾客完成从浏览到下单到售后的完整链路。

联调顺序建议：先确认后端接口可用，再确认管理端基础功能可用，最后处理支付、地图选点等依赖外部服务的功能。

## 本地运行步骤

### 方式一：Docker 启动后端与基础设施

```powershell
Copy-Item .env.example .env
# 编辑 .env，替换所有 change-me
docker compose up -d --build
```

### 方式二：本地启动后端

本地准备 MySQL、Redis（按需准备 MinIO、RocketMQ），开发配置默认使用 `dev` Profile：

```powershell
cd shop-admin
mvn -DskipTests compile
mvn -DskipTests package
java -jar shop-boot/target/shop-boot-0.0.1-SNAPSHOT.jar
```

可通过 `SPRING_PROFILES_ACTIVE` 或 `--spring.profiles.active` 在 `dev`、`test`、`prod` 之间切换。

### 启动两个前端

```powershell
# 管理端
cd shop-web
npm install
Copy-Item .env.example .env.local
npm run dev

# 新开终端启动用户门户
cd shop-portal
npm install
Copy-Item .env.example .env.local
npm run dev -- --port 3001
```

## 开发环境访问地址

| 模块 | 地址 |
| --- | --- |
| 后端 API 根地址 | `http://localhost:8080` |
| 管理端 | `http://localhost:3000` |
| 用户门户 | `http://localhost:3001` |
| MinIO API / 控制台 | `http://localhost:9000` / `http://localhost:9001` |
| RocketMQ NameServer | `localhost:9876` |

说明：业务接口需要鉴权，未登录直接访问会返回统一 JSON 错误体（例如访问 `/api/health` 返回 `AUTH_REQUIRED`），这属于预期行为。

## 推荐启动路径

### 方式一：先熟悉项目

1. 初始化数据库
2. 启动后端
3. 启动管理端，用 `admin / 123456` 登录后台
4. 在后台浏览商品、订单、库存等页面
5. 再启动用户门户，走一遍浏览与下单流程

### 方式二：完整双端联调

1. 准备 MySQL、Redis（需要异步链路时再加 RocketMQ、MinIO）
2. 启动后端并确认接口可访问
3. 启动管理端（`3000`）与用户门户（`3001`）
4. 按需配置高德地图 Key、微信支付回调等外部参数
5. 完成下单、支付、发货、确认收货、售后的全链路验证

## 当前仓库特点

从现有代码结构来看，这个仓库有几个比较明显的特点：

- 不是单页 demo，而是明确拆分成「后端 + 管理端 + 用户门户」三部分
- 后端按业务域完整拆成 11 个 Maven 模块，而不是把代码堆在一个包里
- 交易链路覆盖比较完整，包含订单审核、超时关单、库存预占、售后与物流
- 数据库采用编号脚本逐版演进，并带有迁移校验机制
- 管理端坚持「服务端是唯一事实来源」，接口失败时不使用本地演示数据兜底
- 对中间件有一定依赖，适合做接近真实业务链路的本地联调

## 开发部署说明

开发环境建议按「基础设施 -> 后端 -> 管理端 -> 用户门户」的顺序启动：

1. 先完成 MySQL、Redis 配置
2. 启动后端，确认接口可访问
3. 启动管理端，确认后台可登录
4. 启动用户门户，确认商品与下单链路可用
5. 最后再联调支付、物流、地图等外部能力

验证与测试：

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

后端依赖外部服务的集成测试默认跳过，已准备可用依赖服务时可设置 `SHOP_IT_ENABLED=true` 后再执行。两个前端均可使用 `npm run build` 生成生产构建产物。

## 生产部署说明

当前仓库中已提供 `application-prod.yml`、`application-test.yml` 与 Docker 相关文件，`docker-compose.yml` 以 `prod` Profile 启动后端。

但仓库中没有提供完整的生产部署文档、脚本或演示环境地址，因此本 README 仅说明已有的生产配置入口，不扩展未落地的信息。如果后续需要补充，建议单独增加：

- Nginx 反向代理与前端静态资源部署说明
- JAR 部署与进程守护方式
- MySQL / Redis / RocketMQ / MinIO 生产参数与高可用说明
- 微信支付正式环境参数与回调域名配置说明
- 日志、监控、备份与灾备策略（可参考 [灾备与故障演练](docs/灾备与故障演练.md)）

## 安全提示

⚠️ 当前 `application-dev.yml` 中直接写入了可用于真实联调的敏感值（邮件授权码、微信支付 API v3 密钥与商户信息、物流查询密钥、JWT 默认密钥等）。这些属于**开发环境凭证**，一旦仓库外传即可能被滥用。

建议按以下方式处理：

1. 把开发配置中的敏感值全部改为仅从环境变量读取，不保留可用默认值
2. 对已进入版本库的凭证做一次轮换（邮箱授权码、微信支付密钥、物流平台密钥、JWT 密钥）
3. 生产环境必须由部署平台注入真实配置，禁止使用示例值或开发值
4. `.env` 与 `.env.local` 不应提交到仓库

## 启动顺序建议

```text
MySQL -> Redis -> RocketMQ（按需）-> MinIO（按需）-> shop-admin -> shop-web -> shop-portal
```

## 项目文档

- [API 契约](docs/API契约.md)
- [部署指南](docs/部署指南.md)
- [开发计划](docs/开发计划.md)
- [管理端真实数据改造方案](docs/管理端真实数据改造方案.md)
- [UAT 上线检查清单](docs/UAT上线检查清单.md)
- [灾备与故障演练](docs/灾备与故障演练.md)
- [文档目录](docs/文档目录.md)
- [数据库迁移说明](shop-admin/db/migration/README.md)

## 注意事项

### 1. 数据库脚本只增不改

`shop-admin/db/init` 下的脚本是版本基线，修改已有脚本会导致历史环境与新环境不一致。结构变更请新增编号脚本，并执行迁移校验。

### 2. 两个前端默认端口冲突

管理端与用户门户默认都用 `3000`，同时启动时第二个进程必须显式指定端口，例如 `npm run dev -- --port 3001`。

### 3. 微信支付回调必须是公网 HTTPS

本地开发需要通过内网穿透提供服务地址，并保证与微信商户平台配置的地址一致，否则收不到支付与退款通知。

### 4. RocketMQ 消费者默认关闭

开发环境 `SHOP_ROCKETMQ_CONSUMER_ENABLED=false`，需要异步链路时才打开，避免本地没有 MQ 时不断重试报错。

### 5. 管理端不允许 mock 兜底

管理端的业务数据以服务端接口和数据库为唯一事实来源，接口失败或返回空时应展示错误或空状态，不允许回退到演示数据。

### 6. 修改环境变量后要重启前端

Vite 只在开发服务启动时读取 `.env.local`，改完必须重启才能生效。

## 常见问题

### 1. 后端无法连接数据库或 Redis

优先检查：

- MySQL、Redis 是否已启动
- 当前 Profile 使用的数据库名、端口、用户名密码是否与实际一致
- 使用 Docker Compose 时，`.env` 中所有 `change-me` 是否已替换

### 2. 前端请求接口失败

优先检查：

- 后端是否已启动
- 各前端 `.env.local` 中的 `VITE_API_BASE_URL` 是否指向正确地址
- 修改环境变量后是否重启了 Vite
- 本地是否存在端口占用

### 3. RocketMQ 或 MinIO 连接失败

只联调商品与订单基础流程时，可以先关闭不需要的异步消费者或文件功能。需要完整事件、文件上传或物流链路时，应启动对应服务，并使用后端可访问的地址。

### 4. 商品图片或评价凭证上传失败

优先检查 MinIO 是否启动、endpoint 是否能被后端访问、bucket 是否存在。

### 5. 支付相关功能跑不通

微信支付依赖真实商户环境，本地至少需要：可公网访问的 HTTPS 回调地址、有效的商户证书与私钥、与商户平台一致的参数配置。只做本地基础开发时，可先关闭支付能力。

### 6. 两个前端启动后端口冲突

分别使用不同端口，例如管理端保持 `3000`，用户门户使用 `3001`。

## 补充说明

当前 README 主要用于：

- 帮助首次接手项目的人快速了解仓库结构
- 说明三个部分分别需要什么环境
- 说明本地如何把项目跑起来

如果后续继续补充，建议下一步再单独完善这几块：

- 数据库表结构与核心业务表说明
- 后端接口分组与错误码说明（可基于 [API 契约](docs/API契约.md) 扩展）
- 管理端菜单与权限映射说明
- 双端联调的分场景操作手册
- 生产环境 Nginx 与容器编排方案

## 许可证

本项目的许可证与使用范围以仓库维护者的正式声明为准。
