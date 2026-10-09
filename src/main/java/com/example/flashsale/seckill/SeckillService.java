package com.example.flashsale.seckill;

import com.example.flashsale.product.Product;
import com.example.flashsale.product.ProductService;
import com.example.flashsale.messaging.SeckillMessage;
import com.example.flashsale.messaging.SeckillMessageProducer;
import com.example.flashsale.messaging.MessagePublishException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 秒杀业务层。
 *
 * 负责组合商品校验、活动时间校验和 Redis Lua 预扣库存。
 * Redis 预扣成功后发送订单任务，消费者异步创建 MySQL 订单。
 */
@Service
public class SeckillService {
    private static final Logger log = LoggerFactory.getLogger(SeckillService.class);

    private final ProductService productService;
    private final StockRedisService stockRedisService;
    private final SeckillMessageProducer producer;

    public SeckillService(
            ProductService productService,
            StockRedisService stockRedisService, SeckillMessageProducer producer) {
        this.productService = productService;
        this.stockRedisService = stockRedisService;
        this.producer = producer;
    }

    /**
     * 尝试获得商品的秒杀资格。
     *
     * 用户身份由 Controller 从 JWT 请求属性中获得，再传入这里。
     */
    public SeckillResponse seckill(Long productId, Long userId) {
        Optional<Product> productResult = productService.findOnSaleProduct(productId);

        if (productResult.isEmpty()) {
            return new SeckillResponse(SeckillResult.PRODUCT_NOT_FOUND, "商品不存在或已下架");
        }

        Product product = productResult.get();
        boolean seckillActive = productService.isSeckillActive(product, LocalDateTime.now());

        if (!seckillActive) {
            return new SeckillResponse(SeckillResult.SECKILL_NOT_ACTIVE, "当前不在秒杀活动时间内");
        }

        // 首次抢购时预热库存；内部使用 SETNX，避免并发请求覆盖已扣减的库存。
        stockRedisService.ensureStockWarmUp(productId);

        StockDeductionResult stockResult = stockRedisService.tryDeductStock(productId, userId);

        if (stockResult == StockDeductionResult.SUCCESS) {
            String requestId = UUID.randomUUID().toString();
            SeckillMessage message = new SeckillMessage(requestId, userId, productId, product.getSeckillPrice());
            try {
                producer.send(message);
            } catch (MessagePublishException exception) {
                // 保存日志中的任务内容，便于基础版人工核对；日志不是可靠任务表的替代品。
                log.error("发送订单任务异常：requestId={} userId={} productId={} amount={} outcome={}",
                        requestId, userId, productId, message.getAmount(), exception.getOutcome(), exception);
                if (exception.getOutcome() == MessagePublishException.Outcome.NOT_ROUTED) {
                    return new SeckillResponse(SeckillResult.PUBLISH_FAILED,
                            "消息未路由到队列，预扣暂保留，请核对后处理", requestId);
                }
                // 消费者可能已经下单，绝不能在这里无条件 INCR 或删除购买标记。
                return new SeckillResponse(SeckillResult.PUBLISH_UNKNOWN,
                        "发送结果未知，请使用请求编号查询订单", requestId);
            }
            return new SeckillResponse(SeckillResult.SUCCESS, "请求已提交，订单正在异步创建", requestId);
        }

        if (stockResult == StockDeductionResult.DUPLICATE_PURCHASE) {
            return new SeckillResponse(SeckillResult.DUPLICATE_PURCHASE, "不能重复抢购同一件商品");
        }

        if (stockResult == StockDeductionResult.OUT_OF_STOCK) {
            return new SeckillResponse(SeckillResult.OUT_OF_STOCK, "商品已售罄");
        }

        return new SeckillResponse(SeckillResult.STOCK_NOT_WARMED_UP, "秒杀库存尚未准备完成");
    }
}
