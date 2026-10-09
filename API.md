# Flash Sale Platform HTTP 接口文档

核对日期：2026-10-09。本文以当前项目 Controller、请求/响应类及 Service 为准，记录已经实现的接口。
前端建设期间每次修改接口，应同时更新本文。前端开发计划中的待补接口不算已实现。

## 1. 通用约定

- 本地后端地址：`http://localhost:8080`。
- JSON 请求设置 `Content-Type: application/json`；GET 和无请求体 POST 不必发送 `{}`。
- 除健康检查、登录、注册外，其余接口都需要请求头 `Authorization: Bearer <token>`。
- `/api/admin/**` 额外要求管理员身份，当前后端配置 `app.admin-user-id=1`。
- 用户 ID 从 JWT 获取，前端不能通过请求体中的 userId 代替登录身份。
- URL 中 `{id}` 是文档占位符，实际请求必须替换成数字，不携带大括号。例如 `/api/products/14`。
- 当前没有统一的 `{code,data,message}` 响应包装。成功响应可能是对象、数组或文本。
- 当前列表没有分页，也没有实现 keyword、page、size 等查询参数。
- 金额序列化为 JSON number；时间字段通常是无时区的 ISO 本地时间，例如 `2026-10-10T10:00:00`。
  本地教学环境按 Asia/Shanghai 理解；统计生成时间和采集时间是带 Z 的 UTC 时间，展示时需转换。
- ID 当前为 JSON number。演示数据范围内可用 JS Number；未来 ID 超过 JS 安全整数范围时需先调整契约。

只有以下三个入口公开，精确匹配 HTTP 方法和路径：

```text
GET  /api/health
POST /api/auth/login
POST /api/users
```

## 2. 已实现接口总表（19 个）

| 方法 | 路径 | 权限 | 成功响应 |
|---|---|---|---|
| GET | `/api/health` | 公开 | 200，文本 OK |
| POST | `/api/auth/login` | 公开 | 200，LoginResponse |
| POST | `/api/users` | 公开 | 201，UserResponse |
| GET | `/api/users/me` | 登录 | 200，UserResponse |
| GET | `/api/users/{id}` | 登录且本人 | 200，UserResponse |
| GET | `/api/products` | 登录 | 200，ProductSummary[] |
| GET | `/api/products/{id}` | 登录 | 200，Product |
| POST | `/api/admin/products` | 管理员 | 200，Product |
| PUT | `/api/admin/products/{id}` | 管理员 | 200，Product |
| PUT | `/api/admin/products/{id}/status` | 管理员 | 200，Product |
| POST | `/api/seckill/products/{productId}` | 登录 | 202，SeckillResponse |
| GET | `/api/seckill/requests/{requestId}` | 登录且本人 | 200，SeckillOrder；或 202，SeckillResponse |
| GET | `/api/orders` | 登录 | 200，SeckillOrder[] |
| GET | `/api/orders/{id}` | 登录且本人 | 200，SeckillOrder |
| POST | `/api/orders/{id}/pay` | 登录且本人 | 200，SeckillOrder |
| POST | `/api/orders/{id}/cancel` | 登录且本人 | 200，SeckillOrder |
| GET | `/api/admin/analytics/overview` | 管理员 | 200，Overview |
| GET | `/api/admin/analytics/products` | 管理员 | 200，ProductStatistics[] |
| GET | `/api/admin/analytics/products/{id}` | 管理员 | 200，ProductStatistics |

以下示例均为结构示例，不保证示例商品和订单实际存在，也不保证示例排期当前有效。

## 3. 健康检查、注册与登录

### GET /api/health

无参数、无请求体，200 返回纯文本 `OK`。它只验证 Web 服务入口，不代表 MySQL、Redis、RabbitMQ 全部健康。

### POST /api/users

请求体：

```json
{
  "username": "learner01",
  "password": "my-password",
  "nickname": "学习用户"
}
```

| 字段 | 类型 | 提交要求 |
|---|---|---|
| username | string | 提供登录用户名，不与已有用户名重复 |
| password | string | 提供明文密码，由后端 BCrypt 加密 |
| nickname | string | 提供用户昵称 |

