package com.example.flashsale.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 生产者：将一条教学文本消息发送给 RabbitMQ。 */
@Service
@Profile("rabbitmq-demo")
public class RabbitMqDemoProducer {

    private final RabbitTemplate rabbitTemplate;

    // 构造方法注入：RabbitTemplate 由 Spring Boot 自动配置并交给 Spring 管理。
    public RabbitMqDemoProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void send(String message) {
        // 参数 1：交换机名称。空字符串表示 RabbitMQ 的默认交换机。
        // 参数 2：路由键。默认交换机根据路由键找到同名队列。
        // 参数 3：消息内容。String 会自动转换为消息字节。
        rabbitTemplate.convertAndSend("", RabbitMqDemoConfig.QUEUE_NAME, message);
        // 此处方法正常返回不代表业务处理成功；本阶段由集成测试验证实际收发。
    }
}
