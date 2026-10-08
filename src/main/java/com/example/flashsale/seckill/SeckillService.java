package com.example.flashsale.seckill;

import com.example.flashsale.product.Product;
import com.example.flashsale.product.ProductService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 秒杀业务层。
 *
 * 负责组合商品校验、活动时间校验和 Redis Lua 预扣库存。
 * 本阶段只完成“抢购资格预扣”，订单会在 RabbitMQ 阶段异步创建。
 */
@Service
public class SeckillService {

    private final ProductService productService;
    private final StockRedisService stockRedisService;

    public SeckillService(
            ProductService productService,
            StockRedisService stockRedisService) {
        this.productService = productService;
        this.stockRedisService = stockRedisService;
    }

    /**
     * 尝试获得商品的秒杀资格。
     *
     * 用户身份由 Controller 从 JWT 请求属性中获得，再传入这里。
     */
    public SeckillResult seckill(Long productId, Long userId) {
        Optional<Product> productResult = productService.findOnSaleProduct(productId);

        if (productResult.isEmpty()) {
            return SeckillResult.PRODUCT_NOT_FOUND;
        }

        Product product = productResult.get();
        boolean seckillActive = productService.isSeckillActive(product, LocalDateTime.now());

        if (!seckillActive) {
            return SeckillResult.SECKILL_NOT_ACTIVE;
        }

        // 首次抢购时预热库存；内部使用 SETNX，避免并发请求覆盖已扣减的库存。
        stockRedisService.ensureStockWarmUp(productId);

        StockDeductionResult stockResult = stockRedisService.tryDeductStock(productId, userId);

        if (stockResult == StockDeductionResult.SUCCESS) {
            return SeckillResult.SUCCESS;
        }

        if (stockResult == StockDeductionResult.DUPLICATE_PURCHASE) {
            return SeckillResult.DUPLICATE_PURCHASE;
        }

        if (stockResult == StockDeductionResult.OUT_OF_STOCK) {
            return SeckillResult.OUT_OF_STOCK;
        }

        return SeckillResult.STOCK_NOT_WARMED_UP;
    }
}
