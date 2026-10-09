package com.example.flashsale.analytics;

import com.example.flashsale.analytics.AnalyticsModels.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Redis 统计与库存只读查询；统计失败不改变秒杀业务结果。 */
@Service
public class SeckillMetricsService {
    private static final Logger log = LoggerFactory.getLogger(SeckillMetricsService.class);
    private static final DefaultRedisScript<Long> COUNTER_SCRIPT = new DefaultRedisScript<>();
    static {
        COUNTER_SCRIPT.setLocation(new ClassPathResource("redis/record_seckill_metrics.lua"));
        COUNTER_SCRIPT.setResultType(Long.class);
    }
    private final StringRedisTemplate redis;
    private final String prefix;

    public SeckillMetricsService(StringRedisTemplate redis,
            @Value("${app.analytics.metrics-key-prefix:flash-sale:metrics:seckill:}") String prefix) {
        this.redis = redis;
        this.prefix = prefix;
    }

    /** accepted 仅代表接口返回排队成功，不代表消费者已经创建订单。 */
    public void record(Long productId, boolean accepted) {
        try {
            String increment = "0";
            if (accepted) { increment = "1"; }
            redis.execute(COUNTER_SCRIPT, List.of(prefix + "all", prefix + "product:" + productId),
                    increment, Instant.now().toString());
        } catch (RuntimeException exception) {
            // 埋点是辅助能力。Redis 故障时不能把已经入队的成功响应改成失败。
            log.warn("秒杀统计记录失败，productId={}", productId, exception);
        }
    }

    public RequestStatistics readRequests(Long productId) {
        String key = prefix + "all";
        if (productId != null) { key = prefix + "product:" + productId; }
        RequestStatistics result = new RequestStatistics();
        Map<Object, Object> values;
        try {
            values = redis.opsForHash().entries(key);
        } catch (org.springframework.data.redis.RedisSystemException exception) {
            result.setStatus("INVALID");
            return result;
        } catch (RuntimeException exception) {
            result.setStatus("UNAVAILABLE");
            return result;
        }
        if (values.isEmpty()) {
            result.setStatus("NOT_STARTED");
            result.setRequestCount(0L);
            result.setAcceptedRequestCount(0L);
            result.setSuccessRatePercent(new BigDecimal("0.00"));
            return result;
        }
        try {
            long requests = Long.parseLong((String) values.get("requests"));
            long accepted = Long.parseLong((String) values.get("accepted"));
            String startedAt = (String) values.get("startedAt");
            Instant.parse(startedAt);
            if (requests < 0 || accepted < 0 || accepted > requests) {
                throw new IllegalArgumentException("统计计数异常");
            }
            result.setStatus("AVAILABLE");
            result.setRequestCount(requests);
            result.setAcceptedRequestCount(accepted);
            result.setSuccessRatePercent(AnalyticsService.percent(accepted, requests));
            result.setCollectionStartedAt(startedAt);
        } catch (RuntimeException exception) {
            result.setStatus("INVALID");
        }
        return result;
    }

    /** 只 GET，不调用库存预热。观察库存不应改变库存。 */
    public void fillStock(ProductStatistics product) {
        String stock;
        try {
            stock = redis.opsForValue().get("flash-sale:stock:" + product.getProductId());
        } catch (org.springframework.data.redis.RedisSystemException exception) {
            product.setRedisStockStatus("INVALID");
            return;
        } catch (RuntimeException exception) {
            product.setRedisStockStatus("UNAVAILABLE");
            return;
        }
        if (stock == null) {
            product.setRedisStockStatus("NOT_WARMED_UP");
            return;
        }
        try {
            long remaining = Long.parseLong(stock);
            if (remaining < 0) { throw new IllegalArgumentException("库存不能为负"); }
            product.setRedisSeckillStock(remaining);
            product.setRedisStockStatus("AVAILABLE");
        } catch (RuntimeException exception) {
            product.setRedisStockStatus("INVALID");
        }
    }
}
