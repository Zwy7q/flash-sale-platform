package com.example.flashsale.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 消费者：监听教学队列，收到消息后输出日志。 */
@Component
@Profile("rabbitmq-demo")
public class RabbitMqDemoConsumer {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqDemoConsumer.class);

    // Spring 创建监听容器，在后台线程接收消息，不需要 Controller 主动调用此方法。
    @RabbitListener(queues = RabbitMqDemoConfig.QUEUE_NAME)
    public void receive(String message) {
        log.info("RabbitMQ 教学消费者收到消息：{}", message);
        // 目前只打印学习消息，不创建订单。默认 AUTO 模式下，方法正常返回后确认消息。
        // AUTO 是 Spring 的消费确认模式，不是 RabbitMQ 的收到即确认模式。
    }
}
