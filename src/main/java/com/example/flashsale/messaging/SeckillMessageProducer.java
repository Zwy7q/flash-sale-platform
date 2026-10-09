package com.example.flashsale.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.AmqpException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 生产者：将 Java 任务对象转成 JSON 文本，再发送给订单队列。 */
@Service
public class SeckillMessageProducer {
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String queueName;
    private final long confirmTimeoutMs;

    public SeckillMessageProducer(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper,
            @Value("${app.mq.order-queue:flash-sale.orders.create}") String queueName,
            @Value("${app.mq.confirm-timeout-ms:5000}") long confirmTimeoutMs) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.queueName = queueName;
        if (confirmTimeoutMs <= 0) {
            throw new IllegalArgumentException("发送确认等待时间必须大于 0");
        }
        this.confirmTimeoutMs = confirmTimeoutMs;
    }

    public void send(SeckillMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("订单任务不能为空");
        }
        final String json;
        try {
            // 与 Redis 缓存序列化类似：Java 对象 → JSON 字符串。
            json = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("订单任务无法转换为 JSON", exception);
        }
        // 每次发送尝试使用独立关联编号；业务 requestId 在重试时仍保持不变。
        CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
        final CorrelationData.Confirm confirm;
        try {
            // 第四个参数把此次发送与稍后 broker 的确认结果关联起来。
            rabbitTemplate.convertAndSend("", queueName, json, correlation);
            // Future 是“稍后得到的结果”；get 最多等待指定毫秒数。
            confirm = correlation.getFuture().get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); // 保留中断标志，不能吞掉线程取消信号。
            throw new MessagePublishException(MessagePublishException.Outcome.UNKNOWN,
                    "等待发送确认被中断，结果未知", exception);
        } catch (TimeoutException | ExecutionException | AmqpException exception) {
            // 即使 Java 没拿到确认，broker 也可能收到消息，甚至消费者已经创建订单。
            throw new MessagePublishException(MessagePublishException.Outcome.UNKNOWN,
                    "未取得有效发送确认，结果未知", exception);
        }

        // 开启 returns 后，Spring 保证关联的 returned 信息先于确认 Future 完成写入。
        // 默认交换机找不到队列，也可能 ACK，所以不能只检查 confirm.isAck()。
        if (correlation.getReturned() != null) {
            throw new MessagePublishException(MessagePublishException.Outcome.NOT_ROUTED,
                    "订单消息无法路由到目标队列：" + queueName, null);
        }
        if (!confirm.isAck()) {
            // NACK/连接异常不能作为自动增加 Redis 库存的充分依据。
            throw new MessagePublishException(MessagePublishException.Outcome.UNKNOWN,
                    "RabbitMQ 未给出成功确认：" + confirm.getReason(), null);
        }
        // ACK 且未退回：本次消息被 broker 确认接收，不等于消费者已成功创建订单。
    }
}
