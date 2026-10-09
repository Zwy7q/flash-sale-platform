package com.example.flashsale.order;

import com.example.flashsale.auth.JwtTokenService;
import com.example.flashsale.seckill.StockRedisService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 使用真实 MySQL 事务和 Redis；不加测试外层事务，才能验证提交后释放库存。 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderCancellationIT {
    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenService jwt;
    @Autowired private OrderService orders;
    @Autowired private OrderRepository repository;
    @Autowired private StockRedisService stock;
    @Autowired private StringRedisTemplate redis;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private OrderTimeoutService timeouts;
    private Long productId;
    private Long orderId;
    private final Long userId = 990701L;
    private String requestId;
    private String token;

    @BeforeEach
    void createIsolatedOrder() {
        String name = "取消测试-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_product (name, price, seckill_price, stock, seckill_stock,
                    status, seckill_start_at, seckill_end_at)
                VALUES (?, 299, 129, 2, 2, 1, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY))
                """, name);
        productId = jdbc.queryForObject("SELECT id FROM app_product WHERE name = ?", Long.class, name);
        requestId = UUID.randomUUID().toString();
        stock.setStock(productId, 2);
        stock.tryDeductStock(productId, userId, requestId);
        orderId = orders.createOrder(requestId, userId, productId, new BigDecimal("129.00"));
        token = "Bearer " + jwt.generateToken(userId);
    }

    @AfterEach
    void removeOnlyThisTestsData() {
        if (orderId != null) {
            redis.delete(releaseKey());
        }
        if (productId != null) {
            redis.delete(List.of(stock.stockKey(productId), stock.purchaseKey(productId, userId)));
            jdbc.update("DELETE FROM app_order WHERE product_id = ?", productId);
            jdbc.update("DELETE FROM app_product WHERE id = ?", productId);
        }
    }

    private String releaseKey() {
        return "flash-sale:stock-release:" + orderId;
    }

    private int databaseStock() {
        return jdbc.queryForObject("SELECT seckill_stock FROM app_product WHERE id = ?",
                Integer.class, productId);
    }

    private OrderStatus statusOfOrder() {
        return repository.findByIdAndUserId(orderId, userId).get().getStatus();
    }

    private MvcResult cancel() throws Exception {
        return mvc.perform(post("/api/orders/{id}/cancel", orderId)
                .header("Authorization", token)).andReturn();
    }

    @Test
    void shouldCancelAndReleaseBothStocksOnlyOnce() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/orders/{id}/cancel", orderId).header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(orderId))
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }
        assertEquals(2, databaseStock());
        assertEquals(2, stock.getStock(productId));
        assertEquals("1", redis.opsForValue().get(releaseKey()));
        // 取消成功后解除旧请求的购买标记，允许同一用户再次抢购。
        assertNull(redis.opsForValue().get(stock.purchaseKey(productId, userId)));
        mvc.perform(post("/api/orders/{id}/pay", orderId).header("Authorization", token))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldProtectCancellationOwnershipAndRequireJwt() throws Exception {
        mvc.perform(post("/api/orders/{id}/cancel", orderId)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/orders/{id}/cancel", orderId)
                .header("Authorization", "Bearer " + jwt.generateToken(userId + 1)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/{id}/cancel", -1L).header("Authorization", token))
                .andExpect(status().isNotFound());
        assertEquals(OrderStatus.PENDING_PAYMENT, statusOfOrder());
        assertEquals(1, databaseStock());
        assertEquals(1, stock.getStock(productId));
    }

    @Test
    void shouldRejectPaidAndTimedOutOrdersWithoutReleasingStock() throws Exception {
        for (String closedStatus : new String[]{"PAID", "PAYMENT_TIMEOUT"}) {
            jdbc.update("UPDATE app_order SET status = ? WHERE id = ?", closedStatus, orderId);
            mvc.perform(post("/api/orders/{id}/cancel", orderId).header("Authorization", token))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
        }
        assertEquals(1, databaseStock());
        assertEquals(1, stock.getStock(productId));
        assertNull(redis.opsForValue().get(releaseKey()));
    }

    @Test
    void shouldRetryRedisReleaseAfterFailureWithoutRepeatingDatabaseRelease() throws Exception {
        // 模拟库存数据损坏：Redis INCR 会失败。取消的 MySQL 事务已经提交。
        redis.opsForValue().set(stock.stockKey(productId), "broken-stock");
        mvc.perform(post("/api/orders/{id}/cancel", orderId).header("Authorization", token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("STOCK_RELEASE_PENDING"));
        assertEquals(OrderStatus.CANCELLED, statusOfOrder());
        assertEquals(2, databaseStock());
        assertNull(redis.opsForValue().get(releaseKey()));
        // 只修复本测试的损坏数据，恢复到释放前的 1，再重试同一取消请求。
        stock.setStock(productId, 1);
        assertEquals(200, cancel().getResponse().getStatus());
        assertEquals(200, cancel().getResponse().getStatus());
        assertEquals(2, databaseStock());
        assertEquals(2, stock.getStock(productId));
    }

    @Test
    void shouldNotRecreateMissingRedisStockBlindly() throws Exception {
        redis.delete(stock.stockKey(productId));
        mvc.perform(post("/api/orders/{id}/cancel", orderId).header("Authorization", token))
                .andExpect(status().isServiceUnavailable());
        assertEquals(OrderStatus.CANCELLED, statusOfOrder());
        assertEquals(2, databaseStock());
        assertNull(stock.getStock(productId));
        assertEquals("RECONCILE", redis.opsForValue().get(releaseKey()));
        // 即使后来有人按已归还的 MySQL 库存重建 Key，也不能再自动 INCR 成 3。
        stock.ensureStockWarmUp(productId);
        assertEquals(503, cancel().getResponse().getStatus());
        assertEquals(2, stock.getStock(productId));
    }

    @Test
    void shouldReleaseOnlyOnceWithConcurrentCancellation() throws Exception {
        runConcurrent(false);
        assertEquals(OrderStatus.CANCELLED, statusOfOrder());
        assertEquals(2, databaseStock());
        assertEquals(2, stock.getStock(productId));
    }

    @Test
    void shouldHaveOnlyOneWinnerBetweenPaymentAndCancellation() throws Exception {
        runConcurrent(true);
        OrderStatus finalStatus = statusOfOrder();
        assertTrue(finalStatus == OrderStatus.PAID || finalStatus == OrderStatus.CANCELLED);
        int expectedStock = finalStatus == OrderStatus.PAID ? 1 : 2;
        assertEquals(expectedStock, databaseStock());
        assertEquals(expectedStock, stock.getStock(productId));
    }

    private void runConcurrent(boolean raceWithPayment) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = pool.submit(() -> {
                start.await();
                return cancel().getResponse().getStatus();
            });
            Future<Integer> second = pool.submit(() -> {
                start.await();
                if (raceWithPayment) {
                    return mvc.perform(post("/api/orders/{id}/pay", orderId)
                            .header("Authorization", token)).andReturn().getResponse().getStatus();
                }
                return cancel().getResponse().getStatus();
            });
            start.countDown();
            int firstStatus = first.get(15, TimeUnit.SECONDS);
            int secondStatus = second.get(15, TimeUnit.SECONDS);
            if (raceWithPayment) {
                assertTrue(firstStatus == 200 && secondStatus == 409
                        || firstStatus == 409 && secondStatus == 200);
            } else {
                assertEquals(200, firstStatus);
                assertEquals(200, secondStatus);
            }
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void shouldKeepCancelledOrderOnDuplicateMessage() throws Exception {
        assertEquals(200, cancel().getResponse().getStatus());
        assertEquals(orderId, orders.createOrder(requestId, userId, productId, new BigDecimal("129.00")));
        assertEquals(OrderStatus.CANCELLED, statusOfOrder());
        assertEquals(2, databaseStock());
    }

    @Test
    void shouldNotDeleteNewPurchaseWhenOldCancellationIsRetried() throws Exception {
        assertEquals(200, cancel().getResponse().getStatus());
        String nextRequest = UUID.randomUUID().toString();
        // 新请求真实执行 Lua 预扣：库存减 1，标记记录新的 requestId。
        assertEquals(com.example.flashsale.seckill.StockDeductionResult.SUCCESS,
                stock.tryDeductStock(productId, userId, nextRequest));
        Long newOrder = orders.createOrder(nextRequest, userId, productId, new BigDecimal("129.00"));
        try {
            assertEquals(200, cancel().getResponse().getStatus());
            assertEquals(nextRequest, redis.opsForValue().get(stock.purchaseKey(productId, userId)));
            assertEquals(1, stock.getStock(productId));
            assertEquals(1, databaseStock());
            assertEquals(OrderStatus.PENDING_PAYMENT,
                    repository.findByIdAndUserId(newOrder, userId).get().getStatus());
            assertEquals(orderId, orders.createOrder(requestId, userId, productId, new BigDecimal("129.00")));
            assertEquals(1, databaseStock());
        } finally {
            redis.delete("flash-sale:stock-release:" + newOrder);
        }
    }

    @Test
    void shouldRejectExpiredPaymentEvenBeforeScheduledScan() throws Exception {
        jdbc.update("UPDATE app_order SET created_at = DATE_SUB(NOW(), INTERVAL 16 MINUTE) WHERE id = ?", orderId);
        mvc.perform(post("/api/orders/{id}/pay", orderId).header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PAYABLE"));
        // 支付拒绝不伪装为已支付；实际超时关闭和归还由定时扫描完成。
        assertEquals(OrderStatus.PENDING_PAYMENT, statusOfOrder());
        assertEquals(1, databaseStock());
    }

    private void makeOrderExpired() {
        jdbc.update("UPDATE app_order SET created_at = DATE_SUB(NOW(), INTERVAL 16 MINUTE) WHERE id = ?", orderId);
    }

    private int releaseCompleted() {
        return jdbc.queryForObject("SELECT redis_stock_released FROM app_order WHERE id = ?", Integer.class, orderId);
    }

    @Test
    void shouldTimeoutThenAllowRepurchaseAndKeepOldHistory() {
        makeOrderExpired();
        timeouts.closeOneExpiredOrder(orderId, userId);
        timeouts.closeOneExpiredOrder(orderId, userId);
        assertEquals(OrderStatus.PAYMENT_TIMEOUT, statusOfOrder());
        assertEquals(2, databaseStock());
        assertEquals(2, stock.getStock(productId));
        assertEquals(1, releaseCompleted());
        assertNull(redis.opsForValue().get(stock.purchaseKey(productId, userId)));

        String newRequest = UUID.randomUUID().toString();
        assertEquals(com.example.flashsale.seckill.StockDeductionResult.SUCCESS,
                stock.tryDeductStock(productId, userId, newRequest));
        Long newId = orders.createOrder(newRequest, userId, productId, new BigDecimal("129.00"));
        // 旧超时任务再执行，不能误删新请求的购买标记或多加库存。
        timeouts.closeOneExpiredOrder(orderId, userId);
        assertEquals(newRequest, redis.opsForValue().get(stock.purchaseKey(productId, userId)));
        assertEquals(1, databaseStock());
        assertEquals(1, stock.getStock(productId));
        assertEquals(OrderStatus.PENDING_PAYMENT, repository.findByIdAndUserId(newId, userId).get().getStatus());
        assertEquals(OrderStatus.PAYMENT_TIMEOUT, statusOfOrder());
    }

    @Test
    void shouldSkipFreshOrAlreadyPaidOrder() {
        timeouts.closeOneExpiredOrder(orderId, userId);
        assertEquals(OrderStatus.PENDING_PAYMENT, statusOfOrder());
        orders.payMyOrder(orderId, userId);
        makeOrderExpired();
        timeouts.closeOneExpiredOrder(orderId, userId);
        assertEquals(OrderStatus.PAID, statusOfOrder());
        assertEquals(1, databaseStock());
        assertEquals(1, stock.getStock(productId));
        assertEquals(0, releaseCompleted());
    }

    @Test
    void shouldNotRestoreAgainWhenConfirmedReleaseMarkerIsLost() throws Exception {
        assertEquals(200, cancel().getResponse().getStatus());
        assertEquals(1, releaseCompleted());
        redis.delete(releaseKey());
        assertEquals(200, cancel().getResponse().getStatus());
        assertEquals(2, stock.getStock(productId));
        assertEquals(2, databaseStock());
    }

    @Test
    void shouldRetryFailedTimeoutReleaseWithoutRepeatingDatabaseRestore() {
        makeOrderExpired();
        redis.opsForValue().set(stock.stockKey(productId), "broken-stock");
        assertThrows(OrderStockReleaseException.class, () -> timeouts.closeOneExpiredOrder(orderId, userId));
        assertEquals(OrderStatus.PAYMENT_TIMEOUT, statusOfOrder());
        assertEquals(2, databaseStock());
        assertEquals(0, releaseCompleted());
        stock.setStock(productId, 1);
        timeouts.closeOneExpiredOrder(orderId, userId);
        assertEquals(2, stock.getStock(productId));
        assertEquals(2, databaseStock());
        assertEquals(1, releaseCompleted());
    }

    @Test
    void shouldHandleConcurrentTimeoutWorkersOnlyOnce() throws Exception {
        makeOrderExpired();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> task = () -> {
            start.await();
            timeouts.closeOneExpiredOrder(orderId, userId);
            return true;
        };
        try {
            Future<Boolean> first = pool.submit(task);
            Future<Boolean> second = pool.submit(task);
            start.countDown();
            assertTrue(first.get(15, TimeUnit.SECONDS));
            assertTrue(second.get(15, TimeUnit.SECONDS));
            assertEquals(OrderStatus.PAYMENT_TIMEOUT, statusOfOrder());
            assertEquals(2, databaseStock());
            assertEquals(2, stock.getStock(productId));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
