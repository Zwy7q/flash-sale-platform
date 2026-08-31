package com.example.flashsale.product;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

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
}
