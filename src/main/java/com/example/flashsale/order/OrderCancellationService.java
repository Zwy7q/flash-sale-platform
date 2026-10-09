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
    private final OrderRepository orderRepository;

    public OrderCancellationService(OrderService orderService, StockRedisService stockRedisService,
                                    OrderRepository orderRepository) {
        this.orderService = orderService;
        this.stockRedisService = stockRedisService;
        this.orderRepository = orderRepository;
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
        releaseClosedOrder(order);
        return result;
    }

    /** 取消和超时共用；数据库已提交后才释放，成功后保存可用于重试扫描的完成标志。 */
    @Transactional(propagation = Propagation.NEVER)
    public void releaseClosedOrder(SeckillOrder order) {
        if (order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.PAYMENT_TIMEOUT) {
            throw new IllegalArgumentException("只能释放已取消或已超时订单的库存");
        }
        // 数据库已确认完成时直接返回，即使 Redis 释放标记丢失也不重复加库存。
        if (orderRepository.isRedisStockReleased(order.getId())) {
            return;
        }
        try {
            stockRedisService.releaseClosedOrderStock(order.getProductId(), order.getId(),
                    order.getUserId(), order.getRequestId());
            // 若此 SQL 失败，下一次扫描再次执行 Lua，释放标记能阻止重复加库存。
            orderRepository.markRedisStockReleased(order.getId());
        } catch (RuntimeException exception) {
            // 此时不能回滚已提交的 MySQL，更不能把订单恢复为待支付。
            log.warn("订单已取消，Redis 库存释放待核对或重试：orderId={} productId={} cause={}",
                    order.getId(), order.getProductId(), exception.toString());
            throw new OrderStockReleaseException(
                    "订单已关闭，Redis 库存释放尚未确认；后台会重试，库存数据缺失时需人工核对",
                    exception);
        }
    }
}
