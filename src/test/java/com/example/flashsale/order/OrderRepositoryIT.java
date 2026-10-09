package com.example.flashsale.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 真实 MySQL 测试；每个方法结束后回滚，不留下测试订单，不操作 Redis。 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OrderRepository.class)
class OrderRepositoryIT {

    @Autowired
    private OrderRepository repository;

    @Test
    void shouldSavePendingOrderAndReadItForOwner() {
        String requestId = UUID.randomUUID().toString();
        Long id = repository.createPendingOrder(requestId, 990001L, 990001L,
                new BigDecimal("129.00"));
        Optional<SeckillOrder> result = repository.findByIdAndUserId(id, 990001L);
        assertTrue(result.isPresent());
        SeckillOrder order = result.get();
        assertEquals(requestId, order.getRequestId());
        assertEquals(990001L, order.getProductId());
        assertEquals(new BigDecimal("129.00"), order.getAmount());
        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertNotNull(order.getCreatedAt());
    }

    @Test
    void shouldHideOrderFromAnotherUser() {
        Long id = repository.createPendingOrder(UUID.randomUUID().toString(),
                990002L, 990002L, new BigDecimal("129.00"));
        assertTrue(repository.findByIdAndUserId(id, 990003L).isEmpty());
        assertTrue(repository.findByIdAndUserId(-1L, 990002L).isEmpty());
    }

    @Test
    void shouldRejectRepeatedRequestId() {
        String requestId = UUID.randomUUID().toString();
        repository.createPendingOrder(requestId, 990004L, 990004L, new BigDecimal("129.00"));
        assertThrows(DuplicateKeyException.class, () ->
                repository.createPendingOrder(requestId, 990005L, 990005L,
                        new BigDecimal("129.00")));
    }

    @Test
    void shouldRejectRepeatedPurchaseWithDifferentRequestId() {
        repository.createPendingOrder(UUID.randomUUID().toString(),
                990006L, 990006L, new BigDecimal("129.00"));
        assertThrows(DuplicateKeyException.class, () ->
                repository.createPendingOrder(UUID.randomUUID().toString(),
                        990006L, 990006L, new BigDecimal("129.00")));
    }

    @Test
    void shouldKeepClosedHistoryAndAllowOnlyOneNewActiveOrder() {
        Long first = repository.createPendingOrder(UUID.randomUUID().toString(),
                990007L, 990007L, new BigDecimal("129.00"));
        repository.markCancelled(first, 990007L);
        Long second = repository.createPendingOrder(UUID.randomUUID().toString(),
                990007L, 990007L, new BigDecimal("129.00"));
        assertNotEquals(first, second);
        assertEquals(2, repository.findAllByUserId(990007L).size());
        assertEquals(OrderStatus.CANCELLED, repository.findByIdAndUserId(first, 990007L).get().getStatus());
        assertThrows(DuplicateKeyException.class, () -> repository.createPendingOrder(
                UUID.randomUUID().toString(), 990007L, 990007L, new BigDecimal("129.00")));
    }
}
