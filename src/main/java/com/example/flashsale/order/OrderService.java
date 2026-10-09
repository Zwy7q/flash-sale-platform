package com.example.flashsale.order;

import com.example.flashsale.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;

/** 订单业务层：消费者事务下单，以及当前登录用户的订单查询。 */
@Service
public class OrderService {
    /**
     * 只负责取消的 MySQL 部分：状态变更和归还库存必须一起提交或一起回滚。
     * Redis 释放由 OrderCancellationService 在本方法事务提交后执行。
     */
    @Transactional
    public Optional<SeckillOrder> cancelMyOrder(Long orderId, Long currentUserId) {
        return closePendingOrder(orderId, currentUserId, OrderStatus.CANCELLED, null);
    }

    /** 定时任务使用的内部方法；必须再次锁行校验，不能直接相信扫描时看到的状态。 */
    @Transactional
    public Optional<SeckillOrder> expireOrder(Long orderId, Long userId, LocalDateTime cutoff) {
        return closePendingOrder(orderId, userId, OrderStatus.PAYMENT_TIMEOUT, cutoff);
    }

    /** 两个入口共用锁顺序、状态更新和归还事务，避免取消和超时各写一套库存逻辑。 */
    private Optional<SeckillOrder> closePendingOrder(Long orderId, Long currentUserId,
                                                    OrderStatus target, LocalDateTime cutoff) {
        // 先确认归属并取得商品 ID，此时尚不锁订单，也不依据这次读取判断最终状态。
        Optional<SeckillOrder> initialResult = orderRepository.findByIdAndUserId(orderId, currentUserId);
        if (initialResult.isEmpty()) {
            return Optional.empty();
        }
        Long productId = initialResult.get().getProductId();
        // 与消费者下单保持“先商品，后订单”的加锁顺序，减少互相等待导致的死锁。
        if (!productRepository.lockForOrder(productId)) {
            throw new IllegalStateException("取消订单时对应商品不存在，请核对数据");
        }
        Optional<SeckillOrder> latestResult = orderRepository.findByIdAndUserIdForUpdate(orderId, currentUserId);
        if (latestResult.isEmpty()) {
            return Optional.empty();
        }
        SeckillOrder order = latestResult.get();
        if (order.getStatus() == target) {
            // 重复取消不再次归还 MySQL 库存；外层仍可重试未完成的 Redis 释放。
            return latestResult;
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            if (target == OrderStatus.PAYMENT_TIMEOUT) {
                // 扫描后用户可能已经支付或取消；任务跳过，不覆盖终态。
                return Optional.empty();
            }
            throw new OrderNotCancellableException("只有待支付订单才能取消");
        }
        if (target == OrderStatus.PAYMENT_TIMEOUT && order.getCreatedAt().isAfter(cutoff)) {
            return Optional.empty();
        }
        int affectedRows;
        if (target == OrderStatus.CANCELLED) {
            affectedRows = orderRepository.markCancelled(orderId, currentUserId);
        } else {
            affectedRows = orderRepository.markTimedOut(orderId, currentUserId, cutoff);
        }
        if (affectedRows != 1) {
            throw new OrderNotCancellableException("订单状态已变化，不能取消");
        }
        if (!productRepository.restoreSeckillStock(productId)) {
            // 运行时异常回滚刚才的 CANCELLED 状态，避免只取消却没有归还 MySQL 库存。
            throw new IllegalStateException("归还 MySQL 秒杀库存失败");
        }
        return orderRepository.findByIdAndUserIdForUpdate(orderId, currentUserId);
    }

