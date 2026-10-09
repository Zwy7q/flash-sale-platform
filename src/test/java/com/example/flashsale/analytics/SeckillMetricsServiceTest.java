package com.example.flashsale.analytics;

import com.example.flashsale.analytics.AnalyticsModels.ProductStatistics;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 模拟 Redis 断线，不停止用户正在使用的 Docker 容器。 */
class SeckillMetricsServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void shouldKeepUnknownMetricsAndStockWhenRedisIsUnavailable() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(redis.opsForValue()).thenReturn(values);
        when(hashes.entries(anyString())).thenThrow(new RedisConnectionFailureException("测试断线"));
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("测试断线"));
        SeckillMetricsService metrics = new SeckillMetricsService(redis, "test:");
        var requests = metrics.readRequests(null);
        assertEquals("UNAVAILABLE", requests.getStatus());
        assertNull(requests.getRequestCount());
        assertNull(requests.getSuccessRatePercent());
        ProductStatistics product = new ProductStatistics();
        product.setProductId(1L);
        metrics.fillStock(product);
        assertEquals("UNAVAILABLE", product.getRedisStockStatus());
        assertNull(product.getRedisSeckillStock());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNotPropagateRecordingFailureToSeckill() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("测试埋点失败"));
        SeckillMetricsService metrics = new SeckillMetricsService(redis, "test:");
        assertDoesNotThrow(() -> metrics.record(1L, true));
        verify(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }
}
