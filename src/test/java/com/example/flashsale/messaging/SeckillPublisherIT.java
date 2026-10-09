package com.example.flashsale.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 RabbitMQ 测试：只有生产者，故意发送到不存在的随机队列。 */
@SpringBootTest(classes = SeckillPublisherIT.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("seckill-mq")
class SeckillPublisherIT {
    private static final String QUEUE = "flash-sale.test.confirm." + UUID.randomUUID();
    @DynamicPropertySource
    static void isolatedQueue(DynamicPropertyRegistry registry) {
        registry.add("app.mq.order-queue", () -> QUEUE);
    }
    @Configuration
    @ImportAutoConfiguration({RabbitAutoConfiguration.class, JacksonAutoConfiguration.class})
    @Import({SeckillRabbitConfig.class, SeckillMessageProducer.class})
    static class TestApplication { }

    @Autowired private RabbitTemplate rabbit;
    @Autowired private RabbitAdmin admin;
    @Autowired private ObjectMapper mapper;

    @Test
    void shouldReportUnroutableMessageInsteadOfTreatingBrokerAckAsSuccess() {
        String missingQueue = "flash-sale.test.missing." + UUID.randomUUID();
        SeckillMessageProducer producer = new SeckillMessageProducer(rabbit, mapper, missingQueue, 5000);
        SeckillMessage task = new SeckillMessage(UUID.randomUUID().toString(),
                990401L, 1L, new BigDecimal("129.00"));
        MessagePublishException error = assertThrows(MessagePublishException.class, () -> producer.send(task));
        assertEquals(MessagePublishException.Outcome.NOT_ROUTED, error.getOutcome());
    }

    @AfterEach
    void cleanup() {
        admin.deleteQueue(QUEUE);
        admin.deleteQueue(QUEUE + ".dead");
    }
}
