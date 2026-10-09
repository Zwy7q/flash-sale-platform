package com.example.flashsale.order;

import com.example.flashsale.seckill.StockRedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** 组织取消的两步：先提交 MySQL 事务，再幂等释放 Redis 库存。 */
@Service
public class OrderCancellationService {
    private static final Logger log = LoggerFactory.getLogger(OrderCancellationService.class);
    private final OrderService orderService;
    private final StockRedisService stockRedisService;

    public OrderCancellationService(OrderService orderService, StockRedisService stockRedisService) {
        this.orderService = orderService;
        this.stockRedisService = stockRedisService;
    }

    /**
     * NEVER 表示这里不能包在一个外层事务里，否则内层返回时可能还未真正提交。
     * Controller 调用本方法时没有事务；另一个 Bean 的 cancelMyOrder 会开启自己的事务。
     */
    @Transactional(propagation = Propagation.NEVER)
    public Optional<SeckillOrder> cancel(Long orderId, Long currentUserId) {
        Optional<SeckillOrder> result = orderService.cancelMyOrder(orderId, currentUserId);
        // Spring 的事务代理已经提交了 MySQL；如果提交失败，不会执行到这里。
        if (result.isEmpty()) {
            return Optional.empty();
        }
        SeckillOrder order = result.get();
        try {
            stockRedisService.releaseCancelledOrderStock(order.getProductId(), order.getId());
        } catch (RuntimeException exception) {
            // 此时不能回滚已提交的 MySQL，更不能把订单恢复为待支付。
            log.warn("订单已取消，Redis 库存释放待核对或重试：orderId={} productId={} cause={}",
                    orderId, order.getProductId(), exception.toString());
            throw new OrderStockReleaseException(
                    "订单已取消，Redis 库存释放尚未确认；可重试同一取消请求，库存数据缺失时需人工核对",
                    exception);
        }
        return result;
    }
}
