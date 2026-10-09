package com.example.flashsale.analytics;

import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

/** 统计接口的数据对象；嵌套类用于把相关返回结构集中在一个文件中。 */
public final class AnalyticsModels {
    private AnalyticsModels() { }

    @Data
    public static class OrderStatistics {
        /** 已创建的历史订单总数，包含取消和超时订单。 */
        private long createdOrderCount;
        private long pendingPaymentOrderCount;
        private long paidOrderCount;
        private long cancelledOrderCount;
        private long paymentTimeoutOrderCount;
        /** 只汇总 PAID 订单中的成交金额快照。 */
        private BigDecimal paidAmount = BigDecimal.ZERO;
        private BigDecimal paymentConversionRatePercent = BigDecimal.ZERO;
    }

    @Data
    public static class RequestStatistics {
        /** AVAILABLE / NOT_STARTED / UNAVAILABLE / INVALID。未知不等于零。 */
        private String status;
        private Long requestCount;
        private Long acceptedRequestCount;
        /** HTTP 202 次数 / 已处理秒杀请求数 × 100，并非最终下单成功率。 */
        private BigDecimal successRatePercent;
        private String collectionStartedAt;
    }

    @Data
    public static class ProductStatistics {
        private Long productId;
        private String name;
        private int status;
        private long ordinaryStock;
        private long databaseSeckillStock;
        /** 当前 MySQL 普通库存 + 当前秒杀库存，不是活动最初的库存。 */
        private long totalStock;
        private Long redisSeckillStock;
        private String redisStockStatus;
        private OrderStatistics orders = new OrderStatistics();
        private RequestStatistics requests;
    }

    @Data
    public static class Overview {
        private long productCount;
        private long onSaleProductCount;
        private long ordinaryStock;
        private long databaseSeckillStock;
        private long totalStock;
        /** 只有所有商品的 Redis 库存已知时才返回总和，否则 null。 */
        private Long redisSeckillStock;
        private long knownRedisStockProductCount;
        private OrderStatistics orders;
        private RequestStatistics requests;
        private Instant generatedAt;
    }
}
