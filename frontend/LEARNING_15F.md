# 15F：统计看板与整体联调教学

本阶段新增 `/admin/analytics`。管理员登录后，点击导航“统计看板”。
后端统计接口已经在阶段 14 完成，本阶段没有修改 Java 接口，也没有新增商品、订单或清空 Redis。

## 1. 本阶段文件

| 文件 | 作用 |
|---|---|
| src/api/admin.js | 新增三个统计 GET 请求 |
| src/composables/analyticsFlow.js | 加载、刷新、单商品详情、错误和卸载清理 |
| src/views/admin/AnalyticsView.vue | 数字卡片、商品表格和详情面板 |
| src/utils/display.js | 区分零与未知，展示状态，将 UTC 时间转成北京时间 |
| src/analytics.test.js | 统计显示、部分失败、详情缺失、重复点击和迟到响应测试 |

沿用 Vue Router 的管理员路由守卫和 Axios 的 JWT 请求头。
真正权限由后端拦截器控制，不能仅靠隐藏导航链接。

## 2. 进入页面时如何取得数据

```text
AnalyticsView 挂载
    ↓ onMounted(flow.refresh)
GET /api/admin/analytics/overview     总览
GET /api/admin/analytics/products     商品统计列表
    ↓ JWT → 管理员身份 → AnalyticsController
AnalyticsService 聚合 MySQL 与 Redis 数据
    ↓ HTTP JSON
overview / products 响应式变量更新
    ↓ Vue 自动更新数字卡片和表格
```

`ref(null)` 创建一个响应式变量，初始没有数据。
JavaScript 用 `overview.value` 读取或修改；模板里的 `overview` 自动解包，不写 .value。
`response.data` 是 Axios 收到的 JSON，response.status 才是 HTTP 状态码。

`Promise.allSettled([...])` 同时发出两个独立 GET，并等待每个请求成功或失败。
返回数组顺序与传入请求顺序一致：results[0] 是总览，results[1] 是列表。
成功结果的 status 是 fulfilled，数据在 value.data；失败结果的 status 是 rejected，错误在 reason。
注意这里的 status 是 Promise 状态，不是 HTTP 200/500。

例如总览失败而列表成功，页面显示总览错误，仍显示商品表格。
失败部分清除旧数据，不把上次总览误当成这次读取成功；加载期间保留旧数据并明确提示。
手动刷新重新请求，不使用高频定时器。

## 3. 为什么未知库存不能显示为 0

| 返回值或状态 | 含义 | 页面显示 |
|---|---|---|
| redisSeckillStock = 0 | 确认当前库存为零 | 0 |
| null + NOT_WARMED_UP | 尚未建立 Redis 库存 Key | 未知 / 未预热 |
| null + UNAVAILABLE | Redis 连接或读取失败 | 未知 / 读取不可用 |
| null + INVALID | Key 的内容异常 | 未知 / 数据异常 |

不要使用 `value || 0`：null 会变成 0，用户会误以为商品已售罄。
代码明确判断 null 和 undefined，其他值原样显示。
读取统计不会自动预热库存；管理员看一次看板不应改变秒杀业务状态。
总览只有全部商品的 Redis 库存都已知时才返回总和；否则总和为 null，并给出已知商品数量。

请求采集的 NOT_STARTED 表示尚无采集记录，计数 0 是后端契约允许的真实零。
UNAVAILABLE/INVALID 的计数为 null，应显示未知；采集起点为 null 也不能证明“从未采集”，所以显示时间未知。

## 4. 各项统计口径

- MySQL 总库存是当前普通库存加当前秒杀库存，不是活动最初投放量。
- 历史订单包含待支付、已支付、取消和超时关闭，取消不会从历史总数删除。
- 已支付金额只汇总 PAID 订单金额快照，商品后来改价不改变旧订单金额。
- 支付转化率是已支付订单数 / 历史创建订单数 × 100。
- 受理成功率是返回 SUCCESS/202 的次数 / 进入秒杀 Controller 的已处理请求数 × 100。

