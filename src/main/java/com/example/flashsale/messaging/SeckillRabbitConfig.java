package com.example.flashsale.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import java.util.HashMap;
import java.util.Map;

/** 与文本教学队列分开，只有启用 seckill-mq 配置才监听真实订单任务。 */
@Configuration
@EnableRabbit
@Profile("seckill-mq")
public class SeckillRabbitConfig {
    @Bean
    public RabbitTemplateCustomizer mandatoryOrderMessages() {
        // 找不到目标队列时必须退回消息，不能静默丢弃后仍只收到 broker ACK。
        return template -> template.setMandatory(true);
    }

    @Bean
    public Queue seckillOrderQueue(@Value("${app.mq.order-queue:flash-sale.orders.create}") String name) {
        // 测试可以覆盖队列名，避免消费人工测试或业务队列中的消息。
        Map<String, Object> arguments = new HashMap<>();
        // 拒绝且不重新入队时，交给默认交换机，以死信队列名作为路由键。
        arguments.put("x-dead-letter-exchange", "");
        arguments.put("x-dead-letter-routing-key", name + ".dead");
        // 名称、持久化、是否独占、是否自动删除、扩展参数。
        return new Queue(name, true, false, false, arguments);
    }

    @Bean
    public Queue seckillDeadLetterQueue(
            @Value("${app.mq.order-queue:flash-sale.orders.create}") String name) {
        // 不配置消费者，不自动重投或自动补偿，先保留失败任务供排查。
        return new Queue(name + ".dead", true);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory orderRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        // 保留 Spring Boot 的连接、消息转换和默认 AUTO 确认配置。
        configurer.configure(factory, connectionFactory);
        factory.setPrefetchCount(1); // 每个消费者限制未确认消息数量，便于理解处理顺序。
        factory.setDefaultRequeueRejected(false);
        RetryInterceptorBuilder.StatelessRetryInterceptorBuilder retryBuilder = RetryInterceptorBuilder.stateless();
        // 总计最多 3 次：第一次执行 + 两次重试，不是额外再重试三次。
        retryBuilder.maxAttempts(3);
        // 参数：首次等待毫秒数、增长倍数、最大等待毫秒数；这里固定间隔 500ms。
        retryBuilder.backOffOptions(500, 1.0, 500);
        // 耗尽后拒绝消息且不重新入队，让 broker 按队列配置转入死信队列。
        retryBuilder.recoverer(new RejectAndDontRequeueRecoverer());
        factory.setAdviceChain(retryBuilder.build());
        return factory;
    }
}
