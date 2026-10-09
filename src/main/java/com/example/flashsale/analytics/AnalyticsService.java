package com.example.flashsale.analytics;

import com.example.flashsale.analytics.AnalyticsModels.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 组合 MySQL 持久化统计和 Redis 实时统计；不是跨系统的同一时刻快照。 */
@Service
public class AnalyticsService {
    private final AnalyticsRepository repository;
    private final SeckillMetricsService metrics;
    public AnalyticsService(AnalyticsRepository repository, SeckillMetricsService metrics) {
        this.repository = repository;
        this.metrics = metrics;
    }

    public List<ProductStatistics> products() {
        List<ProductStatistics> products = repository.findProducts();
        Map<Long, OrderStatistics> orderMap = repository.findOrdersByProduct();
        for (ProductStatistics product : products) {
            OrderStatistics orders = orderMap.get(product.getProductId());
            if (orders != null) { product.setOrders(orders); }
            calculatePaymentRate(product.getOrders());
            metrics.fillStock(product);
            product.setRequests(metrics.readRequests(product.getProductId()));
        }
        return products;
    }

    public Optional<ProductStatistics> product(Long id) {
        // 当前是小型演示项目，复用批量汇总；大量商品时应改为按 ID 查询及分页。
        for (ProductStatistics product : products()) {
            if (product.getProductId().equals(id)) { return Optional.of(product); }
        }
        return Optional.empty();
    }

    public Overview overview() {
        Overview result = new Overview();
        // 总览不需要逐商品读取请求计数，只读取库存和全局计数。
        List<ProductStatistics> products = repository.findProducts();
        long redisTotal = 0;
        for (ProductStatistics product : products) {
            result.setProductCount(result.getProductCount() + 1);
            if (product.getStatus() == 1) {
                result.setOnSaleProductCount(result.getOnSaleProductCount() + 1);
            }
            result.setOrdinaryStock(result.getOrdinaryStock() + product.getOrdinaryStock());
            result.setDatabaseSeckillStock(result.getDatabaseSeckillStock() + product.getDatabaseSeckillStock());
            metrics.fillStock(product);
            if (product.getRedisSeckillStock() != null) {
                redisTotal += product.getRedisSeckillStock();
                result.setKnownRedisStockProductCount(result.getKnownRedisStockProductCount() + 1);
            }
        }
        result.setTotalStock(result.getOrdinaryStock() + result.getDatabaseSeckillStock());
        // 有任何未知库存，就不能把已知的部分和冒充全部库存。
        if (result.getKnownRedisStockProductCount() == result.getProductCount()) {
            result.setRedisSeckillStock(redisTotal);
        }
        OrderStatistics orders = repository.findAllOrders();
        calculatePaymentRate(orders);
        result.setOrders(orders);
        result.setRequests(metrics.readRequests(null));
        result.setGeneratedAt(Instant.now());
        return result;
    }

    private void calculatePaymentRate(OrderStatistics orders) {
        orders.setPaymentConversionRatePercent(percent(orders.getPaidOrderCount(), orders.getCreatedOrderCount()));
    }

    /** 乘 100 表示百分数；分母为零时返回 0.00，保留两位小数，四舍五入。 */
    public static BigDecimal percent(long numerator, long denominator) {
        if (denominator == 0) { return new BigDecimal("0.00"); }
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
}
