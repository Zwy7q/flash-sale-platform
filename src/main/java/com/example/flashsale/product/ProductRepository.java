package com.example.flashsale.product;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 商品数据访问层。
 *
 * Repository 的职责是负责和数据库交互，不处理 HTTP 请求，也不编写业务规则。
 */
@Repository
public class ProductRepository {


    private final JdbcTemplate jdbcTemplate;

    public ProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询所有上架商品，并按主键升序返回。
     */
    public List<Product> findAllOnSale() {
        String sql = """
                SELECT id, name, description, price, seckill_price,
                       stock, seckill_stock, status,
                       seckill_start_at, seckill_end_at
                FROM app_product
                WHERE status = 1
                ORDER BY id
                """;

        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> {
            Product product = new Product();
            product.setId(resultSet.getLong("id"));
            product.setName(resultSet.getString("name"));
            product.setDescription(resultSet.getString("description"));
            product.setPrice(resultSet.getBigDecimal("price"));
            product.setSeckillPrice(resultSet.getBigDecimal("seckill_price"));
            product.setStock(resultSet.getInt("stock"));
            product.setSeckillStock(resultSet.getInt("seckill_stock"));
            product.setStatus(resultSet.getInt("status"));
            product.setSeckillStartAt(resultSet.getTimestamp("seckill_start_at").toLocalDateTime());
            product.setSeckillEndAt(resultSet.getTimestamp("seckill_end_at").toLocalDateTime());
            return product;
        });
    }

    /**
     * 查询指定商品的秒杀库存。
     *
     * 这里只返回库存字段，不需要把整行商品数据都查出来，减少数据库和 Java 对象的开销。
     */
    public Optional<Integer> findSeckillStockById(Long productId) {
        String sql = """
                SELECT seckill_stock
                FROM app_product
                WHERE id = ? AND status = 1
                """;

        List<Integer> stocks = jdbcTemplate.query(
                sql,
                preparedStatement -> preparedStatement.setLong(1, productId),
                (resultSet, rowNumber) -> resultSet.getInt("seckill_stock")
        );

        // 查询结果为空时返回 Optional.empty()，避免直接返回 null。
        return stocks.stream().findFirst();
    }
}
