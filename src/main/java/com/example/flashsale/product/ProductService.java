package com.example.flashsale.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 商品业务层。
 *
 * 当前商品查询规则比较简单，所以 Service 只是调用 Repository。
 * 后续可以在这里增加权限判断、活动时间判断和库存业务规则。
 */
@Service
public class ProductService {

    /** Redis 中保存商品列表的固定 Key。 */
    public static final String PRODUCT_LIST_CACHE_KEY = "flash-sale:products:on-sale";

    /** Redis 中保存单个商品详情的 Key 前缀。 */
    private static final String PRODUCT_DETAIL_CACHE_KEY_PREFIX = "flash-sale:product:";

    /**
     * Redis 中表示“商品不存在”的特殊值。
     *
     * 不能把 Java 的 null 直接保存到 Redis，所以使用一个约定好的字符串表示空结果。
     */
    public static final String EMPTY_PRODUCT_CACHE_VALUE = "__EMPTY_PRODUCT__";

    /** 商品正常缓存的基础有效期。 */
    private static final long PRODUCT_CACHE_TTL_SECONDS = 500;

    /** 每个商品缓存额外随机增加的最大秒数。 */
    private static final int PRODUCT_CACHE_TTL_JITTER_MAX_SECONDS = 30;

    /** 空值缓存只保存较短时间，避免商品后来创建后长时间查不到。 */
    private static final Duration EMPTY_PRODUCT_CACHE_TTL = Duration.ofSeconds(30);

    private final ProductRepository productRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public ProductService(
            ProductRepository productRepository,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询所有上架商品。
     */
    public List<ProductSummary> listOnSaleProducts() {
        String cachedJson = readCacheValue(PRODUCT_LIST_CACHE_KEY);

        if (cachedJson != null) {
            try {
                JavaType listType = objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, ProductSummary.class);
                // 缓存命中：直接返回 Redis 中的商品，不访问 MySQL。
                return objectMapper.readValue(cachedJson, listType);
            } catch (JsonProcessingException exception) {
                // 缓存 JSON 损坏时删除脏数据，随后降级查询 MySQL。
                deleteCacheValue(PRODUCT_LIST_CACHE_KEY);
            }
        }

        // 缓存未命中：MySQL 是真实数据来源。
        List<ProductSummary> products = productRepository.findAllOnSaleSummaries();

        try {
            String json = objectMapper.writeValueAsString(products);
            // 回填缓存，并设置 TTL，让缓存最终自动失效。
            writeCacheValue(
                    PRODUCT_LIST_CACHE_KEY,
                    json,
                    randomProductCacheTtl());
        } catch (JsonProcessingException exception) {
            // 序列化失败不影响本次查询结果，只是不写入缓存。
        }

        return products;
    }

    /**
     * 查询一件正在上架的商品详情。
     */
    public Optional<Product> findOnSaleProduct(Long productId) {
        String cacheKey = productDetailCacheKey(productId);
        String cachedJson = readCacheValue(cacheKey);

        if (cachedJson != null) {
            // 命中空值标记：说明最近查询过该 ID，但数据库中没有上架商品。
            // 直接返回空结果，避免恶意或重复请求不断访问 MySQL。
            if (EMPTY_PRODUCT_CACHE_VALUE.equals(cachedJson)) {
                return Optional.empty();
            }

            try {
                // Redis 中保存的是一个商品 JSON，因此反序列化成 Product 对象。
                Product product = objectMapper.readValue(cachedJson, Product.class);
                return Optional.of(product);
            } catch (JsonProcessingException exception) {
                // 缓存内容损坏时删除脏数据，然后继续查询 MySQL。
                deleteCacheValue(cacheKey);
            }
        }

        Optional<Product> result = productRepository.findOnSaleById(productId);

        if (result.isPresent()) {
            Product product = result.get();
            try {
                String json = objectMapper.writeValueAsString(product);
                writeCacheValue(
                        cacheKey,
                        json,
                        randomProductCacheTtl());
            } catch (JsonProcessingException exception) {
                // 序列化失败不影响本次查询，只是不写入缓存。
            }
        } else {
            // 数据库中没有商品时缓存一个短期空值，防止缓存穿透。
            writeCacheValue(
                    cacheKey,
                    EMPTY_PRODUCT_CACHE_VALUE,
                    EMPTY_PRODUCT_CACHE_TTL);
        }

        return result;
    }

