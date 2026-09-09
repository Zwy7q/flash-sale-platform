package com.example.flashsale.product;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Statement;
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

    /**
     * 根据商品 ID 查询一件正在上架的商品。
     * 查询不到时返回 Optional.empty()，由 Service 层决定如何处理。
     */
    public Optional<Product> findOnSaleById(Long productId) {
        String sql = """
                SELECT id, name, description, price, seckill_price,
                       stock, seckill_stock, status,
                       seckill_start_at, seckill_end_at
                FROM app_product
                WHERE id = ? AND status = 1
                """;

        List<Product> products = jdbcTemplate.query(
                sql,
                preparedStatement -> preparedStatement.setLong(1, productId),
                (resultSet, rowNumber) -> {
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
                }
        );

        return products.stream().findFirst();
    }

    /**
     * 新增商品并返回数据库生成的商品 ID。
     */
    public Product create(CreateProductRequest request) {
        String sql = """
                INSERT INTO app_product (
                    name, description, price, seckill_price,
                    stock, seckill_stock, status,
                    seckill_start_at, seckill_end_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            var preparedStatement = connection.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS);
            preparedStatement.setString(1, request.getName());
            preparedStatement.setString(2, request.getDescription());
            preparedStatement.setBigDecimal(3, request.getPrice());
            preparedStatement.setBigDecimal(4, request.getSeckillPrice());
            preparedStatement.setInt(5, request.getStock());
            preparedStatement.setInt(6, request.getSeckillStock());
            preparedStatement.setInt(7, request.getStatus());
            preparedStatement.setObject(8, request.getSeckillStartAt());
            preparedStatement.setObject(9, request.getSeckillEndAt());
            return preparedStatement;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("商品新增后没有获得自增 ID");
        }

        // 使用数据库生成的 ID 查询，避免并发新增同名商品时查错记录。
        return findAnyById(generatedId.longValue())
                .orElseThrow(() -> new IllegalStateException("商品新增后查询失败"));
    }

    /** 根据商品 ID 查询商品，不限制上下架状态。 */
    private Optional<Product> findAnyById(Long productId) {
        String sql = """
                SELECT id, name, description, price, seckill_price,
                       stock, seckill_stock, status,
                       seckill_start_at, seckill_end_at
                FROM app_product
                WHERE id = ?
                """;

        List<Product> products = jdbcTemplate.query(
                sql,
                preparedStatement -> preparedStatement.setLong(1, productId),
                (resultSet, rowNumber) -> {
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
                }
        );

        return products.stream().findFirst();
    }

    /**
     * 修改商品的全部可编辑字段。
     * 返回 Optional.empty() 表示指定 ID 的商品不存在。
     */
    public Optional<Product> update(Long productId, UpdateProductRequest request) {
        String sql = """
                UPDATE app_product
                SET name = ?,
                    description = ?,
                    price = ?,
                    seckill_price = ?,
                    stock = ?,
                    seckill_stock = ?,
                    status = ?,
                    seckill_start_at = ?,
                    seckill_end_at = ?
                WHERE id = ?
                """;

        int affectedRows = jdbcTemplate.update(
                sql,
                request.getName(),
                request.getDescription(),
                request.getPrice(),
                request.getSeckillPrice(),
                request.getStock(),
                request.getSeckillStock(),
                request.getStatus(),
                request.getSeckillStartAt(),
                request.getSeckillEndAt(),
                productId
        );

        if (affectedRows == 0) {
            return Optional.empty();
        }

        return findAnyById(productId);
    }
}