202 表示秒杀请求已受理，不表示 RabbitMQ 已成功创建订单，更不表示已支付。
请求计数从采集启用后开始，Redis 故障可能漏记，不能将它当成全部历史请求量。
跨 MySQL / Redis 的不同查询没有严格共同快照，并发时短暂差异需要结合业务链路核对。

## 5. 点击查看统计和手动刷新

表格按钮执行 `selectProduct(productId)`，发送第三个接口：
`GET /api/admin/analytics/products/{id}`。
这里用的是统计返回的 productId，不是商品管理返回的 id。
详情显示订单各状态数量和采集起点；不存在返回 404，显示不存在，不伪造一个全零商品。

`detailVersion` 是前端请求序号，作用是保护显示，不是数据库乐观锁。
例如先点击商品 14，又点击商品 15，14 的慢请求最后返回也不能覆盖 15：
只允许响应携带的 version 与当前 detailVersion 相等时更新界面。
关闭详情或刷新总览也会使旧序号失效。
`onBeforeUnmount(flow.stop)` 在离开页面时调用 AbortController.abort，取消未完成请求，并忽略迟到响应。

## 6. 统计时间与商品时间不同

商品活动时间是无时区的 LocalDateTime，按项目约定直接展示为北京时间。
统计 generatedAt 和 collectionStartedAt 带 Z，是 UTC 时间，应转成北京时间。
`Intl.DateTimeFormat` 指定 `timeZone: 'Asia/Shanghai'`，不依赖浏览器所在机器的时区。
例如 `2026-10-10T00:00:00Z` 显示为北京时间 08:00:00。

## 7. 本次验收证据

39 项前端测试和构建通过，覆盖账户、秒杀轮询、订单、商品管理及统计流程。
构建仍有 Element Plus 全量引入导致的大 chunk 提示，构建成功，后续可按需拆包。

真实浏览器验证了管理员登录、看板总览、单商品详情、刷新与订单页面衔接。
查询当时 MySQL：15 件商品、7 笔历史订单、2 笔已支付、已支付金额598元，与页面一致。
已有未预热商品和无请求商品正确显示“未知/未预热”及真实 0。
独立临时后端指向不可连接的 Redis 端口，浏览器确认 Redis 库存及请求计数显示未知/不可用，
MySQL 库存和订单统计仍可展示；没有停止用户的 Redis 容器。
本阶段后端代码未变，没有重跑会插入商品的后端集成测试，因此不继续消耗商品自增编号。

## 8. 你自己做完整购买验收

本次没有替你新增订单或改变已有订单状态。以下真实写入步骤由你选择测试商品完成，记录前后统计：

1. 确保 Docker 的 MySQL、Redis、RabbitMQ 正常，IDEA 后端和 Vite 已运行。
2. 用普通测试用户注册或登录，商品列表→详情→秒杀，活动时间必须有效。
3. 返回202后等待查询订单变为200，进入“我的订单”，确认待支付金额及商品 ID。
4. 模拟支付，确认 PAID；管理员刷新看板，已支付数量、金额和转化率应更新。
5. 用另一个测试用户创建待支付订单并取消，确认 CANCELLED；刷新看板，取消数量增加，
   历史订单数保留这笔订单，支付金额不增加。库存释放确认后才能测试重购。
6. 管理员商品管理→统计看板，确认新增/上下架在统计中的商品数、上架数正确。
7. 普通用户直接访问 /admin/analytics 应进入无权限页；不要把隐藏链接当成后端安全证明。
8. 刷新浏览器，确认 /me 恢复会话；退出后受保护页面重新要求登录。

前端自动测试通过不等于本次重新完成上述所有真实写入链路。
全流程写入验收仍需你完成并确认；各模块此前的实际联调与本次只读核对分别保留记录。
不要为获得连续 ID 调整自增计数，不要为测试批量删除商品、订单或 Redis Key。

## 9. 下一阶段

前端功能已完成15A～15F。理解并完成整体购买验收后，进入阶段16：
JMeter 压测、QPS/P95/错误率和超卖核对、README/API/架构图/启动说明，以及简历与面试材料。
压测结果以真实测量为准，不提前编造性能数字。Git commit/push 仍由你自行执行。