成功为 201 + UserResponse；用户名重复时返回 409。
当前尚未建立完整的参数校验与统一 400 错误处理，不能把“建议填写”误认为后端已实现全部校验。

UserResponse：

```json
{
  "id": 2,
  "username": "learner01",
  "nickname": "学习用户",
  "status": 1,
  "isAdmin": false
}
```

status=1 正常，0 禁用。不会返回 passwordHash。15B 新增 isAdmin（boolean），由服务端根据 app.admin-user-id 计算，管理员为 true，普通用户为 false；实际权限仍由后端拦截器验证。
注册成功不自动登录，需要调用登录接口取得 Token。

### POST /api/auth/login

路径是 `/api/auth/login`，不是 `/api/login`。

```json
{
  "username": "learner01",
  "password": "my-password"
}
```

成功 200，LoginResponse：

```json
{
  "token": "<JWT字符串>",
  "user": {
    "id": 2,
    "username": "learner01",
    "nickname": "学习用户",
    "status": 1,
    "isAdmin": false
  }
}
```

用户名/密码错误或账号禁用返回 401。当前 Token 有效期配置为 7200 秒，没有刷新 Token 接口。
密码必须使用自己已验证过的账号密码，不根据示例 BCrypt 密文猜测明文。

### GET /api/users/me

无请求体，成功 200 + UserResponse；Token 有效但数据库查不到用户返回 404。
前端刷新页面后通过它恢复当前用户资料。

### GET /api/users/{id}

无请求体。只能查询 JWT 所属用户本人；不存在或他人的用户返回 404。
前端通常直接用 `/api/users/me`，不需要维护当前用户 ID 来查询资料。

## 4. 用户商品查询

### GET /api/products

无请求体，返回所有上架商品的摘要数组，空列表为 `[]`。

```json
[
  {
    "id": 14,
    "name": "无线耳机",
    "price": 599.00,
    "seckillPrice": 299.00,
    "seckillStartAt": "2026-10-10T10:00:00",
    "seckillEndAt": "2026-10-10T12:00:00"
  }
]
```

ProductSummary 不包含 description、stock、seckillStock、status。
上架不等于活动正在进行，页面可显示活动时间，但最终能否秒杀由后端判断。

### GET /api/products/{id}

无请求体，成功 200 + Product。商品不存在或已下架返回 404、空响应体。

Product：

```json
{
  "id": 14,
  "name": "无线耳机",
  "description": "商品介绍",
  "price": 599.00,
  "seckillPrice": 299.00,
  "stock": 100,
  "seckillStock": 50,
  "status": 1,
  "seckillStartAt": "2026-10-10T10:00:00",
  "seckillEndAt": "2026-10-10T12:00:00"
}
```

price、seckillPrice 为 number；stock、seckillStock、status 为 integer。
普通库存和秒杀库存是独立库存。商品详情经过缓存，seckillStock 不能作为 Redis 实时可抢库存承诺。
当前也没有普通用户查询 Redis 实时库存的专用接口。

## 5. 管理员商品写接口

### POST /api/admin/products

请求体是 CreateProductRequest，提交商品全部业务字段，不提交 id：

```json
{
  "name": "无线耳机",
  "description": "商品介绍",
  "price": 599.00,
  "seckillPrice": 299.00,
  "stock": 100,
  "seckillStock": 50,
  "status": 1,
  "seckillStartAt": "2026-10-10T10:00:00",
  "seckillEndAt": "2026-10-10T12:00:00"
}
```

成功返回 **200** + 创建后的 Product，包含数据库生成的 id；这里不是 201。
status=1 上架、0 下架。创建接口尚无完整 Bean Validation，前端需要基本表单校验，后端校验补强另行安排。

### PUT /api/admin/products/{id}

请求体为 UpdateProductRequest，字段与上面的创建请求相同，要求提交全部可编辑字段，**不是部分更新**。
成功 200 + 更新后的 Product；不存在返回 404、空响应体。

活动/库存规则冲突返回 409：

```json
{
  "code": "STOCK_CHANGE_NOT_ALLOWED",
  "message": "活动已经开始或结束，不能修改秒杀库存或重新排期"
}
```

现有规则：

