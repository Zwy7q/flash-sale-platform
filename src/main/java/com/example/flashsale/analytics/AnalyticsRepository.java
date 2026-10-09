package com.example.flashsale.analytics;

import com.example.flashsale.analytics.AnalyticsModels.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** 只查询 MySQL：订单先按商品汇总，避免一件商品有多笔订单导致库存被重复相加。 */
@Repository
public class AnalyticsRepository {
    private final JdbcTemplate jdbc;

    public AnalyticsRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // CASE WHEN 相当于 SQL 中的 if；符合状态的订单贡献 1，否则贡献 0。
    // 没有订单时 SUM 返回 NULL，因此用 COALESCE(..., 0) 转成 0。
    private static final String ORDER_COLUMNS = """
            COUNT(*) AS created_count,
            COALESCE(SUM(CASE WHEN status='PENDING_PAYMENT' THEN 1 ELSE 0 END),0) AS pending_count,
            COALESCE(SUM(CASE WHEN status='PAID' THEN 1 ELSE 0 END),0) AS paid_count,
            COALESCE(SUM(CASE WHEN status='CANCELLED' THEN 1 ELSE 0 END),0) AS cancelled_count,
            COALESCE(SUM(CASE WHEN status='PAYMENT_TIMEOUT' THEN 1 ELSE 0 END),0) AS timeout_count,
            COALESCE(SUM(CASE WHEN status='PAID' THEN amount ELSE 0 END),0) AS paid_amount
            """;

    public List<ProductStatistics> findProducts() {
        return jdbc.query("SELECT id,name,status,stock,seckill_stock FROM app_product ORDER BY id",
                (rs, row) -> {
                    ProductStatistics product = new ProductStatistics();
                    product.setProductId(rs.getLong("id"));
                    product.setName(rs.getString("name"));
                    product.setStatus(rs.getInt("status"));
                    product.setOrdinaryStock(rs.getLong("stock"));
                    product.setDatabaseSeckillStock(rs.getLong("seckill_stock"));
                    product.setTotalStock(product.getOrdinaryStock() + product.getDatabaseSeckillStock());
                    return product;
                });
    }

    public Map<Long, OrderStatistics> findOrdersByProduct() {
        Map<Long, OrderStatistics> result = new HashMap<>();
        jdbc.query("SELECT product_id, " + ORDER_COLUMNS + " FROM app_order GROUP BY product_id",
                (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        result.put(rs.getLong("product_id"), readOrders(rs)));
        return result;
    }

    /** 总览独立汇总整个订单表，即使商品已被删除，历史订单仍计入总数。 */
    public OrderStatistics findAllOrders() {
        return jdbc.queryForObject("SELECT " + ORDER_COLUMNS + " FROM app_order",
                (rs, row) -> readOrders(rs));
    }

    private OrderStatistics readOrders(ResultSet rs) throws SQLException {
        OrderStatistics orders = new OrderStatistics();
        orders.setCreatedOrderCount(rs.getLong("created_count"));
        orders.setPendingPaymentOrderCount(rs.getLong("pending_count"));
        orders.setPaidOrderCount(rs.getLong("paid_count"));
        orders.setCancelledOrderCount(rs.getLong("cancelled_count"));
        orders.setPaymentTimeoutOrderCount(rs.getLong("timeout_count"));
        orders.setPaidAmount(rs.getBigDecimal("paid_amount"));
        return orders;
    }
}
