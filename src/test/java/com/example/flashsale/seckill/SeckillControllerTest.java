package com.example.flashsale.seckill;

import com.example.flashsale.auth.JwtTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Duration;
import java.util.UUID;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** JWT → Redis → RabbitMQ → MySQL → 结果查询，使用本轮独立商品与队列。 */
@SpringBootTest
@AutoConfigureMockMvc
class SeckillControllerTest {
    private static final String QUEUE = "flash-sale.test.http." + UUID.randomUUID();
    private static final Long USER_ID = 990001L;
    @DynamicPropertySource
    static void queue(DynamicPropertyRegistry registry) {
        registry.add("app.mq.order-queue", () -> QUEUE);
    }
    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenService jwt;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper mapper;
    @Autowired private RabbitAdmin admin;
    @Autowired private RabbitListenerEndpointRegistry listeners;
    private Long productId;

    @BeforeEach
    void setup() {
        admin.initialize();
        listeners.start();
        String name = "HTTP秒杀测试-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_product (name, price, seckill_price, stock, seckill_stock,
                   status, seckill_start_at, seckill_end_at)
                VALUES (?, 299, 129, 2, 2, 1, DATE_SUB(NOW(), INTERVAL 1 HOUR),
                   DATE_ADD(NOW(), INTERVAL 1 HOUR))
                """, name);
        productId = jdbc.queryForObject("SELECT id FROM app_product WHERE name = ?", Long.class, name);
    }

    @AfterEach
    void cleanup() {
        listeners.stop(); // 等待消费者完成，避免删商品后仍有消费事务。
        admin.deleteQueue(QUEUE);
        admin.deleteQueue(QUEUE + ".dead");
        if (productId != null) {
            // 本测试新增取消/重购场景，一并清理仅属于测试订单的释放标记。
            java.util.List<Long> ids = jdbc.query("SELECT id FROM app_order WHERE product_id = ?",
                    (resultSet, rowNumber) -> resultSet.getLong("id"), productId);
            for (Long id : ids) {
                redis.delete("flash-sale:stock-release:" + id);
            }
            jdbc.update("DELETE FROM app_order WHERE product_id = ?", productId);
            jdbc.update("DELETE FROM app_product WHERE id = ?", productId);
            redis.delete("flash-sale:stock:" + productId);
            redis.delete("flash-sale:purchase:" + productId + ":" + USER_ID);
            redis.delete("flash-sale:product:" + productId);
        }
    }

    private String submit() throws Exception {
        String json = mvc.perform(post("/api/seckill/products/{id}", productId)
                .header("Authorization", "Bearer " + jwt.generateToken(USER_ID)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("requestId").asText();
    }

    @Test
    void shouldCreateNewOrderThroughMqAfterCancellation() throws Exception {
        String firstRequest = submit();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM app_order WHERE request_id = ?",
                        Integer.class, firstRequest)));
        Long firstId = jdbc.queryForObject("SELECT id FROM app_order WHERE request_id = ?", Long.class, firstRequest);
        mvc.perform(post("/api/orders/{id}/cancel", firstId)
                .header("Authorization", "Bearer " + jwt.generateToken(USER_ID)))
                .andExpect(status().isOk());
        String nextRequest = submit();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM app_order WHERE request_id = ?",
                        Integer.class, nextRequest)));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM app_order WHERE product_id = ?",
                Integer.class, productId));
        assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM app_order WHERE id = ?", String.class, firstId));
        assertEquals(nextRequest, redis.opsForValue().get("flash-sale:purchase:" + productId + ":" + USER_ID));
        // 重试旧取消接口，库存和新购买标记必须保持不变。
        mvc.perform(post("/api/orders/{id}/cancel", firstId)
                .header("Authorization", "Bearer " + jwt.generateToken(USER_ID)))
                .andExpect(status().isOk());
        assertEquals("1", redis.opsForValue().get("flash-sale:stock:" + productId));
        assertEquals(nextRequest, redis.opsForValue().get("flash-sale:purchase:" + productId + ":" + USER_ID));
    }

    @Test
    void shouldCreateOrderThroughFullSeckillChainAndProtectResult() throws Exception {
        String requestId = submit();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            mvc.perform(get("/api/seckill/requests/{id}", requestId)
                    .header("Authorization", "Bearer " + jwt.generateToken(USER_ID)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.requestId").value(requestId))
                    .andExpect(jsonPath("$.status").value("PENDING_PAYMENT")));
        assertEquals(1, jdbc.queryForObject("SELECT seckill_stock FROM app_product WHERE id = ?",
                Integer.class, productId));
        assertEquals("1", redis.opsForValue().get("flash-sale:stock:" + productId));
        mvc.perform(get("/api/seckill/requests/{id}", requestId)
                .header("Authorization", "Bearer " + jwt.generateToken(USER_ID + 1)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.result").value("RESULT_UNKNOWN"));
        mvc.perform(get("/api/seckill/requests/{id}", requestId)).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectRepeatedSeckillRequestFromSameUser() throws Exception {
        submit();
        mvc.perform(post("/api/seckill/products/{id}", productId)
                .header("Authorization", "Bearer " + jwt.generateToken(USER_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.result").value("DUPLICATE_PURCHASE"));
    }

    @Test
    void shouldRequireJwt() throws Exception {
        mvc.perform(post("/api/seckill/products/{id}", productId)).andExpect(status().isUnauthorized());
    }
}
