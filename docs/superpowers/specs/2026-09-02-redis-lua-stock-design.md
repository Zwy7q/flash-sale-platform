# Redis Lua 原子扣库存设计

## 目标

在秒杀请求到达时，使用 Redis Lua 脚本原子完成库存读取、库存判断和库存扣减，避免多个并发请求同时读取到相同库存而产生超卖。

## 组件与职责

- `StockRedisService`：接收商品 ID，构造库存 Key，执行 Lua 脚本并解释返回值。
- Redis Lua 脚本：在 Redis 服务端一次完成库存校验和扣减。
- Redis Key：`flash-sale:stock:{productId}`。

## Lua 返回值约定

- `1`：扣减成功；
- `0`：库存不足；
- `-1`：库存 Key 不存在，通常表示尚未预热库存。

## 数据流

```text
StockRedisService.decreaseStock(productId)
        -> Redis Lua
        -> 读取 flash-sale:stock:{productId}
        -> 判断库存
        -> 成功时执行 DECR
        -> 返回状态码
```

## 错误处理

服务层不把库存不足当作系统异常，而是转换成明确的业务结果；库存 Key 不存在则保留独立状态，便于后续定位库存预热问题。

## 测试策略

先使用 Mockito 验证服务是否传入正确的 Key 和脚本参数，再使用真实 Redis 集成测试验证 Lua 的原子扣减行为。当前阶段只实现服务和测试，不实现秒杀接口、订单和消息队列。