- 秒杀库存非负，秒杀结束时间晚于开始时间。
- 修改秒杀库存或排期时，旧活动必须尚未开始，新开始时间必须在未来。
- 修改前检查 Redis 库存与购买标记，无法确认安全时拒绝修改。
- 已开始或已结束的活动不能通过重新排期绕过库存限制。
- 价格、名称等其他字段可修改；订单金额保留下单时快照，不随商品改价变化。

### PUT /api/admin/products/{id}/status

只更新上下架状态：

```json
{
  "status": 0
}
```

status 只应提交 0 或 1。成功 200 + Product；不存在返回 404。
非法状态当前抛 IllegalArgumentException，尚未统一映射为 400，前端不能假设其错误结构。

**现阶段缺口：** 没有 `GET /api/admin/products` 或 `GET /api/admin/products/{id}`。
用户商品接口只查上架商品，不能用于完整管理下架商品；统计列表虽能列出下架商品，
但不含价格、描述、排期等全部编辑字段。前端管理页之前需要补管理员只读接口，详见 FRONTEND_PLAN.md。

## 6. 秒杀与异步结果查询

### POST /api/seckill/products/{productId}

无请求体。用户身份来自 JWT，价格来自后端商品信息；不提交 userId、amount 或库存。
成功受理返回 202 + SeckillResponse：

```json
{
  "result": "SUCCESS",
  "message": "请求已提交，订单正在异步创建",
  "requestId": "88c54202-4bf4-4c61-9641-f5949b5712eb"
}
```

202 代表异步受理，不等于已创建订单或已支付。此时应保存 requestId 并查询结果，不能自动再 POST 秒杀。

| HTTP 状态 | result | 前端动作 |
|---|---|---|
| 202 | SUCCESS | 查询 requestId 对应订单 |
| 404 | 无响应体 | 提示商品不存在或已下架 |
| 400 | SECKILL_NOT_ACTIVE | 提示不在活动时间 |
| 409 | DUPLICATE_PURCHASE | 提示重复购买，可跳转我的订单 |
| 409 | OUT_OF_STOCK | 提示售罄 |
| 503 | STOCK_NOT_WARMED_UP | 提示库存未准备完成，不自动重复提交 |
| 503 | PUBLISH_FAILED | 预扣保留，提示需核对，不能假定没有扣库存 |
| 503 | PUBLISH_UNKNOWN | 保存 requestId，查询订单，不能假定下单失败 |

除 404 外，以上业务结果返回 SeckillResponse；未生成 requestId 的结果该字段为 null。
基础设施未被业务处理捕获的异常仍可能返回 500，应显示服务异常而不是假造售罄。
浏览器超时或网络断开也不证明请求没有执行；不要对秒杀 POST 设置自动重试。

### GET /api/seckill/requests/{requestId}

无请求体，只查询当前用户拥有的订单：

- 查到订单：200 + SeckillOrder，停止轮询并展示订单。
- 暂未查到、编号不存在或属于别人：202 + 下方结果。

```json
{
  "result": "RESULT_UNKNOWN",
  "message": "尚未查询到你的订单，请稍后查询；持续无结果需检查消息和死信",
  "requestId": "88c54202-4bf4-4c61-9641-f5949b5712eb"
}
```

202 无法区分仍在排队、最终失败等情况，因为没有任务表。
前端计划每秒查询一次、最多 15 次；持续未知就停止并提供稍后查询/我的订单入口，不能显示确定失败。
页面卸载时必须取消定时器或未完成请求。次数和间隔是前端计划，不是后端 API 参数。

## 7. 我的订单与模拟支付

SeckillOrder：

```json
{
  "id": 224,
  "requestId": "88c54202-4bf4-4c61-9641-f5949b5712eb",
  "userId": 1,
  "productId": 14,
  "amount": 299.00,
  "status": "PENDING_PAYMENT",
  "createdAt": "2026-10-09T15:06:07",
  "updatedAt": "2026-10-09T15:06:07"
}
```

订单不包含商品名、商品图片等信息。前端第一版显示 productId，不为每笔订单强制追加商品详情查询，
因为下架商品详情会返回 404，且订单不应因此无法显示。

