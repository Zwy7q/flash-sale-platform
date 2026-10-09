package com.example.flashsale.messaging;

import com.example.flashsale.order.OrderRepository;
import com.example.flashsale.order.OrderService;
import com.example.flashsale.product.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/** 连接真实 RabbitMQ 和 MySQL；独立队列与商品，不读取或扣减 Redis。 */
@SpringBootTest(classes = SeckillMessageIT.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("seckill-mq")
class SeckillMessageIT {
    private static final String TEST_QUEUE = "flash-sale.test.orders." + UUID.randomUUID();

    @DynamicPropertySource
    static void isolatedQueue(DynamicPropertyRegistry registry) {
        registry.add("app.mq.order-queue", () -> TEST_QUEUE);
    }

    @Configuration
    @EnableAutoConfiguration
    @Import({SeckillRabbitConfig.class, SeckillMessageProducer.class, SeckillMessageConsumer.class,
            OrderService.class, OrderRepository.class, ProductRepository.class})
    static class TestApplication { }

    @Autowired private SeckillMessageProducer producer;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RabbitListenerEndpointRegistry listeners;
    @Autowired private RabbitAdmin admin;
    @SpyBean private SeckillMessageConsumer consumer;
    private Long productId;

    @Test
    void shouldCreateOnlyOneOrderForRepeatedMessages() {
        String name = "消息测试-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_product (name, price, seckill_price, stock, seckill_stock,
                    status, seckill_start_at, seckill_end_at)
                VALUES (?, 299, 129, 2, 2, 1, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY))
                """, name);
        productId = jdbc.queryForObject("SELECT id FROM app_product WHERE name = ?", Long.class, name);
        SeckillMessage message = new SeckillMessage(UUID.randomUUID().toString(),
                990201L, productId, new BigDecimal("129.00"));
        producer.send(message);
        producer.send(message); // 模拟同一任务重复投递，编号和内容不变。

        // Spy 调用观察不是事务提交证明；还需等待真实数据库里的提交结果。
        verify(consumer, timeout(10000).times(2)).receive(anyString());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM app_order WHERE request_id = ?", Integer.class, message.getRequestId()));
            assertEquals("PENDING_PAYMENT", jdbc.queryForObject(
                    "SELECT status FROM app_order WHERE request_id = ?", String.class, message.getRequestId()));
            assertEquals(new BigDecimal("129.00"), jdbc.queryForObject(
                    "SELECT amount FROM app_order WHERE request_id = ?", BigDecimal.class, message.getRequestId()));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT seckill_stock FROM app_product WHERE id = ?", Integer.class, productId));
        });
    }

    @AfterEach
    void cleanupIsolatedResources() {
        // 先停止监听器，等待正在处理的消息结束，再删除本轮测试数据。
        listeners.stop();
        admin.deleteQueue(TEST_QUEUE);
        admin.deleteQueue(TEST_QUEUE + ".dead");
        if (productId != null) {
            jdbc.update("DELETE FROM app_order WHERE product_id = ?", productId);
            jdbc.update("DELETE FROM app_product WHERE id = ?", productId);
        }
    }
}
