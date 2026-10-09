# 11B：订单持久化基础

本步只建立订单表、状态枚举、实体和 Repository。尚无下单 HTTP 接口，
也未连接 RabbitMQ 消费者；不扣 MySQL 库存、不操作 Redis。
后续 OrderService 将负责组织事务、商品库存扣减和消费幂等。

## 表字段

| 字段 | 用途 |
|---|---|
| id | MySQL 自增订单主键 |
| request_id | 同一次秒杀任务的唯一编号；重复投递时应保持不变 |
| user_id / product_id | 谁购买了哪个商品 |
| amount | 下单时成交金额快照，不随以后商品改价变化 |
| status | 初始为 PENDING_PAYMENT，代表订单已创建、等待支付 |
| created_at / updated_at | 创建和更新时间，由 MySQL 生成 |

当前一单一件商品，因此 amount 就是一件商品的成交价。
Repository 不接收 HTTP 输入；未来成交价应由可信业务数据提供，而非由客户端决定。

`UNIQUE(request_id)` 防止同一次任务重复插入；
`UNIQUE(user_id, product_id)` 限制同一用户同一商品只有一个订单。
数据库在并发插入时也会检查唯一约束，比“先查有没有，再插入”更可靠。
当前重复插入会抛 DuplicateKeyException，后续 Service 还需识别已处理任务并返回已有结果。
只有唯一约束，不代表完整消费幂等已经完成。

用户、商品有效性暂由业务层校验，表没有物理外键。
当前一商品对应一次活动，取消或超时后也仍保留唯一约束。
如果以后允许取消后重购或多轮活动，需要一起调整订单约束和 Redis 购买标记。

## 对照代码

1. OrderStatus 用 enum 定义固定状态；name() 将枚举转换为数据库中的字符串。
2. SeckillOrder 是一行订单对应的 Java 对象；Lombok 自动生成 getter/setter。
3. OrderRepository 的 createPendingOrder：SQL 参数绑定 → INSERT → KeyHolder 取自增主键。
4. findByIdAndUserId：SQL 同时限制 id 和 user_id → ResultSet 转为对象 → if 判断是否为空。

查询时 userId 将由 JWT 提供，不能使用前端随意传来的用户 ID。
暂无 Controller，所以现在不能在 Apifox 中访问订单接口。

## 如何测试

已有 MySQL 数据卷不会在重启容器时自动执行新增 SQL。
请在 IDEA 数据库窗口连接 localhost:3307 的 flash_sale，执行
`sql/007_create_app_order.sql`（已经执行过时无需重建）。
不要删除数据卷来更新表结构。

在 IDEA 运行 OrderRepositoryIT，或终端执行：

```powershell
mvn -Dtest=OrderRepositoryIT test
```

一个类内包含四个测试方法：插入并读回、其他用户不可见、请求编号重复、重复购买。
@JdbcTest 为每个方法开启事务并在结束后回滚，因此测试通过后表通常仍为空，
自增序号可能有间隔，这是正常现象。
测试只启动 JDBC 相关组件，不依赖 RabbitMQ 和 Redis。

## Debug 顺序

在测试调用 createPendingOrder 的位置、Repository 的 jdbcTemplate.update、
keyHolder.getKey 和 findByIdAndUserId 的 return 前设置断点。
观察五个 SQL 参数、自增 ID、orders 列表和转换后的状态枚举。
测试事务提交前，另一个 IDEA 数据库连接通常看不到测试插入的订单。

下一小步：OrderService 组织事务内的订单创建和 MySQL 库存扣减；
验证后再接入真实秒杀消息生产者和消费者。