    /** 根据商品 ID 构造商品详情缓存 Key。 */
    public static String productDetailCacheKey(Long productId) {
        return PRODUCT_DETAIL_CACHE_KEY_PREFIX + productId;
    }

    /**
     * 根据指定的随机偏移量计算缓存 TTL。
     *
     * 这个方法不直接产生随机数，便于测试“基础时间 + 偏移量”的计算规则。
     */
    static Duration productCacheTtlWithJitter(int jitterSeconds) {
        if (jitterSeconds < 0 || jitterSeconds > PRODUCT_CACHE_TTL_JITTER_MAX_SECONDS) {
            throw new IllegalArgumentException("缓存随机偏移量必须在 0 到 30 秒之间");
        }

        return Duration.ofSeconds(PRODUCT_CACHE_TTL_SECONDS + jitterSeconds);
    }

    /**
     * 生成本次写缓存要使用的 TTL。
     *
     * 不同 Key 会得到不同的过期时间，降低大量缓存同一时刻失效的概率。
     */
    private Duration randomProductCacheTtl() {
        int jitterSeconds = ThreadLocalRandom.current()
                .nextInt(PRODUCT_CACHE_TTL_JITTER_MAX_SECONDS + 1);
        return productCacheTtlWithJitter(jitterSeconds);
    }

    /**
     * 安全读取 Redis 缓存。
     *
     * Redis 暂时不可用时，把它视为“缓存未命中”，后续业务会查询 MySQL。
     */
    private String readCacheValue(String cacheKey) {
        try {
            return stringRedisTemplate.opsForValue().get(cacheKey);
        } catch (RedisConnectionFailureException exception) {
            return null;
        }
    }

