# Flash Sale Platform Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在两周内完成一个可运行、可压测、可写进 Java 后端实习简历的高并发秒杀平台。

**Architecture:** 采用 `Controller → Service → Repository → MySQL` 分层；商品热点数据和秒杀库存放入 Redis；秒杀请求先由 Redis Lua 原子校验库存和重复购买，再通过 RabbitMQ 异步创建订单，最终订单和支付状态持久化到 MySQL。

**Tech Stack:** Java 21、Spring Boot 3.2、Spring JDBC、MySQL 8、Redis 7、Redis Lua、RabbitMQ、JWT/JJWT、BCrypt、Vue 3、JMeter、Docker Compose。

---

## 一、当前完成情况

### 已完成

- Spring Boot 项目启动和健康检查；
- Docker Compose MySQL，数据库 `flash_sale`；
- `app_user`、`app_product` 等基础表和演示数据；
- 用户查询、注册、BCrypt 密码校验；
- 登录接口和 JWT 生成；
- JWT 拦截器和当前用户接口 `/api/users/me`；
- 商品列表接口 `/api/products`；
- 商品列表 Redis Cache-Aside 缓存；
- Redis 连接配置和测试；
- 从 MySQL 读取秒杀库存并预热到 Redis；
- 基础单元测试和数据库、Redis 连接测试；
- GitHub 远程仓库连接。

### 后续已完成的开发阶段（截至阶段 10C）

- 8A：商品详情查询、秒杀活动时间判断；
- 8B：管理员身份拦截、商品新增、完整修改和快速上下架；
- 9：商品详情缓存、主动失效、空值缓存、随机 TTL 和查询连接异常降级；
- 商品列表改用摘要字段，减少缓存体积；
- 10A / 10B：Lua 原子预扣库存、重复购买标记；
- 10C：JWT 保护的秒杀接口、活动时间校验、SETNX 按需预热。

### 当前进行中及待完成

- 11A：RabbitMQ 配置和独立教学队列收发验证；
- 11B 及以后：订单表、真实异步下单、幂等、失败重试和补偿；
- 订单、支付、全局 JWT 收口、统计、前端和压测。

当前 Redis 预扣成功仅代表获得资格，还未创建订单。购买标记暂未设置 TTL，
活动库存重建、后台修改库存与已有预扣的协调，需要在订单和补偿阶段继续完善。

## 二、开发原则

每个小功能都遵循：

1. 先写一个能表达业务行为的测试；
2. 在 IDEA 中确认测试因功能缺失而失败；
3. 编写最少生产代码使测试通过；
4. 再解释代码、参数和请求流程；
5. 用户确认理解后进入下一小功能；
6. 每个阶段通过后由用户自行提交、推送 Git，助手不执行提交或推送。

## 三、分阶段执行计划

### 阶段 8A：完善商品查询和活动规则

**目标：** 让商品模块具备真实秒杀场景需要的查询和活动时间判断。

**文件：**

- 修改 `src/main/java/com/example/flashsale/product/ProductRepository.java`：增加 `findOnSaleById(Long)`；
- 修改 `src/main/java/com/example/flashsale/product/ProductService.java`：增加商品详情查询和活动时间判断；
- 修改 `src/main/java/com/example/flashsale/product/ProductController.java`：增加 `GET /api/products/{id}`；
- 测试 `src/test/java/com/example/flashsale/product/ProductServiceTest.java` 和 `ProductControllerTest.java`。

**验证：** 商品存在返回 200；不存在返回 404；活动未开始或已结束时返回明确业务结果。

### 阶段 8B：管理员身份和商品管理

**目标：** 先建立管理员身份拦截，再增加新增、修改全部信息和上下架能力，为后续库存预热和缓存失效提供安全的管理入口。

**8B-0：管理员身份拦截（必须先完成）。**

- `/api/admin/**` 请求必须先通过 JWT 拦截器；
- 没有 `Authorization: Bearer <token>` 或 Token 无效时返回 `401 Unauthorized`；
- JWT 有效但当前用户不是管理员时返回 `403 Forbidden`；
- 当前教学阶段使用配置化管理员 ID，例如 `app.admin-user-id: 1`，避免现在立刻修改用户表结构；
- `AdminAuthorizationInterceptor` 只负责判断身份，不处理商品业务；
- 在 `WebConfig` 中只拦截 `/api/admin/**`，登录、注册、普通商品查询不受管理员拦截影响。

**管理员请求链路：**

```text
HTTP 请求
    ↓
JWTAuthenticationInterceptor：Token 是否有效？
    ↓
AdminAuthorizationInterceptor：用户是否为管理员？
    ↓
AdminProductController
```

**8B-1：管理员新增商品。**

**文件：**

