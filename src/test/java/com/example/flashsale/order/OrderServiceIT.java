package com.example.flashsale.order;

import com.example.flashsale.product.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** 真实事务测试：不使用测试外层事务，才能观察 Service 提交和回滚的实际结果。 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({OrderService.class, OrderRepository.class, ProductRepository.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderServiceIT {
    @Autowired private OrderService service;
    @Autowired private OrderRepository orders;
    @Autowired private JdbcTemplate jdbc;
    private Long productId;
    private final BigDecimal amount = new BigDecimal("129.00");

    @BeforeEach
    void createIsolatedProduct() {
        String name = "事务测试-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_product (name, price, seckill_price, stock, seckill_stock,
                    status, seckill_start_at, seckill_end_at)
                VALUES (?, 299, 129, 2, 2, 1, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY))
                """, name);
        productId = jdbc.queryForObject("SELECT id FROM app_product WHERE name = ?", Long.class, name);
    }

    @AfterEach
    void removeOnlyThisTestsData() {
        // 测试需要真实提交，所以只删除本轮自动生成商品对应的订单和商品。
        if (productId != null) {
            jdbc.update("DELETE FROM app_order WHERE product_id = ?", productId);
            jdbc.update("DELETE FROM app_product WHERE id = ?", productId);
        }
    }

    private int remainingStock() {
        return jdbc.queryForObject("SELECT seckill_stock FROM app_product WHERE id = ?",
                Integer.class, productId);
    }

    @Test
    void shouldCommitOrderAndStockTogether() {
        Long id = service.createOrder(UUID.randomUUID().toString(), 990101L, productId, amount);
        assertEquals(1, remainingStock());
        assertEquals(OrderStatus.PENDING_PAYMENT, orders.findByIdAndUserId(id, 990101L).get().getStatus());
    }

    @Test
    void shouldReturnSameOrderWithoutDeductingAgain() {
        String requestId = UUID.randomUUID().toString();
        Long first = service.createOrder(requestId, 990101L, productId, amount);
        Long second = service.createOrder(requestId, 990101L, productId, amount);
        assertEquals(first, second);
        assertEquals(1, remainingStock());
        // 相同请求编号却换了用户，不能拿到别人的订单。
        assertThrows(IllegalArgumentException.class, () ->
                service.createOrder(requestId, 990102L, productId, amount));
        assertEquals(1, remainingStock());
    }

    @Test
    void shouldRejectSoldOutWithoutCreatingOrder() {
        jdbc.update("UPDATE app_product SET seckill_stock = 0 WHERE id = ?", productId);
        assertThrows(IllegalStateException.class, () ->
                service.createOrder(UUID.randomUUID().toString(), 990101L, productId, amount));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM app_order WHERE product_id = ?",
                Integer.class, productId));
    }

    @Test
    void shouldRollBackStockWhenInsertFails() {
        // 模拟同一用户已存在订单、新任务违反唯一约束：扣库存后 INSERT 失败。
        orders.createPendingOrder(UUID.randomUUID().toString(), 990101L, productId, amount);
        assertThrows(DuplicateKeyException.class, () ->
                service.createOrder(UUID.randomUUID().toString(), 990101L, productId, amount));
        assertEquals(2, remainingStock());
    }

    @Test
    void shouldCreateOnlyOneOrderWhenTwoUsersCompeteForLastStock() throws Exception {
        jdbc.update("UPDATE app_product SET seckill_stock = 1 WHERE id = ?", productId);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = pool.submit(() -> attempt(start, 990101L));
            Future<Boolean> second = pool.submit(() -> attempt(start, 990102L));
            start.countDown();
            boolean firstSuccess = first.get(10, TimeUnit.SECONDS);
            boolean secondSuccess = second.get(10, TimeUnit.SECONDS);
            assertNotEquals(firstSuccess, secondSuccess);
            assertEquals(0, remainingStock());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM app_order WHERE product_id = ?",
                    Integer.class, productId));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void shouldHandleConcurrentCopiesOfSameTaskOnlyOnce() throws Exception {
        String requestId = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Long> task = () -> {
            start.await();
            return service.createOrder(requestId, 990101L, productId, amount);
        };
        try {
            Future<Long> first = pool.submit(task);
            Future<Long> second = pool.submit(task);
            start.countDown();
            assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, remainingStock());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM app_order WHERE product_id = ?",
                    Integer.class, productId));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private boolean attempt(CountDownLatch start, Long userId) throws InterruptedException {
        start.await();
        try {
            service.createOrder(UUID.randomUUID().toString(), userId, productId, amount);
            return true;
        } catch (IllegalStateException soldOut) {
            return false;
        }
    }
}
