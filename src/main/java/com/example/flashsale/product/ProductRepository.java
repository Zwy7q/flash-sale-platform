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
    /** 管理员修改前读取最新库存与活动时间，不走商品缓存，并锁住该商品行。 */
    public Optional<Product> findByIdForUpdate(Long productId) {
        List<Product> products = jdbcTemplate.query("""
                SELECT seckill_stock, seckill_start_at, seckill_end_at
                FROM app_product WHERE id = ? FOR UPDATE
                """, (resultSet, rowNumber) -> {
            Product product = new Product();
            product.setId(productId);
            product.setSeckillStock(resultSet.getInt("seckill_stock"));
            product.setSeckillStartAt(resultSet.getTimestamp("seckill_start_at").toLocalDateTime());
            product.setSeckillEndAt(resultSet.getTimestamp("seckill_end_at").toLocalDateTime());
            return product;
        }, productId);
        if (products.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(products.get(0));
    }

    /**
     * 锁定商品行直到事务结束。同一商品的下单任务依次进入库存与订单处理。
     * 必须由带事务的 Service 调用，脱离事务使用无法持续持有锁。
     * 不重新检查上下架和活动时间：未来消费者处理的是请求时已获得资格的任务。
     */
    public boolean lockForOrder(Long productId) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM app_product WHERE id = ? FOR UPDATE",
                (resultSet, rowNumber) -> resultSet.getLong("id"), productId);
        return !ids.isEmpty();
    }

    /** 在数据库中做带条件的扣减，库存为 0 时不会减成负数。 */
    public boolean deductSeckillStock(Long productId) {
        int affectedRows = jdbcTemplate.update("""
                UPDATE app_product SET seckill_stock = seckill_stock - 1
                WHERE id = ? AND seckill_stock > 0
                """, productId);
        return affectedRows == 1;
    }

    /** 取消一件商品的订单后归还一件秒杀库存；普通 stock 字段在下单时未扣，故不修改。 */
    public boolean restoreSeckillStock(Long productId) {
        int affectedRows = jdbcTemplate.update("""
                UPDATE app_product SET seckill_stock = seckill_stock + 1 WHERE id = ?
                """, productId);
        return affectedRows == 1;
    }


    private final JdbcTemplate jdbcTemplate;

    public ProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询所有上架商品，并按主键升序返回。
     */
    public List<ProductSummary> findAllOnSaleSummaries() {
        String sql = """
                SELECT id, name, price, seckill_price,
                       seckill_start_at, seckill_end_at
                FROM app_product
                WHERE status = 1
                ORDER BY id
                """;

        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> {
            ProductSummary summary = new ProductSummary();
            summary.setId(resultSet.getLong("id"));
            summary.setName(resultSet.getString("name"));
            summary.setPrice(resultSet.getBigDecimal("price"));
            summary.setSeckillPrice(resultSet.getBigDecimal("seckill_price"));
            summary.setSeckillStartAt(resultSet.getTimestamp("seckill_start_at").toLocalDateTime());
            summary.setSeckillEndAt(resultSet.getTimestamp("seckill_end_at").toLocalDateTime());
            return summary;
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
    public Optional<Product> findAnyById(Long productId) {
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

        if (products.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(products.get(0));
    }

    /** 管理员列表包含下架商品，返回表单需要的完整字段。 */
    public List<Product> findAllForAdmin() {
        String sql = """
                SELECT id, name, description, price, seckill_price,
                       stock, seckill_stock, status, seckill_start_at, seckill_end_at
                FROM app_product ORDER BY id
                """;
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> {
            // 每一行转换成一个 Product，字段名称对应 MySQL 列名。
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

    /**
     * 快速修改商品上下架状态。
     */
    public Optional<Product> updateStatus(Long productId, Integer status) {
        String sql = """
                UPDATE app_product
                SET status = ?
                WHERE id = ?
                """;

        int affectedRows = jdbcTemplate.update(sql, status, productId);
        if (affectedRows == 0) {
            return Optional.empty();
        }

        return findAnyById(productId);
    }
}
