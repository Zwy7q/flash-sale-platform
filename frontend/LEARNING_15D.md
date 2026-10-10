# 15D：我的订单、模拟支付与取消

本阶段把秒杀结果带来的订单接到前端。页面只展示和发起操作，订单状态永远由后端决定。

## 1. 接口和分层

`src/api/orders.js` 提供三个方法：`GET /api/orders` 查询当前用户订单，`POST /api/orders/{id}/pay` 模拟支付，`POST /api/orders/{id}/cancel` 取消待支付订单。三个请求都使用 JWT；POST 不需要 JSON 请求体，前端只提交订单 ID。

`OrderListView.vue` 负责页面，`ordersFlow.js` 负责查询、操作状态、刷新和错误处理。页面不直接修改订单状态。

## 2. 支付流程

```text
点击模拟支付
  ↓ POST /api/orders/{id}/pay
后端校验 JWT、订单归属、状态和支付期限
  ↓ MySQL 事务更新为 PAID
返回数据库中的完整订单
  ↓ 前端用返回对象替换列表项
```

前端不能直接写 `order.status = 'PAID'`，因为订单可能已取消、超时，或支付期限刚刚结束。后端返回的状态才是真实结果。重复支付由后端幂等处理。

## 3. 取消和库存释放

取消待支付订单后，后端会更新 MySQL 状态并释放 Redis 购买资格。如果返回 503 `STOCK_RELEASE_PENDING`，说明库存释放还没有确认，页面会重新查询并提示“释放待确认”，不会承诺可以立即重购。

只有服务端确认释放后，下一次秒杀请求才可能成功。前端不能删除订单、增加库存或伪造取消成功。

## 4. 为什么要有 actionId

`actionId` 保存当前正在操作的订单 ID。开始操作后两个按钮都禁用；请求成功时用服务端返回值替换订单；409、503、404 会重新调用 `GET /orders`；`finally` 中清除 actionId。

这是防重复点击，真正的幂等和状态保护仍在后端事务、行锁和状态判断中。页面卸载时 `stop()` 设置停止标记，迟到响应不会更新离开的页面。

## 5. 状态展示

`utils/display.js` 只做文字映射：`PENDING_PAYMENT` 待支付、`PAID` 已支付、`CANCELLED` 已取消、`PAYMENT_TIMEOUT` 超时关闭。它不改变状态。未知金额显示 `—`，零仍显示为 0。

只有待支付订单显示支付和取消按钮；空数组代表查询成功但没有订单，不是错误。

## 6. 手工测试

1. 登录已有秒杀订单的账号，打开 `http://localhost:5173/orders`。
2. 检查 Network 中的 `GET /api/orders`，确认只返回当前 JWT 用户的订单。
3. 对待支付订单点击模拟支付，观察按钮 loading，返回后状态变为“已支付”。
4. 对另一笔待支付订单点击取消，返回后状态变为“已取消”；重复刷新不会再次归还库存。
5. 已支付、已取消和超时订单不显示操作按钮。
6. 取消后回商品详情重新秒杀，是否能购买以服务端释放结果为准。

Apifox 请求：

```text
GET  http://localhost:8080/api/orders
POST http://localhost:8080/api/orders/订单ID/pay
POST http://localhost:8080/api/orders/订单ID/cancel
```

都要带当前用户的 `Authorization: Bearer <JWT>`，POST 不需要请求体。

```powershell
cd D:/codexspace/codespace02/projects/flash-sale-platform/frontend
npm test
npm run build
```

本阶段测试覆盖列表、支付、取消、状态冲突、库存释放待确认和重复点击。下一阶段是 15E 管理员商品管理。