- 创建 `AdminProductController.java`、`CreateProductRequest.java`；
- 修改 `ProductService.java` 和 `ProductRepository.java`：新增商品；
- 创建 `AdminAuthorizationInterceptorTest.java` 和 `AdminProductControllerTest.java`；
- 在测试中验证未登录返回 401、普通用户返回 403、管理员可以访问。

**接口：**

```text
POST /api/admin/products
```

**8B-2：管理员修改商品全部信息。**

管理员可以修改：

- 商品名称和描述；
- 原价和秒杀价；
- 普通库存和秒杀库存；
- 上下架状态；
- 秒杀开始和结束时间。

**接口：**

```text
PUT /api/admin/products/{id}
```

**8B-3：管理员快速上下架。**

保留一个只修改状态的快捷接口，供管理后台列表页按钮使用：

```text
PUT /api/admin/products/{id}/status
```

新增、修改或上下架成功后，统一删除商品详情缓存和商品列表缓存；如果秒杀库存发生变化，再同步 Redis 库存。

### 阶段 9：完善 Redis 缓存、失效和缓存异常保护

**已完成：** Redis 连接、商品列表缓存、TTL、缓存脏数据删除、秒杀库存 Key、库存预热。

**补充目标：** 增加商品详情缓存、主动失效、缓存穿透保护和缓存雪崩缓解。

**文件：**

- 修改 `ProductService.java`：详情查询使用 `flash-sale:product:{id}`；
- 修改管理员商品更新逻辑：新增或上下架后删除对应缓存；
- 创建 `ProductCacheIntegrationTest.java`，验证写入、读取和失效。

**9A：缓存失效。**

- 被动失效：为缓存设置 TTL，时间到后 Redis 自动删除；
- 主动失效：商品新增、修改、上下架后调用 `delete` 删除旧缓存；
- 测试商品更新后再次查询必须访问 MySQL，而不是继续返回旧缓存。

**9B：缓存穿透。**

缓存穿透是指大量请求查询不存在的商品，Redis 没有数据，每次都打到 MySQL。当前项目先采用适合初学者的“空值缓存”：

- 商品不存在时，在 Redis 写入特殊空值；
- 空值 TTL 设置为 30 秒，避免永久占用；
- 再次请求相同 ID 时直接返回 404，不访问 MySQL；
- 测试同一个不存在 ID 连续查询两次，Repository 只允许被调用一次。

**9C：缓存雪崩。**

缓存雪崩是指大量缓存同时过期或 Redis 整体不可用，导致请求集中访问 MySQL。当前项目采用两种基础措施：

- 在固定 TTL 上增加随机秒数，让不同商品不会同时过期；
- Redis 读取异常时降级查询 MySQL，接口仍可返回数据，同时记录日志。

商品详情缓存 TTL 使用类似下面的规则：

```text
基础 TTL 100 秒 + 随机 0～30 秒
```

测试 Redis 异常时，商品查询应能降级到 MySQL；测试不同商品的 TTL 不应完全相同。

### 阶段 10A：Redis Lua 原子扣库存

**目标：** 先单独理解并实现“库存判断 + 扣减”的原子操作。

**文件：**

- 修改 `src/main/java/com/example/flashsale/seckill/StockRedisService.java`：增加 `decreaseStock(Long)`；
- 创建 `src/main/resources/redis/decrease_stock.lua`；
- 修改 `StockRedisServiceTest.java`：验证传入正确 Key 和参数；
- 创建 `RedisStockLuaIntegrationTest.java`：连接真实 Redis 验证成功、库存不足、Key 不存在。

**Lua 返回值：**

```text
1  成功扣减
0  库存不足
-1 库存 Key 不存在
```

### 阶段 10B：Lua 重复购买校验

**目标：** 把“检查用户是否购买过、扣减库存、记录购买标记”放进同一个 Lua 脚本。

**Key：**

```text
库存：flash-sale:stock:{productId}
购买标记：flash-sale:purchase:{productId}:{userId}
```

**返回值：**

```text
1  秒杀资格校验成功
0  库存不足
-1 库存 Key 不存在
-2 用户已经购买过
```

**测试：** 同一用户第二次请求必须返回 `-2`，且库存不再减少。

### 阶段 10C：秒杀接口

**目标：** 对外提供登录用户的秒杀入口，但只完成 Redis 预扣和排队响应。

**文件：**

- 创建 `SeckillController.java`、`SeckillService.java`、`SeckillResult.java`；
- 创建 `SeckillControllerTest.java`；
- 使用 JWT 拦截器提供的当前用户 ID；
- 活动时间校验放在 Service 层；
- 成功返回排队状态，不直接在请求线程创建订单。

**接口：**

```text
POST /api/seckill/products/{productId}
```

### 阶段 11：RabbitMQ 异步下单

按以下小步推进，每次验证并讲解后再进入下一步：

