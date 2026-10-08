package com.example.flashsale.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 阶段 11A：声明独立的教学队列，暂时不承载订单业务。 */
@Configuration
@EnableRabbit
@Profile("rabbitmq-demo")
public class RabbitMqDemoConfig {

    public static final String QUEUE_NAME = "flash-sale.learning.demo";

    @Bean
    public Queue rabbitMqDemoQueue() {
        // 第二个参数 true 表示队列本身持久化，RabbitMQ 重启后仍保留队列。
        // 队列持久化并不等于已解决业务消息可靠性，后续还需发送确认、幂等等。
        return new Queue(QUEUE_NAME, true);
    }
}
