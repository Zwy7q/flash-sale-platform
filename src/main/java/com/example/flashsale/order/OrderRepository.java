package com.example.flashsale.order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

/** 订单数据访问层：只负责 SQL；库存校验、事务和消息幂等由后续业务层组织。 */
@Repository
public class OrderRepository {
    /** 只查当前用户订单；同一秒创建的订单再按主键倒序，确保顺序稳定。 */
    public List<SeckillOrder> findAllByUserId(Long userId) {
        String sql = """
                SELECT id, request_id, user_id, product_id, amount, status, created_at, updated_at
                FROM app_order WHERE user_id = ?
                ORDER BY created_at DESC, id DESC
                """;
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> {
            // 一行数据库结果转换成一个订单对象；不是重新查询当前商品价格。
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
        }, userId); // 将 userId 绑定到 SQL 问号，用户隔离在数据库查询时完成。
    }
    /** 按任务编号查询当前用户的订单，供秒杀结果轮询使用。 */
    public Optional<SeckillOrder> findByRequestIdAndUserId(String requestId, Long userId) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM app_order WHERE request_id = ? AND user_id = ?",
                (resultSet, rowNumber) -> resultSet.getLong("id"), requestId, userId);
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return findByIdAndUserId(ids.get(0), userId);
    }
    /**
     * 消费任务时按请求编号查询已创建订单；FOR UPDATE 使用当前读。
     * 必须在事务内调用，避免 MySQL 可重复读下使用旧快照判断重复任务。
     * 只返回幂等检查需要的字段，不作为对外查询接口。
     */
    public Optional<SeckillOrder> findByRequestIdForUpdate(String requestId) {
        List<SeckillOrder> orders = jdbcTemplate.query("""
                SELECT id, user_id, product_id, amount FROM app_order
                WHERE request_id = ? FOR UPDATE
                """, (resultSet, rowNumber) -> {
            SeckillOrder order = new SeckillOrder();
            order.setId(resultSet.getLong("id"));
            order.setUserId(resultSet.getLong("user_id"));
            order.setProductId(resultSet.getLong("product_id"));
            order.setAmount(resultSet.getBigDecimal("amount"));
            return order;
        }, requestId);
        if (orders.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(orders.get(0));
    }

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
        return queryOwnedOrder(orderId, userId, false);
    }

    /** 支付事务使用当前读并锁住这一行，其他修改同一订单的事务需要等待。 */
    public Optional<SeckillOrder> findByIdAndUserIdForUpdate(Long orderId, Long userId) {
        return queryOwnedOrder(orderId, userId, true);
    }

    /** 只允许待支付变成已支付；再次用 SQL 条件防止覆盖其他终态。 */
    public int markPaid(Long orderId, Long userId) {
        return jdbcTemplate.update("""
                UPDATE app_order SET status = 'PAID'
                WHERE id = ? AND user_id = ? AND status = 'PENDING_PAYMENT'
                """, orderId, userId);
    }

    /** 只有待支付状态能取消；受影响行数为 1 才表示这次真正完成状态变更。 */
    public int markCancelled(Long orderId, Long userId) {
        return jdbcTemplate.update("""
                UPDATE app_order SET status = 'CANCELLED'
                WHERE id = ? AND user_id = ? AND status = 'PENDING_PAYMENT'
                """, orderId, userId);
    }

    /** Lua 归还成功后再写数据库完成标志，供定时任务查找尚未完成的释放。 */
    public boolean isRedisStockReleased(Long orderId) {
        Integer completed = jdbcTemplate.queryForObject(
                "SELECT redis_stock_released FROM app_order WHERE id = ?", Integer.class, orderId);
        return Integer.valueOf(1).equals(completed);
    }

    /** 成功标志只从 0 改成 1，不允许重复操作把它改回未完成。 */
    public void markRedisStockReleased(Long orderId) {
        jdbcTemplate.update("""
                UPDATE app_order SET redis_stock_released = 1
                WHERE id = ? AND status IN ('CANCELLED', 'PAYMENT_TIMEOUT')
                """, orderId);
    }

    /** 扫描只拿候选 ID 和用户 ID；是否真的关闭仍由事务中最新状态决定。 */
    public List<SeckillOrder> findExpiredPendingOrders(LocalDateTime cutoff, int limit) {
        return jdbcTemplate.query("""
                SELECT id, user_id FROM app_order
                WHERE status = 'PENDING_PAYMENT' AND created_at <= ?
                ORDER BY created_at, id LIMIT ?
                """, (resultSet, rowNumber) -> {
            SeckillOrder reference = new SeckillOrder();
            reference.setId(resultSet.getLong("id"));
            reference.setUserId(resultSet.getLong("user_id"));
            return reference;
        }, cutoff, limit);
    }

    /** 已关闭但 Redis 未确认完成的订单，不会因进程重启而丢失重试线索。 */
    public List<SeckillOrder> findClosedOrdersAwaitingRelease(long afterId, int limit) {
        return jdbcTemplate.query("""
                SELECT id, user_id FROM app_order
                WHERE status IN ('CANCELLED', 'PAYMENT_TIMEOUT')
                  AND redis_stock_released = 0 AND id > ?
                ORDER BY id LIMIT ?
                """, (resultSet, rowNumber) -> {
            SeckillOrder reference = new SeckillOrder();
            reference.setId(resultSet.getLong("id"));
            reference.setUserId(resultSet.getLong("user_id"));
            return reference;
        }, afterId, limit);
    }

    /** SQL 再限制原状态和到期时间，不覆盖已支付订单。 */
    public int markTimedOut(Long orderId, Long userId, LocalDateTime cutoff) {
        return jdbcTemplate.update("""
                UPDATE app_order SET status = 'PAYMENT_TIMEOUT'
                WHERE id = ? AND user_id = ? AND status = 'PENDING_PAYMENT' AND created_at <= ?
                """, orderId, userId, cutoff);
    }

    /** 普通查询和加锁查询共用同一套行映射，避免复制所有字段转换代码。 */
    private Optional<SeckillOrder> queryOwnedOrder(Long orderId, Long userId, boolean lock) {
        String sql = """
                SELECT id, request_id, user_id, product_id, amount, status, created_at, updated_at
                FROM app_order WHERE id = ? AND user_id = ?
                """;
        if (lock) {
            // 该后缀由程序内部决定，不拼接用户输入；必须在 Service 事务内使用。
            sql = sql + " FOR UPDATE";
        }
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
