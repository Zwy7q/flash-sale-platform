package com.example.flashsale.order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/** 订单数据访问层：只负责 SQL；库存校验、事务和消息幂等由后续业务层组织。 */
@Repository
public class OrderRepository {
    private final JdbcTemplate jdbcTemplate;

    public OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 新建待支付订单，返回 MySQL 生成的主键。每次秒杀购买数量固定为 1。 */
    public Long createPendingOrder(String requestId, Long userId, Long productId,
                                   BigDecimal amount) {
        String sql = """
                INSERT INTO app_order (request_id, user_id, product_id, amount, status)
                VALUES (?, ?, ?, ?, ?)
                """;
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            // RETURN_GENERATED_KEYS 告诉 JDBC：插入后还需要获取自增 ID。
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            // 参数位置从 1 开始，分别填入 SQL 中的五个问号，避免拼接用户输入。
            statement.setString(1, requestId);
            statement.setLong(2, userId);
            statement.setLong(3, productId);
            statement.setBigDecimal(4, amount);
            statement.setString(5, OrderStatus.PENDING_PAYMENT.name());
            return statement;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("创建订单后未获得自增 ID");
        }
        return generatedId.longValue();
    }

    /** 同时限制订单 ID 和所属用户，避免普通用户查询别人的订单。 */
    public Optional<SeckillOrder> findByIdAndUserId(Long orderId, Long userId) {
        String sql = """
                SELECT id, request_id, user_id, product_id, amount, status, created_at, updated_at
                FROM app_order WHERE id = ? AND user_id = ?
                """;
        List<SeckillOrder> orders = jdbcTemplate.query(sql, statement -> {
            statement.setLong(1, orderId);
            statement.setLong(2, userId);
        }, (resultSet, rowNumber) -> {
            // ResultSet 是当前结果行；将数据库列依次转换为 Java 对象字段。
            SeckillOrder order = new SeckillOrder();
            order.setId(resultSet.getLong("id"));
            order.setRequestId(resultSet.getString("request_id"));
            order.setUserId(resultSet.getLong("user_id"));
            order.setProductId(resultSet.getLong("product_id"));
            order.setAmount(resultSet.getBigDecimal("amount"));
            order.setStatus(OrderStatus.valueOf(resultSet.getString("status")));
            order.setCreatedAt(resultSet.getTimestamp("created_at").toLocalDateTime());
            order.setUpdatedAt(resultSet.getTimestamp("updated_at").toLocalDateTime());
            return order;
        });
        // 普通 if 写法：无记录返回空，有记录返回对象。
        if (orders.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(orders.get(0));
    }
}
