package com.example.flashsale.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * 真实 RabbitMQ 集成测试：必须先启动 RabbitMQ。
 * IT 后缀使它不会在普通 mvn test 中自动执行；在 IDEA 中可单独运行。
 * 只加载消息相关组件，不连接 MySQL，也不操作 Redis 秒杀库存。
 */
@SpringBootTest(classes = RabbitMqDemoIT.TestApplication.class)
@ActiveProfiles("rabbitmq-demo")
class RabbitMqDemoIT {

    @Configuration
    @ImportAutoConfiguration(RabbitAutoConfiguration.class)
    @Import({RabbitMqDemoConfig.class, RabbitMqDemoProducer.class, RabbitMqDemoConsumer.class})
    static class TestApplication {
    }

    @Autowired
    private RabbitMqDemoProducer producer;

    // Spy 保留真实消费逻辑，并允许测试观察消费者是否收到了消息。
    @SpyBean
    private RabbitMqDemoConsumer consumer;

    @Test
    void shouldDeliverMessageToConsumerThroughRabbitMq() {
        // 唯一内容避免把队列中上一轮遗留消息误认为本轮消息。
        String message = "学习 RabbitMQ：" + UUID.randomUUID();
        producer.send(message);

        // 消费是异步的，允许最多等待 5 秒；收到后立即结束等待。
        verify(consumer, timeout(5000)).receive(message);
    }
}
