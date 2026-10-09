package com.example.flashsale.order;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 调度测试不扫描开发库；数据库、Redis 真实事务行为由 OrderCancellationIT 覆盖。 */
class OrderTimeoutServiceTest {
    private SeckillOrder reference(long id) {
        SeckillOrder order = new SeckillOrder();
        order.setId(id);
        order.setUserId(990701L);
        order.setStatus(OrderStatus.PAYMENT_TIMEOUT);
        return order;
    }

    @Test
    void shouldContinueAfterFailureAndRetryUnreleasedClosedOrders() {
        OrderService orders = mock(OrderService.class);
        OrderRepository repository = mock(OrderRepository.class);
        OrderCancellationService releases = mock(OrderCancellationService.class);
        OrderTimeoutService service = new OrderTimeoutService(orders, repository, releases, 900);
        SeckillOrder first = reference(1);
        SeckillOrder second = reference(2);
        SeckillOrder retry = reference(3);
        when(repository.findExpiredPendingOrders(any(LocalDateTime.class), eq(100))).thenReturn(List.of(first, second));
        when(orders.expireOrder(eq(1L), eq(990701L), any(LocalDateTime.class)))
                .thenThrow(new IllegalStateException("模拟单笔处理失败"));
        when(orders.expireOrder(eq(2L), eq(990701L), any(LocalDateTime.class))).thenReturn(Optional.of(second));
        when(repository.findClosedOrdersAwaitingRelease(0, 100)).thenReturn(List.of(retry));
        when(orders.findMyOrder(3L, 990701L)).thenReturn(Optional.of(retry));
        service.closeExpiredOrders();
        verify(releases).releaseClosedOrder(second);
        verify(releases).releaseClosedOrder(retry);
    }

    @Test
    void shouldTriggerSchedulerWhenEnabled() {
        OrderTimeoutService service = mock(OrderTimeoutService.class);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "app.order.timeout-enabled", "true", "app.order.timeout-scan-interval-ms", "25")));
            context.registerBean(OrderTimeoutService.class, () -> service);
            context.register(OrderTimeoutScheduler.class);
            context.refresh();
            // 使用真实 Spring 调度线程，但调用的是 mock，不会操作任何用户订单。
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> verify(service, atLeastOnce()).closeExpiredOrders());
        }
    }

    @Test
    void shouldDisableSchedulerForTests() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                    Map.of("app.order.timeout-enabled", "false")));
            context.register(OrderTimeoutScheduler.class);
            context.refresh();
            assertFalse(context.containsBean("orderTimeoutScheduler"));
        }
    }
}
