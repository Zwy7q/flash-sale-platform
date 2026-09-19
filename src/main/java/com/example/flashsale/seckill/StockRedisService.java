package com.example.flashsale.seckill;

import com.example.flashsale.product.ProductRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 秒杀库存 Redis 服务。
 *
 * 这个类只负责库存 Key 的读写，不负责判断活动时间，也不负责创建订单。
 * 这样后续使用 Lua 扣减库存时，职责会比较清晰。
 */
@Service
public class StockRedisService {

    private static final String STOCK_KEY_PREFIX = "flash-sale:stock:";

    /**
     * Lua 原子扣减库存脚本。
     *
     * 返回值约定：
     *  1：库存扣减成功；
     *  0：库存已售罄；
     * -1：Redis 中尚未预热该商品库存。
     */
    private static final DefaultRedisScript<Long> STOCK_DEDUCTION_SCRIPT =
            new DefaultRedisScript<>();

    static {
        STOCK_DEDUCTION_SCRIPT.setScriptText("""
                -- KEYS[1] 是当前商品的秒杀库存 Key。
                local stock = redis.call('GET', KEYS[1])

                -- 库存 Key 不存在时，说明还没有执行库存预热。
                if not stock then
                    return -1
                end

                -- 库存已经为 0 时，不能继续扣减，避免出现负库存。
                if tonumber(stock) <= 0 then
                    return 0
                end

                -- 只有库存充足时才扣减 1，整段 Lua 脚本在 Redis 中原子执行。
                redis.call('DECR', KEYS[1])
                return 1
                """);
        STOCK_DEDUCTION_SCRIPT.setResultType(Long.class);
    }

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

    /**
     * 使用 Lua 脚本尝试预扣一件秒杀库存。
     *
     * 脚本返回 1 时才表示预扣成功；库存不足和未预热都先返回 false。
     * 下一阶段会增加更细的失败原因，例如“该用户已购买过”。
     */
    public boolean tryDeductStock(Long productId) {
        Long scriptResult = stringRedisTemplate.execute(
                STOCK_DEDUCTION_SCRIPT,
                List.of(stockKey(productId)));

        return Long.valueOf(1L).equals(scriptResult);
    }
}
