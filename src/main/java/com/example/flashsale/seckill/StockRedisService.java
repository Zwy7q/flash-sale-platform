package com.example.flashsale.seckill;

import com.example.flashsale.product.ProductRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 秒杀库存 Redis 服务。
 *
 * 这个类只负责库存 Key 的读写，不负责判断活动时间，也不负责创建订单。
 * 这样后续使用 Lua 扣减库存时，职责会比较清晰。
 */
@Service
public class StockRedisService {

    private static final String STOCK_KEY_PREFIX = "flash-sale:stock:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ProductRepository productRepository;

    public StockRedisService(
            StringRedisTemplate stringRedisTemplate,
            ProductRepository productRepository) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.productRepository = productRepository;
    }

    /**
     * 构造指定商品的 Redis Key。
     */
    public String stockKey(Long productId) {
        return STOCK_KEY_PREFIX + productId;
    }

    /**
     * 将秒杀库存写入 Redis。
     *
     * 预热库存时调用这个方法，库存不设置 TTL，避免活动期间 Key 自动过期。
     */
    public void setStock(Long productId, Integer stock) {
        stringRedisTemplate.opsForValue()
                .set(stockKey(productId), String.valueOf(stock));
    }

    /**
     * 读取 Redis 中的秒杀库存。
     * Redis 没有这个 Key 时返回 null。
     */
    public Integer getStock(Long productId) {
        String value = stringRedisTemplate.opsForValue().get(stockKey(productId));
        return value == null ? null : Integer.valueOf(value);
    }

    /**
     * 库存预热：应用启动或秒杀活动开始前，
     * 先从 MySQL 读取真实库存，再写入 Redis。
     */
    public void warmUpStock(Long productId) {
        Integer stock = productRepository.findSeckillStockById(productId)
                .orElseThrow(() -> new IllegalArgumentException("商品不存在或未上架：" + productId));

        setStock(productId, stock);
    }
}