- [ ] **11A：基础收发。** 添加 AMQP 依赖、RabbitMQ Docker 服务和连接配置；使用独立教学队列、文本生产者和日志消费者。真实集成测试不连接 MySQL/Redis。测试与教学监听器使用 `rabbitmq-demo` profile；本步不改变秒杀请求链路。
- [ ] **11B：订单基础。** 设计订单表、状态、Repository 和业务唯一约束。SQL 编号以现有目录为准，避免覆盖已有种子脚本。
- [ ] **11C：接入业务消息。** Lua 成功后发送秒杀消息，消费者创建订单，提供排队结果查询；明确发送异常和库存补偿策略，避免成功响应但消息未入队。
- [ ] **11D：可靠性。** 发送确认、消费幂等、失败重试、死信队列、库存与购买资格补偿及并发验证。

**目标：** 将 Redis 预扣成功的请求发送到消息队列，由消费者异步创建订单。

**文件：**

- 修改 `docker-compose.yml` 和 `pom.xml`：加入 RabbitMQ；
- 创建 `SeckillMessage.java`、`SeckillMessageProducer.java`、`SeckillMessageConsumer.java`；
- 创建 `OrderRepository.java`、`OrderService.java`；
- 创建订单表 `sql/006_create_order.sql`；
- 测试消息发送、消费、重复消费和失败重试。

**核心链路：**

```text
Lua 预扣成功 → RabbitMQ → 消费者 → MySQL 创建 PENDING_PAYMENT 订单
```

### 阶段 12：订单查询和模拟支付

**目标：** 实现订单生命周期，并保证支付回调幂等。

**状态：**

```text
PENDING_PAYMENT → PAID
PENDING_PAYMENT → CANCELLED
PENDING_PAYMENT → PAYMENT_TIMEOUT
```

**接口：**

```text
GET  /api/orders
GET  /api/orders/{id}
POST /api/orders/{id}/pay
POST /api/orders/{id}/cancel
```

**安全规则：** JWT 用户只能查看和操作自己的订单。

### 阶段 13：全局 JWT 保护和权限收口

**目标：** 在订单、秒杀、管理员接口完成后统一收紧拦截策略。

**公开接口：**

```text
GET  /api/health
POST /api/auth/login
POST /api/users
```

除上述接口外，其余 `/api/**` 接口都必须携带合法 JWT；管理员接口额外检查管理员身份。

**测试：** 无 Token 返回 401；Token 过期返回 401；普通用户访问管理员接口返回 403。

### 阶段 14：数据分析

**目标：** 提供可展示项目成果的统计接口，不建设复杂大数据系统。

**统计项：** 商品总库存、Redis 剩余库存、秒杀请求数、成功订单数、支付订单数、支付转化率、秒杀成功率。

**文件：** `AnalyticsController.java`、`AnalyticsService.java`、`AnalyticsRepository.java`、统计测试。

### 阶段 15：Vue 前端

**目标：** 提供可演示的三个用户页面和一个简易管理页面。

**页面：**

- 商品列表页；
- 商品详情和秒杀页；
- 我的订单页；
- 管理员商品管理和统计页。

前端只调用已经完成并有接口测试的后端接口，不在前端复制库存判断逻辑。

### 阶段 16：压测、文档和简历整理

**目标：** 用数据证明高并发设计有效，并完成 GitHub 项目包装。

**JMeter 场景：** 商品查询、秒杀接口、不同并发数、QPS、P95、错误率、超卖检查、MySQL 压力。

**交付物：**

- `README.md`：架构、启动、接口、测试；
- 架构图和请求时序图；
- Docker Compose 启动说明；
- 压测结果截图和表格；
- GitHub 提交记录；
- 简历项目描述和面试讲解稿。

## 四、两周时间安排

| 时间 | 内容 |
|---|---|
| 第 1 天 | 商品详情、活动时间校验 |
| 第 2 天 | 管理员商品新增、上下架 |
| 第 3-4 天 | 缓存失效、穿透、雪崩保护和测试 |
| 第 5 天 | Lua 单纯扣库存 |
| 第 6 天 | Lua 重复购买校验 |
| 第 7 天 | 秒杀接口 |
| 第 8-9 天 | RabbitMQ 和异步下单 |
| 第 10 天 | 订单查询和状态流转 |
| 第 11 天 | 模拟支付和幂等 |
| 第 12 天 | JWT 全局保护、管理员权限、数据分析 |
| 第 13 天 | Vue 演示页面 |
| 第 14 天 | JMeter 压测、README、简历和面试稿 |

## 五、当前下一步

当前开发阶段是 **11A：RabbitMQ 基础收发**。操作和调试说明见
`docs/11A-rabbitmq-learning.md`。本步验证通过并理解消息流程后，进入
11B：订单表与订单基础代码。真实验证结果以当次执行输出为准，不把编译成功当作收发成功。