    /** 模拟支付：只推进订单状态，不调用第三方支付，也不再次扣减库存。 */
    @Transactional
    public Optional<SeckillOrder> payMyOrder(Long orderId, Long currentUserId) {
        // 锁定“属于当前用户”的订单，锁一直保持到整个事务提交或回滚。
        Optional<SeckillOrder> result = orderRepository.findByIdAndUserIdForUpdate(orderId, currentUserId);
        if (result.isEmpty()) {
            return Optional.empty();
        }
        SeckillOrder order = result.get();
        if (order.getStatus() == OrderStatus.PAID) {
            // 幂等：客户端重试或重复点击时返回原订单，不重复执行支付更新。
            return result;
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new OrderNotPayableException("订单已关闭，不能支付");
        }
        // 即使定时任务尚未扫描到这笔订单，超过支付期限也不能继续支付。
        LocalDateTime deadline = order.getCreatedAt().plusSeconds(paymentTimeoutSeconds);
        if (!LocalDateTime.now().isBefore(deadline)) {
            throw new OrderNotPayableException("订单已超过支付期限，等待系统关闭");
        }
        int affectedRows = orderRepository.markPaid(orderId, currentUserId);
        if (affectedRows != 1) {
            // 运行时异常触发事务回滚，不能向客户端报告支付成功。
            throw new OrderNotPayableException("订单状态已变化，不能支付");
        }
        // 重新查询，返回数据库实际状态和 updated_at，而不是自行编造更新时间。
        return orderRepository.findByIdAndUserIdForUpdate(orderId, currentUserId);
    }

    /** 当前阶段采用简单列表，后续订单量增大再增加分页。 */
    public List<SeckillOrder> listMyOrders(Long currentUserId) {
        return orderRepository.findAllByUserId(currentUserId);
    }

    /** 必须将用户条件继续传到 Repository，不能只按订单 ID 查。 */
    public Optional<SeckillOrder> findMyOrder(Long orderId, Long currentUserId) {
        return orderRepository.findByIdAndUserId(orderId, currentUserId);
    }
    /** 当前用户按请求编号查询订单，保持 Controller → Service → Repository 分层。 */
    public Optional<SeckillOrder> findByRequestIdAndUserId(String requestId, Long userId) {
        return orderRepository.findByRequestIdAndUserId(requestId, userId);
    }
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final long paymentTimeoutSeconds;

    public OrderService(ProductRepository productRepository, OrderRepository orderRepository,
                        @Value("${app.order.payment-timeout-seconds:900}") long paymentTimeoutSeconds) {
        if (paymentTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("支付超时秒数必须大于 0");
        }
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.paymentTimeoutSeconds = paymentTimeoutSeconds;
    }

    /**
     * 参数由可信任务提供：amount 是请求获得资格时的成交价快照，不能由客户端决定。
     * requestId 在重试时必须保持不变。
     * @Transactional 让扣库存和 INSERT 共用事务；运行时异常会触发回滚。
     */
    @Transactional
    public Long createOrder(String requestId, Long userId, Long productId, BigDecimal amount) {
        if (requestId == null || requestId.isBlank() || requestId.length() > 64) {
            throw new IllegalArgumentException("请求编号不能为空且不能超过 64 字符");
        }
        if (userId == null || userId <= 0 || productId == null || productId <= 0) {
            throw new IllegalArgumentException("用户 ID 和商品 ID 必须为正数");
        }
        if (amount == null || amount.signum() < 0 || amount.scale() > 2
                || amount.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new IllegalArgumentException("金额必须符合 DECIMAL(10,2) 的非负金额范围");
        }

        // 先锁商品行：同一商品的并发订单串行处理，事务提交/回滚时自动释放锁。
        if (!productRepository.lockForOrder(productId)) {
            throw new IllegalStateException("订单对应商品不存在");
        }

        Optional<SeckillOrder> existingResult = orderRepository.findByRequestIdForUpdate(requestId);
        if (existingResult.isPresent()) {
            SeckillOrder existing = existingResult.get();
            // 相同编号必须对应相同任务，不能用别人的编号冒领订单。
            if (!existing.getUserId().equals(userId) || !existing.getProductId().equals(productId)
                    || existing.getAmount().compareTo(amount) != 0) {
                throw new IllegalArgumentException("请求编号与原订单信息不一致");
            }
            // 幂等：已成功处理过的任务返回原 ID，不再次扣减库存。
            return existing.getId();
        }

        if (!productRepository.deductSeckillStock(productId)) {
            throw new IllegalStateException("MySQL 秒杀库存不足");
        }

        // 不吞掉 INSERT 异常：让它传播到事务代理，回滚前面的库存扣减。
        return orderRepository.createPendingOrder(requestId, userId, productId, amount);
    }
}
