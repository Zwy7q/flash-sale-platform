package com.example.flashsale.messaging;

import com.example.flashsale.order.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** 真实失败链路：订单 INSERT 失败 → 库存回滚 → 3 次耗尽 → 原消息进入死信队列。 */
@SpringBootTest(classes = SeckillMessageIT.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("seckill-mq")
class SeckillFailureIT {
    private static final String TEST_QUEUE = "flash-sale.test.failure." + UUID.randomUUID();
    @DynamicPropertySource
    static void isolatedQueue(DynamicPropertyRegistry registry) {
        registry.add("app.mq.order-queue", () -> TEST_QUEUE);
    }
    @Autowired private SeckillMessageProducer producer;
    @Autowired private RabbitTemplate rabbit;
    @Autowired private RabbitAdmin admin;
    @Autowired private RabbitListenerEndpointRegistry listeners;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private OrderRepository orders;
    @Autowired private ObjectMapper mapper;
    @Autowired @Qualifier("seckillOrderQueue") private Queue queue;
    @SpyBean private SeckillMessageConsumer consumer;
    private Long productId;

    @Test
    void shouldRetryThreeTimesThenKeepFailedMessageAndRollBackStock() throws Exception {
        // 先检查声明：功能未实现时在这里失败，不发送会无限重投的任务。
        assertEquals("", queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(TEST_QUEUE + ".dead", queue.getArguments().get("x-dead-letter-routing-key"));
        String name = "失败测试-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_product (name, price, seckill_price, stock, seckill_stock,
                    status, seckill_start_at, seckill_end_at)
                VALUES (?, 299, 129, 2, 2, 1, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY))
                """, name);
        productId = jdbc.queryForObject("SELECT id FROM app_product WHERE name = ?", Long.class, name);
        // 预置同一用户的已有订单，使新请求在扣库存之后 INSERT 违反唯一约束。
        orders.createPendingOrder(UUID.randomUUID().toString(), 990301L, productId, new BigDecimal("129.00"));
        SeckillMessage task = new SeckillMessage(UUID.randomUUID().toString(),
                990301L, productId, new BigDecimal("129.00"));
        String json = mapper.writeValueAsString(task);
        producer.send(task);

        // 有界等待真正的死信到达；receive 会取走并确认这条测试死信。
        Message dead = rabbit.receive(TEST_QUEUE + ".dead", 10000);
        assertNotNull(dead, "失败消息应在重试耗尽后进入死信队列");
        assertEquals(json, new String(dead.getBody(), StandardCharsets.UTF_8));
        verify(consumer, times(3)).receive(json);
        assertEquals(2, jdbc.queryForObject("SELECT seckill_stock FROM app_product WHERE id = ?",
                Integer.class, productId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM app_order WHERE request_id = ?",
                Integer.class, task.getRequestId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM app_order WHERE product_id = ?",
                Integer.class, productId));
        List<Map<String, ?>> deaths = dead.getMessageProperties().getXDeathHeader();
        assertNotNull(deaths);
        assertEquals("rejected", deaths.get(0).get("reason"));
        assertEquals(TEST_QUEUE, deaths.get(0).get("queue"));
    }

    @AfterEach
    void cleanup() {
        listeners.stop();
        admin.deleteQueue(TEST_QUEUE);
        admin.deleteQueue(TEST_QUEUE + ".dead");
        if (productId != null) {
            jdbc.update("DELETE FROM app_order WHERE product_id = ?", productId);
            jdbc.update("DELETE FROM app_product WHERE id = ?", productId);
        }
    }
}