| 状态 | 含义 | 可操作 |
|---|---|---|
| PENDING_PAYMENT | 待支付 | 支付、取消；最终以服务端期限和状态为准 |
| PAID | 已支付 | 查询；重复支付幂等 |
| CANCELLED | 已取消 | 查询；已释放购买资格后可重购 |
| PAYMENT_TIMEOUT | 超时关闭 | 查询；已释放购买资格后可重购 |

支付期限当前配置 900 秒，后台每 60 秒扫描。
到期但未扫描时 GET 仍可能显示 PENDING_PAYMENT，支付接口会独立拒绝超时支付。
前端不把倒计时当成真正关闭订单的操作。

### GET /api/orders

无参数，只返回当前用户订单数组；没有订单时是 200 + `[]`，不是 404。
示例：`[SeckillOrder]`。当前没有管理员查询所有用户订单的接口。

### GET /api/orders/{id}

成功 200 + SeckillOrder；不存在或他人订单统一 404、空响应体。

### POST /api/orders/{id}/pay

无请求体，模拟支付，不对接第三方支付。
待支付且未超时：200 + PAID 订单；重复支付已支付订单：200 + 原 PAID 订单。
不存在或他人订单：404。取消、超时关闭或超过支付期限：409：

```json
{
  "code": "ORDER_NOT_PAYABLE",
  "message": "订单已关闭，不能支付"
}
```

没有第三方支付回调、退款、支付二维码或预支付接口，不在前端伪装成真实支付。

### POST /api/orders/{id}/cancel

无请求体。待支付取消成功：200 + CANCELLED 订单；重复取消已取消订单仍可返回 200，不重复归还库存。
不存在或他人订单：404。已支付或已超时关闭：409 + `code=ORDER_NOT_CANCELLABLE` 和 message。

如果 MySQL 已取消，但 Redis 库存释放未确认，返回 503：

```json
{
  "code": "STOCK_RELEASE_PENDING",
  "message": "库存释放尚未确认"
}
```

message 示例仅示意，按实际响应显示。此时不能显示“取消完全成功，马上重新抢购”，
应重新查询订单，提示购买资格恢复待确认。系统可能自动重试，异常数据仍需人工核对。

## 8. 管理员统计

### GET /api/admin/analytics/overview

无请求体，200 + Overview：

```json
{
  "productCount": 2,
  "onSaleProductCount": 1,
  "ordinaryStock": 150,
  "databaseSeckillStock": 10,
  "totalStock": 160,
  "redisSeckillStock": null,
  "knownRedisStockProductCount": 1,
  "orders": {
    "createdOrderCount": 4,
    "pendingPaymentOrderCount": 1,
    "paidOrderCount": 1,
    "cancelledOrderCount": 1,
    "paymentTimeoutOrderCount": 1,
    "paidAmount": 59.00,
    "paymentConversionRatePercent": 25.00
  },
  "requests": {
    "status": "AVAILABLE",
    "requestCount": 6,
    "acceptedRequestCount": 1,
    "successRatePercent": 16.67,
    "collectionStartedAt": "2026-10-09T00:00:00Z"
  },
  "generatedAt": "2026-10-09T08:00:00Z"
}
```

| 字段 | 类型 | 含义 |
|---|---|---|
| productCount / onSaleProductCount | integer | 全部商品数 / 上架商品数 |
| ordinaryStock / databaseSeckillStock | integer | 当前数据库普通库存 / 秒杀库存 |
| totalStock | integer | 上述两种当前库存之和，不是最初投放库存 |
| redisSeckillStock | integer 或 null | 全部商品 Redis 秒杀库存总和，有任一未知则 null |
| knownRedisStockProductCount | integer | Redis 库存已知的商品数 |
| orders | object | 下方 OrderStatistics |
| requests | object | 下方 RequestStatistics |
| generatedAt | string | 生成时间，UTC ISO 时间 |

### GET /api/admin/analytics/products

无请求体，返回所有商品（含下架）的 ProductStatistics 数组；无商品返回 `[]`。
它是统计列表，不是完整商品编辑列表。

### GET /api/admin/analytics/products/{id}

无请求体，返回一件商品的 ProductStatistics；不存在返回 404。可以查询下架商品的统计。

