package com.example.flashsale.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 与文本教学队列分开，只有启用 seckill-mq 配置才监听真实订单任务。 */
@Configuration
@EnableRabbit
@Profile("seckill-mq")
public class SeckillRabbitConfig {
    @Bean
    public Queue seckillOrderQueue(@Value("${app.mq.order-queue:flash-sale.orders.create}") String name) {
        // 测试可以覆盖队列名，避免消费人工测试或业务队列中的消息。
        return new Queue(name, true);
    }
}
