# 15C：商品展示、秒杀与异步结果查询

本阶段接入已有后端，不改 Java 接口，不做支付与取消页面。Git 提交和推送由你自己完成。

## 1. 先运行，再对照代码理解

保持 MySQL、Redis、RabbitMQ 和 IDEA 后端运行。frontend 目录执行 npm run dev，已有 5173 服务时不重复启动。
打开 http://localhost:5173/products，未登录会先去登录页，成功后回到商品列表。

新增文件的阅读顺序：

1. src/api/products.js：两个商品查询方法。
2. src/views/ProductListView.vue：列表加载与展示。
3. src/views/ProductDetailView.vue：详情、秒杀按钮与结果展示。
4. src/api/seckill.js：提交和查询两个不同的 HTTP 请求。
5. src/composables/seckillFlow.js：提交状态、轮询、刷新恢复与停止逻辑。
6. src/seckill.test.js：集中测试关键边界，不需要每个方法单独建一个测试类。

utils/display.js 提前提供金额、时间和订单状态显示函数，下一阶段订单页也会复用。

## 2. 商品列表为什么只查一次摘要

ProductListView 的 products 是 ref([])。onMounted(loadProducts) 表示页面挂载后调用加载函数。
函数先设 loading=true，再 await productListRequest，成功后把 response.data 赋给 products.value。
Vue 发现响应式数据变化，会自动更新模板，不用自己操作 HTML。

```javascript
const response = await productListRequest(controller.signal)
products.value = response.data
```

productListRequest 使用 GET /api/products，返回 ProductSummary[]，没有描述和库存。
模板 `v-for="product in products"` 循环渲染卡片；`:key="product.id"` 用稳定 ID 标识每个商品。
`:to="'/products/' + product.id"` 是浏览器页面地址，不是后端接口地址。
点击后详情页再 GET /api/products/{id}，取得完整信息，避免列表一次携带过多字段。

v-if / v-else-if / v-else 依次处理加载中、错误、空列表和正常数据。
finally 恢复按钮，catch 展示失败。404 详情表示商品不存在或已下架。

## 3. 点击秒杀后发生什么

```text
ProductDetailView 的按钮调用 flow.submit()
  → POST /api/seckill/products/{id}，不带请求体
  → Axios 自动附加 JWT
  → 后端检查商品、活动和购买资格，Lua 预扣库存
  → RabbitMQ 消息交给消费者创建 MySQL 订单
  → POST 返回 202 与 requestId
  → 前端用 requestId 定期 GET 查询
  → GET 返回 200，才展示具体订单
```

前端不发送 userId、amount 或库存。身份来自 JWT，金额和库存由后端决定。
202 是“已受理”，不是“订单已创建”，更不是“已支付”。
GET 查询返回 202 时，后端只知道暂未查到订单，不能据此声称确定失败或一定还在排队。

## 4. 为什么单独建立 seckillFlow

这是 Vue 组合函数：把一组有关联的状态和操作放一起，页面负责展示，组合函数负责流程。
没有引入新的框架，也没有把所有业务塞进 Pinia。商品秒杀状态只属于当前页面。

| phase 值 | 含义 | 是否能再次提交 |
|---|---|---|
| idle | 尚未提交 | 能 |
| submitting | POST 请求未结束 | 不能 |
| polling | 正在查询结果 | 不能 |
| success | 查到订单 | 当前页不能 |
| rejected | 后端明确拒绝本次请求 | 能手动重试，仍需后端校验 |
| unknown | 结果待确认 | 不能，只能继续查询或核对订单 |

phase、message、requestId、order、attempts 都是 ref。
computed 让 busy 随 phase 自动变化，canSubmit 根据 phase 自动决定按钮是否允许提交。
按钮的 disabled 防止用户交互；submit 方法内部再次检查 canSubmit，避免连续点击重复发请求。

页面通过 `const { phase, ... } = flow` 取得的仍是 ref，不是普通字符串。模板自动解包，JavaScript 用 .value。

## 5. 轮询为什么用 setTimeout

setTimeout(queryOnce, 1000) 的意思是安排约一秒后执行一次 queryOnce，不是立即执行。
queryOnce 使用 await 等查询完成，再决定安排下一次。
因此是“等待一秒 → 请求 → 请求结束 → 再等待一秒”，慢请求期间不会启动第二个请求。

