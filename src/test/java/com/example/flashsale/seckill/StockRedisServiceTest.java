package com.example.flashsale.seckill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import com.example.flashsale.product.ProductRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 秒杀库存服务测试。
 */
@ExtendWith(MockitoExtension.class)
class StockRedisServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ProductRepository productRepository;

    @Test
    void shouldStoreAndReadStock() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("flash-sale:stock:1")).thenReturn("100");

        StockRedisService stockRedisService =
                new StockRedisService(stringRedisTemplate, productRepository);

        stockRedisService.setStock(1L, 100);
        Integer stock = stockRedisService.getStock(1L);

        verify(valueOperations).set("flash-sale:stock:1", "100");
        assertEquals(100, stock);
    }

    @Test
    void shouldWarmUpStockFromDatabase() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(productRepository.findSeckillStockById(1L)).thenReturn(java.util.Optional.of(80));

        StockRedisService stockRedisService =
                new StockRedisService(stringRedisTemplate, productRepository);

        stockRedisService.warmUpStock(1L);

        verify(valueOperations).set("flash-sale:stock:1", "80");
    }

    @Test
    void shouldReturnTrueWhenLuaScriptDeductsStockSuccessfully() {
        when(stringRedisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                org.mockito.ArgumentMatchers.eq(java.util.List.of("flash-sale:stock:1"))))
                .thenReturn(1L);

        StockRedisService stockRedisService =
                new StockRedisService(stringRedisTemplate, productRepository);

        boolean deducted = stockRedisService.tryDeductStock(1L);

        assertTrue(deducted);
    }

    @Test
    void shouldReturnFalseWhenLuaScriptReportsOutOfStock() {
        when(stringRedisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                org.mockito.ArgumentMatchers.eq(java.util.List.of("flash-sale:stock:1"))))
                .thenReturn(0L);

        StockRedisService stockRedisService =
                new StockRedisService(stringRedisTemplate, productRepository);

        boolean deducted = stockRedisService.tryDeductStock(1L);

        assertFalse(deducted);
    }
}
