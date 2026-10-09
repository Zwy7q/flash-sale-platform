package com.example.flashsale.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 超时业务层：每笔订单单独事务关闭，失败不阻止其他订单处理。 */
@Service
public class OrderTimeoutService {
    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutService.class);
    private static final int BATCH_SIZE = 100;
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final OrderCancellationService cancellationService;
    private final long timeoutSeconds;
    // 重试扫描按 ID 继续向后走，避免最早一批异常订单一直占满重试批次。
    private long releaseCursor;

    public OrderTimeoutService(OrderService orderService, OrderRepository orderRepository,
                               OrderCancellationService cancellationService,
                               @Value("${app.order.payment-timeout-seconds:900}") long timeoutSeconds) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.cancellationService = cancellationService;
        this.timeoutSeconds = timeoutSeconds;
    }

    /** 单笔超时处理，供扫描调用；此方法不包外层事务，保证数据库先提交。 */
    public void closeOneExpiredOrder(Long orderId, Long userId) {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(timeoutSeconds);
        Optional<SeckillOrder> result = orderService.expireOrder(orderId, userId, cutoff);
        if (result.isPresent()) {
            cancellationService.releaseClosedOrder(result.get());
        }
    }

    /** 定时入口分两部分：关闭新到期订单，以及重试过去未完成的 Redis 释放。 */
    public void closeExpiredOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(timeoutSeconds);
        List<SeckillOrder> candidates = orderRepository.findExpiredPendingOrders(cutoff, BATCH_SIZE);
        for (SeckillOrder candidate : candidates) {
            try {
                closeOneExpiredOrder(candidate.getId(), candidate.getUserId());
            } catch (RuntimeException exception) {
                log.warn("超时关闭或释放待重试：orderId={} cause={}", candidate.getId(), exception.toString());
            }
        }
        retryPendingReleases();
    }

    private void retryPendingReleases() {
        List<SeckillOrder> pending = orderRepository.findClosedOrdersAwaitingRelease(releaseCursor, BATCH_SIZE);
        if (pending.isEmpty()) {
            releaseCursor = 0;
            return;
        }
        for (SeckillOrder reference : pending) {
            releaseCursor = reference.getId();
            try {
                Optional<SeckillOrder> result = orderService.findMyOrder(reference.getId(), reference.getUserId());
                if (result.isPresent()) {
                    cancellationService.releaseClosedOrder(result.get());
                }
            } catch (RuntimeException exception) {
                log.warn("已关闭订单库存释放待重试：orderId={} cause={}", reference.getId(), exception.toString());
            }
        }
        if (pending.size() < BATCH_SIZE) {
            releaseCursor = 0;
        }
    }
}