每轮最多 15 次 GET：

- 200：保存订单，停止，不再安排计时器。
- 202 且不到 15 次：再安排下一次。
- 第 15 次仍是 202：停止，显示待确认。
- 网络或其他查询错误：停止，保留编号，允许手动继续查询。

“继续查询结果”开启新一轮最多 15 次 GET，绝不重新 POST 秒杀。
这里没有用 setInterval，因为它会按固定间隔触发，容易在上次请求还没完成时再发请求。

## 6. 为什么要 AbortController 和 stop

AbortController 创建一个取消控制器，controller.signal 传给 Axios。
离开页面时 onBeforeUnmount 调用 controller.abort() 和 flow.stop()：

1. clearTimeout 清掉还没触发的计时器。
2. abort 停止浏览器等待尚未完成的请求。
3. stopped 标记让迟到响应不再更新已离开的页面。

abort 不能撤销服务器已经执行的扣库存和下单，所以离开页面不等于取消订单。
MainLayout 给 RouterView 绑定路径 key，/products/1 切换到 /products/3 时会卸载旧详情页，避免轮询串到另一个商品。

## 7. 网络失败为什么不能直接再点一次

POST 超时只表示浏览器没拿到响应，服务器可能已处理。
PUBLISH_UNKNOWN 或 PUBLISH_FAILED 也可能保留 Redis 预扣，不能擅自恢复购买资格。
有 requestId 就继续查询，没有编号则提示核对自己的订单，不自动重发。

本阶段用 sessionStorage 记录未确认提交，键为：

```text
flash-sale:pending:<用户ID>:<商品ID>
```

记录中只有 requestId，不保存密码。发送前先留空编号记录，拿到编号后补上。
刷新后读取记录，显示待确认；有编号可手动继续查询。同一标签页不同用户不会混用编号。
查到订单或收到明确业务拒绝才删除记录，关闭标签页后 sessionStorage 不长期保留。
这是前端防误点措施，不能替代后端幂等和重复购买校验，也不能保证跨设备去重。

## 8. 你怎么测试

先使用自己已经验证的账号登录，不要在 URL 中写大括号。

1. 查看商品列表，F12 → Network 应有 GET /api/products，返回摘要数组。
2. 进入详情，应有 GET /api/products/数字，返回描述、价格、排期和库存参考。
3. 选择活动进行中的商品，点击一次秒杀。POST 没有业务请求体，请求头有 Bearer。
4. 观察 202 响应的 requestId，随后出现 GET /api/seckill/requests/编号；200 后页面显示订单 ID、金额和状态。
5. 查询期间立即返回列表，Network 中不应继续出现该详情页发起的新轮询。
6. 订单创建后刷新详情再秒杀，已有有效购买记录应得到重复购买提示。
7. 已结束商品点击秒杀，显示活动时间提示；下架商品详情显示不存在或已下架。

订单页面在 15D 接入。当前可以在 Apifox 使用同一个用户的 JWT：

```text
GET  http://localhost:8080/api/orders
POST http://localhost:8080/api/orders/你的订单ID/cancel
```

只有待支付订单可以取消；取消成功且购买资格释放后，重新进入详情才能继续测试购买。
不要为了重测清空全部 Redis，也不要把 Token 发给别人。
如果 POST 结果未知且没有 requestId，先通过订单接口、服务端日志和消息情况核对，不直接重复提交。

停止网络不等于制造可靠的“未下单”。持续未知、慢请求、不重复提交和卸载清理主要用自动测试验证：

```powershell
cd D:/codexspace/codespace02/projects/flash-sale-platform/frontend
npm test
npm run build
```

本次前端 21 项测试与构建通过；浏览器实测普通用户登录、列表、详情、真实异步创建订单、重复购买、活动已结束和下架商品。
临时订单通过取消接口释放库存后清理测试数据，不改原有商品库存和历史订单；统计会保留真实联调请求痕迹。

## 9. 下一阶段

15D：我的订单列表、模拟支付、取消与状态刷新。届时才能在页面完成完整购买流程。
先理解三个区别：页面路由和 API 路径、202 受理和 200 查到订单、停止轮询和取消订单。
