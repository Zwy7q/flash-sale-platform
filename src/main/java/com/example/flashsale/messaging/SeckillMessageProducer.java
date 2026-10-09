package com.example.flashsale.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 生产者：将 Java 任务对象转成 JSON 文本，再发送给订单队列。 */
@Service
@Profile("seckill-mq")
public class SeckillMessageProducer {
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String queueName;

    public SeckillMessageProducer(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper,
            @Value("${app.mq.order-queue:flash-sale.orders.create}") String queueName) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.queueName = queueName;
    }

    public void send(SeckillMessage message) {
        final String json;
        try {
            // 与 Redis 缓存序列化类似：Java 对象 → JSON 字符串。
            json = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("订单任务无法转换为 JSON", exception);
        }
        // 默认交换机按路由键找到同名队列，第三个参数发送的是 JSON 文本。
        rabbitTemplate.convertAndSend("", queueName, json);
        // 这里没有开启发送确认；正常返回不能据此向用户承诺订单创建成功。
    }
}