    /**
     * 安全写入 Redis 缓存。
     *
     * 缓存是性能优化而非唯一数据来源，写入失败不能影响本次 MySQL 查询结果。
     */
    private void writeCacheValue(String cacheKey, String value, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, value, ttl);
        } catch (RedisConnectionFailureException exception) {
            // Redis 恢复后，后续请求会重新回填缓存。
        }
    }

    /** Redis 不可用时，删除缓存失败也不影响商品查询。 */
    private void deleteCacheValue(String cacheKey) {
        try {
            stringRedisTemplate.delete(cacheKey);
        } catch (RedisConnectionFailureException exception) {
            // 缓存服务恢复后，由 TTL 或下一次写入处理旧缓存。
        }
    }

    /**
     * 判断商品当前是否处于秒杀活动时间窗口。
     * 开始时间可以参加，结束时间这一刻开始不再参加。
     */
    public boolean isSeckillActive(Product product, LocalDateTime now) {
        LocalDateTime startAt = product.getSeckillStartAt();
        LocalDateTime endAt = product.getSeckillEndAt();

        return !now.isBefore(startAt) && now.isBefore(endAt);
    }

    /**
     * 创建商品。
     */
    public Product createProduct(CreateProductRequest request) {
        Product product = productRepository.create(request);

        // 新增商品会改变商品列表，删除旧列表缓存，下一次查询时重新加载。
        stringRedisTemplate.delete(PRODUCT_LIST_CACHE_KEY);
        return product;
    }

    /**
     * 修改商品全部信息。商品不存在时返回 Optional.empty()。
     */
    @Transactional
    public Optional<Product> updateProduct(Long productId, UpdateProductRequest request) {
        // 必须使用 MySQL 最新记录；缓存可能带着消费者扣库存之前的旧值。
        Optional<Product> currentResult = productRepository.findByIdForUpdate(productId);
        if (currentResult.isEmpty()) {
            return Optional.empty();
        }
        Product current = currentResult.get();
        if (request.getSeckillStock() == null || request.getSeckillStock() < 0
                || request.getSeckillStartAt() == null || request.getSeckillEndAt() == null
                || !request.getSeckillEndAt().isAfter(request.getSeckillStartAt())) {
            throw new ProductStockChangeException("秒杀库存必须非负，且结束时间必须晚于开始时间");
        }
        boolean stockChanged = !current.getSeckillStock().equals(request.getSeckillStock());
        boolean timeChanged = !current.getSeckillStartAt().equals(request.getSeckillStartAt())
                || !current.getSeckillEndAt().equals(request.getSeckillEndAt());
        if (stockChanged || timeChanged) {
            LocalDateTime now = LocalDateTime.now();
            // 使用修改前的时间，防止先把已开始活动改到未来、再修改库存来绕过限制。
            if (!now.isBefore(current.getSeckillStartAt())) {
                throw new ProductStockChangeException("活动已经开始或结束，不能修改秒杀库存或重新排期");
            }
            if (!now.isBefore(request.getSeckillStartAt())) {
                throw new ProductStockChangeException("调整库存或排期时，新开始时间必须在未来");
            }
            verifyNoReservationAndInvalidateStock(productId, current.getSeckillStock());
        }
        Optional<Product> result = productRepository.update(productId, request);

        if (result.isPresent()) {
            // 修改成功后删除旧列表缓存，避免返回修改前的商品信息。
            stringRedisTemplate.delete(PRODUCT_LIST_CACHE_KEY);
            // 同时删除当前商品的详情缓存。
            stringRedisTemplate.delete(productDetailCacheKey(productId));
        }

        return result;
    }

    /** 活动开始前的基础防误操作检查；Redis 不可用时不能降级为“没有预扣”。 */
    private void verifyNoReservationAndInvalidateStock(Long productId, Integer databaseStock) {
        String stockKey = "flash-sale:stock:" + productId;
        try {
            String redisStock = stringRedisTemplate.opsForValue().get(stockKey);
            if (redisStock != null && !databaseStock.equals(Integer.valueOf(redisStock))) {
                throw new ProductStockChangeException("Redis 与 MySQL 库存不一致，需先核对预扣记录");
            }
            ScanOptions.ScanOptionsBuilder builder = ScanOptions.scanOptions();
            builder.match("flash-sale:purchase:" + productId + ":*");
            builder.count(100); // 每批扫描建议数量，避免使用阻塞 Redis 的全库 KEYS 命令。
            try (Cursor<String> cursor = stringRedisTemplate.scan(builder.build())) {
                if (cursor.hasNext()) {
                    throw new ProductStockChangeException("该商品仍有购买标记，不能调整库存或重新排期");
                }
            }
            // 安全准备阶段删除旧库存，下一次抢购从修改后的 MySQL 数据 SETNX 预热。
            // 不直接 SET 一个新值，避免误覆盖；MySQL 后续失败时仅丢失可重建的预热数据。
            stringRedisTemplate.delete(stockKey);
        } catch (DataAccessException | NumberFormatException exception) {
            throw new ProductStockChangeException("暂时无法确认 Redis 库存安全，请检查 Redis 后重试");
        }
    }

    /**
     * 快速修改商品上下架状态。
     */
    public Optional<Product> updateProductStatus(Long productId, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new IllegalArgumentException("商品状态只能是 0 或 1");
        }

        Optional<Product> result = productRepository.updateStatus(productId, status);

        if (result.isPresent()) {
            // 上下架会影响“上架商品列表”，因此需要删除列表缓存。
            stringRedisTemplate.delete(PRODUCT_LIST_CACHE_KEY);
            // 商品状态变化也会影响商品详情是否可见。
            stringRedisTemplate.delete(productDetailCacheKey(productId));
        }

        return result;
    }
}
