# 阶段 11A：先弄懂 RabbitMQ 的收发

## 本步目标

让 Java 发送一条文本消息，经过真实 RabbitMQ 后由 Java 消费者接收。
现在仍未连接秒杀接口，也不会创建订单；测试不修改 MySQL 或 Redis 数据。

RabbitMQ 是消息中间件：发送方把任务消息交给它，接收方在后台处理。
后续秒杀接口可以先提交下单任务，再由消费者处理数据库写入。
它有助于削峰，但队列本身不会解决库存一致性、消息丢失或重复下单。

## 启动与测试

1. 打开 Docker Desktop，等待引擎启动。
2. 在 IDEA 的 Terminal 中执行（工作目录为本项目）：

```powershell
docker compose up -d rabbitmq
docker compose ps rabbitmq
docker exec flash-sale-rabbitmq rabbitmq-diagnostics -q ping
```

首次启动需要下载镜像并初始化。`ping` 成功后再运行测试。
如果拉取镜像超时，检查 Docker Desktop 的代理或镜像网络连接；这不是 Java 代码错误。

3. 在 IDEA 的 Maven 面板重新加载依赖。
4. 打开 `src/test/java/com/example/flashsale/messaging/RabbitMqDemoIT.java`，运行测试方法。
   测试类已经使用 `@ActiveProfiles("rabbitmq-demo")`，无需修改启动类配置。

也可在终端运行：

```powershell
mvn -Dtest=RabbitMqDemoIT test
```

`IT` 后缀的测试不会被普通 `mvn test` 默认选中，需要单独运行。
预期测试通过，并看到日志：`RabbitMQ 教学消费者收到消息：学习 RabbitMQ：...`。
这里不要求先运行主启动类：测试会启动自己的最小 Spring 容器。

## 对照代码理解流程

```text
测试调用 producer.send(message)
    ↓
RabbitTemplate.convertAndSend("", 队列名, 消息)
    ↓
RabbitMQ 默认交换机根据路由键找到同名队列
    ↓
flash-sale.learning.demo 队列暂存消息
    ↓
监听容器后台接收消息
    ↓
@RabbitListener 标注的 receive(String message)
    ↓
输出日志，正常返回后由 Spring 确认消费
```

- 生产者 Producer：发送任务的 Java 代码。
- 交换机 Exchange：按路由规则把消息交给队列。本步使用名字为空字符串的默认交换机。
- 路由键 Routing Key：告诉交换机该往哪里发送；默认交换机按同名队列路由。
- 队列 Queue：等待处理的消息存放位置。
- 消费者 Consumer：接收并处理消息的 Java 代码。

`RabbitMqDemoConfig` 的 `@Bean` 创建队列声明对象，Spring AMQP 连接 broker 后声明真实队列；
并非 `new Queue(...)` 本身就发送了网络请求。`true` 表示队列持久化，不能据此认为业务消息绝不丢失。

`RabbitMqDemoProducer.send` 的三个参数分别是交换机、路由键、消息内容。
`convertAndSend` 把 String 转为消息字节后发送。
它正常返回不证明订单创建成功，本阶段由集成测试检查接收端实际收到相同消息。

`RabbitMqDemoConsumer.receive` 不由 Controller 调用。Spring 根据 `@RabbitListener`
创建监听容器，在另一个线程中接收消息并调用方法。因此发送和消费不是同一个调用栈。
默认确认模式为 Spring AUTO：监听方法正常返回后确认。可靠性、失败重试和幂等后续单独实现。

`@Profile("rabbitmq-demo")` 表示这些教学组件仅在启用该配置时加载。
普通启动项目不会加载教学消费者。如果想让主程序也监听教学队列，
可在 IDEA 启动配置的 Active profiles 填 `rabbitmq-demo`。

## 在 IDEA 中 Debug

在 `producer.send(message)`、`convertAndSend(...)` 和消费者 `log.info(...)` 三处打断点，
用 Debug 运行测试。观察生产者线程和消费者线程名称，它们通常不同。
消费者断点建议设置 Suspend 为 Thread，避免暂停所有线程，阻塞消息发送。
测试最多等待 5 秒，逐步调试时可以临时把 `timeout(5000)` 改为 `timeout(120000)`，
调试结束改回。发送后不要依靠 Step Into 一直跟到消费者，需要等消费者断点命中。

## 网页观察队列

打开 http://localhost:15672，账号 `flashsale`，密码 `flashsale123456`。
进入 Queues and Streams，找到 `flash-sale.learning.demo`。
队列通常在测试连接并声明之后才出现。

测试运行时消息很快被消费，Ready 为 0 是正常的；它不代表没发送过。
测试结束后消费者停止，你可以在该队列的 Publish message 区域发送教学文本，
然后看到 Ready 增加。重新运行测试会消费遗留的教学文本并打印日志。
集成测试使用唯一消息内容，保证验证的是本次发送的消息。

只在教学队列中操作。后续订单队列要保留独立名称和可靠处理逻辑。

## 下一步

理解并验证本步后进入 11B：设计订单表，先实现订单数据持久化基础。
再把秒杀消息与订单消费者连接起来，补上幂等和失败补偿。
