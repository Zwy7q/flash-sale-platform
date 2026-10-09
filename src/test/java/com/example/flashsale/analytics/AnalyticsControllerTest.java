package com.example.flashsale.analytics;

import com.example.flashsale.auth.JwtTokenService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 独立商品 + 事务回滚；Redis 只清理本测试创建的 Key。 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnalyticsControllerTest {
    private static final String PREFIX = "test:analytics:" + UUID.randomUUID() + ":";
    @DynamicPropertySource
    static void config(DynamicPropertyRegistry registry) {
        registry.add("app.analytics.metrics-key-prefix", () -> PREFIX);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired JwtTokenService jwt;
    @Autowired SeckillMetricsService metrics;
    private Long productId;
    private String url;

    @BeforeEach
    void setup() {
        String name = "统计测试-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_product(name, price, seckill_price, stock, seckill_stock,
                    status, seckill_start_at, seckill_end_at)
                VALUES (?, 299, 59, 100, 7, 1, NOW(), DATE_ADD(NOW(), INTERVAL 1 HOUR))
                """, name);
        productId = jdbc.queryForObject("SELECT id FROM app_product WHERE name=?", Long.class, name);
        url = "/api/admin/analytics/products/" + productId;
        String[] states = {"PENDING_PAYMENT", "PAID", "CANCELLED", "PAYMENT_TIMEOUT"};
        for (int i = 0; i < states.length; i++) {
            jdbc.update("""
                    INSERT INTO app_order(request_id, user_id, product_id, amount, status)
                    VALUES (?, ?, ?, 59, ?)
                    """, UUID.randomUUID().toString(), 998001L + i, productId, states[i]);
        }
    }

    @AfterEach
    void cleanup() {
        redis.delete("flash-sale:stock:" + productId);
        redis.delete(PREFIX + "product:" + productId);
        redis.delete(PREFIX + "all");
        // 不存在商品请求也会生成详情空值缓存，只删除本测试的独立负数 ID。
        redis.delete("flash-sale:product:" + (-productId));
        redis.delete(PREFIX + "product:" + (-productId));
    }

    private String adminToken() { return "Bearer " + jwt.generateToken(1L); }

    @Test
    void shouldAggregateOrdersWithoutMultiplyingStocks() throws Exception {
        redis.opsForValue().set("flash-sale:stock:" + productId, "6");
        redis.opsForHash().putAll(PREFIX + "product:" + productId,
                Map.of("requests", "4", "accepted", "1", "startedAt", "2026-10-09T00:00:00Z"));
        mvc.perform(get(url).header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStock").value(107))
                .andExpect(jsonPath("$.databaseSeckillStock").value(7))
                .andExpect(jsonPath("$.redisSeckillStock").value(6))
                .andExpect(jsonPath("$.orders.createdOrderCount").value(4))
                .andExpect(jsonPath("$.orders.pendingPaymentOrderCount").value(1))
                .andExpect(jsonPath("$.orders.paidOrderCount").value(1))
                .andExpect(jsonPath("$.orders.cancelledOrderCount").value(1))
                .andExpect(jsonPath("$.orders.paymentTimeoutOrderCount").value(1))
                .andExpect(jsonPath("$.orders.paidAmount").value(59))
                .andExpect(jsonPath("$.orders.paymentConversionRatePercent").value(25))
                .andExpect(jsonPath("$.requests.requestCount").value(4))
                .andExpect(jsonPath("$.requests.successRatePercent").value(25));
    }

    @Test
    void shouldShowUnknownStockWithoutWarmingItUp() throws Exception {
        jdbc.update("DELETE FROM app_order WHERE product_id=?", productId);
        mvc.perform(get(url).header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redisStockStatus").value("NOT_WARMED_UP"))
                .andExpect(jsonPath("$.redisSeckillStock").isEmpty())
                .andExpect(jsonPath("$.orders.createdOrderCount").value(0))
                .andExpect(jsonPath("$.orders.paymentConversionRatePercent").value(0))
                .andExpect(jsonPath("$.requests.status").value("NOT_STARTED"));
        assertFalse(Boolean.TRUE.equals(redis.hasKey("flash-sale:stock:" + productId)));
    }

    @Test
    void shouldReportCorruptionAsUnknown() throws Exception {
        redis.opsForValue().set("flash-sale:stock:" + productId, "broken");
        redis.opsForHash().putAll(PREFIX + "product:" + productId,
                Map.of("requests", "1", "accepted", "2", "startedAt", "2026-10-09T00:00:00Z"));
        mvc.perform(get(url).header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redisStockStatus").value("INVALID"))
                .andExpect(jsonPath("$.requests.status").value("INVALID"))
                .andExpect(jsonPath("$.requests.requestCount").isEmpty());
    }

    @Test
    void shouldUseGlobalCounterIncludingUnknownProducts() throws Exception {
        // 不存在商品的请求也进入全局计数，不能靠现有商品计数相加。
        mvc.perform(post("/api/seckill/products/{id}", -productId).header("Authorization", adminToken()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/analytics/overview").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requests.requestCount").value(1))
                .andExpect(jsonPath("$.requests.acceptedRequestCount").value(0));
    }

    @Test
    void shouldEnforceAdminAndReturnListAndMissingDetail() throws Exception {
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(get(url).header("Authorization", "Bearer " + jwt.generateToken(2L)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/analytics/products").header("Authorization", adminToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        mvc.perform(get("/api/admin/analytics/products/-1").header("Authorization", adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldKeepConcurrentGlobalAndProductCountersConsistent() throws Exception {
        // 并发写真实 Redis，确认 HINCRBY 没有因多个线程同时读取再覆盖而丢计数。
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            java.util.List<java.util.concurrent.Future<?>> tasks = new java.util.ArrayList<>();
            for (int i = 0; i < 100; i++) {
                boolean accepted = i % 4 == 0;
                tasks.add(executor.submit(() -> metrics.record(productId, accepted)));
            }
            for (var task : tasks) { task.get(10, java.util.concurrent.TimeUnit.SECONDS); }
        }
        assertEquals(100L, metrics.readRequests(null).getRequestCount());
        assertEquals(25L, metrics.readRequests(productId).getAcceptedRequestCount());
        assertEquals(new java.math.BigDecimal("25.00"), metrics.readRequests(null).getSuccessRatePercent());
    }

    @Test
    void shouldAggregateOverviewAndRoundPercentages() throws Exception {
        Long totalStock = jdbc.queryForObject(
                "SELECT COALESCE(SUM(stock + seckill_stock),0) FROM app_product", Long.class);
        Long orders = jdbc.queryForObject("SELECT COUNT(*) FROM app_order", Long.class);
        redis.opsForHash().putAll(PREFIX + "all",
                Map.of("requests", "6", "accepted", "1", "startedAt", "2026-10-09T00:00:00Z"));
        mvc.perform(get("/api/admin/analytics/overview").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStock").value(totalStock.intValue()))
                .andExpect(jsonPath("$.orders.createdOrderCount").value(orders.intValue()))
                .andExpect(jsonPath("$.redisSeckillStock").isEmpty())
                .andExpect(jsonPath("$.requests.successRatePercent").value(16.67));
    }
}
