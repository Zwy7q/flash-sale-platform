package com.example.flashsale.messaging;

import com.example.flashsale.order.OrderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 订单消费者：转换任务，调用 Spring 管理的事务服务，成功后确认消费。 */
@Component
@Profile("seckill-mq")
public class SeckillMessageConsumer {
    private static final Logger log = LoggerFactory.getLogger(SeckillMessageConsumer.class);
    private final ObjectMapper objectMapper;
    private final OrderService orderService;

    public SeckillMessageConsumer(ObjectMapper objectMapper, OrderService orderService) {
        this.objectMapper = objectMapper;
        this.orderService = orderService;
    }

    @RabbitListener(queues = "${app.mq.order-queue:flash-sale.orders.create}")
    public void receive(String json) {
        final SeckillMessage message;
        try {
            // JSON 字符串 → Java 任务对象，不修改全局 RabbitTemplate 的转换器。
            message = objectMapper.readValue(json, SeckillMessage.class);
        } catch (JsonProcessingException exception) {
            // 无法解析的消息不应无限重投。本阶段未配死信队列，拒绝后将丢弃。
            throw new AmqpRejectAndDontRequeueException("订单消息 JSON 格式错误", exception);
        }
        if (message == null) {
            throw new AmqpRejectAndDontRequeueException("订单消息不能为 null");
        }
        // 不手动 new OrderService，必须调用注入的 Spring 事务代理。
        Long orderId = orderService.createOrder(message.getRequestId(), message.getUserId(),
                message.getProductId(), message.getAmount());
        // createOrder 返回时事务已提交；重复任务返回已有订单，不重复扣减 MySQL 库存。
        log.info("订单任务处理完成：requestId={}，orderId={}", message.getRequestId(), orderId);
        // 默认 Spring AUTO：本方法正常返回后确认消息。
        // 业务异常继续向外抛出；有界重试/死信和补偿将在下一小步完成。
    }
}
