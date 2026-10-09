package com.example.flashsale.order;

import com.example.flashsale.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/** 事务下单基础；后续由真实消息消费者调用，目前尚未开放 HTTP 下单接口。 */
@Service
public class OrderService {
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public OrderService(ProductRepository productRepository, OrderRepository orderRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
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