```json
{
  "productId": 14,
  "name": "无线耳机",
  "status": 1,
  "ordinaryStock": 100,
  "databaseSeckillStock": 7,
  "totalStock": 107,
  "redisSeckillStock": 6,
  "redisStockStatus": "AVAILABLE",
  "orders": {
    "createdOrderCount": 4,
    "pendingPaymentOrderCount": 1,
    "paidOrderCount": 1,
    "cancelledOrderCount": 1,
    "paymentTimeoutOrderCount": 1,
    "paidAmount": 59.00,
    "paymentConversionRatePercent": 25.00
  },
  "requests": {
    "status": "AVAILABLE",
    "requestCount": 4,
    "acceptedRequestCount": 1,
    "successRatePercent": 25.00,
    "collectionStartedAt": "2026-10-09T00:00:00Z"
  }
}
```

ProductStatistics 的 productId 为 integer、name 为 string、status 为 integer；库存、orders、requests 与总览同义。
redisStockStatus：AVAILABLE 可读，NOT_WARMED_UP 未预热，UNAVAILABLE 读取故障，INVALID 数据异常。
后三种情况下 redisSeckillStock 为 null，页面显示“未预热/不可用/异常”，不能显示 0。

OrderStatistics：所有 Count 字段为 integer，paidAmount 和 paymentConversionRatePercent 为 number。
createdOrderCount 包含已取消、已超时的历史订单；paidAmount 仅汇总 PAID 订单。
支付转化率 = paidOrderCount / createdOrderCount × 100，零分母返回 0.00。

RequestStatistics：status 为 string；两个 Count 为 integer 或 null；successRatePercent 为 number 或 null；
collectionStartedAt 为 string 或 null。AVAILABLE 正常，NOT_STARTED 无统计 Key（计数为 0），
UNAVAILABLE / INVALID 时计数与比率为 null。
受理成功率 = 返回 SUCCESS/202 次数 / 进入秒杀 Controller 的已处理请求数 × 100。
采集从阶段 14 开始，Redis 故障可能漏记，不能补回历史请求，也不代表最终下单成功率。
商品库存和跨系统统计是实时观察值，并非严格同一时刻快照。

## 9. 前端统一 HTTP 处理规则

| 场景 | 当前后端行为 | Vue 应对 |
|---|---|---|
| 认证失败 | 401，拦截器通常空响应体 | 清理会话、去登录页；登录接口 401 只提示凭据错误 |
| 权限不足 | 403，拦截器通常空响应体 | 提示无权限，不反复跳登录 |
| 资源缺失 | 404，多数为无响应体 | 页面显示不存在，不直接读取 data.message |
| 业务冲突 | 409，可能是 code/message 或 SeckillResponse | 优先展示 message，按 code/result 处理 |
| 异步受理/未知 | 202，JSON 对象 | 检查 status 和数据形状，不能一律当成订单对象 |
| 服务异常 | 500/503，结构因入口不同而异 | 有 message 则展示，否则使用默认提示 |
| 网络断开/超时 | 浏览器无 HTTP 响应 | 提示网络或结果未知，POST 不自动重试 |

框架处理的错误有时返回 timestamp/status/error/path，但没有统一契约；不能假设所有错误都有这个对象。
Axios 默认把 202 当成功，把 4xx/5xx 当异常：请求结果查询必须保留 response.status，
不能在公共拦截器中只返回 response.data 后丢掉 HTTP 状态。

前端开发时使用 Vite `/api` 代理，保留路径，不去掉 `/api`：
浏览器请求 `http://localhost:5173/api/products`，Vite 转发到 `http://localhost:8080/api/products`。
这避免浏览器开发跨域预检，不需要为开发先开放全部后端 CORS。

## 10. 当前没有的能力

管理员商品读列表/读详情、统一参数校验/错误包装、Token 刷新、服务端退出登录、
分页搜索、用户商品实时库存、订单商品名称快照、管理员全量订单查询、第三方支付、图片上传，均不应当作已有接口使用。
15B 已补管理员身份标识；15E 计划补管理员商品读取，以支持既定页面；其他能力不顺带扩张。
