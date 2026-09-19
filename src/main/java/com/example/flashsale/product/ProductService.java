package com.example.flashsale.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    /** 缓存有效期，避免商品数据永久不更新。 */
    private static final Duration PRODUCT_CACHE_TTL = Duration.ofSeconds(500);

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
        String cachedJson = stringRedisTemplate.opsForValue().get(PRODUCT_LIST_CACHE_KEY);

        if (cachedJson != null) {
            try {
                JavaType listType = objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, ProductSummary.class);
                // 缓存命中：直接返回 Redis 中的商品，不访问 MySQL。
                return objectMapper.readValue(cachedJson, listType);
            } catch (JsonProcessingException exception) {
                // 缓存 JSON 损坏时删除脏数据，随后降级查询 MySQL。
                stringRedisTemplate.delete(PRODUCT_LIST_CACHE_KEY);
            }
        }

        // 缓存未命中：MySQL 是真实数据来源。
        List<ProductSummary> products = productRepository.findAllOnSaleSummaries();

        try {
            String json = objectMapper.writeValueAsString(products);
            // 回填缓存，并设置 TTL，让缓存最终自动失效。
            stringRedisTemplate.opsForValue().set(PRODUCT_LIST_CACHE_KEY, json, PRODUCT_CACHE_TTL);
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
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);

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
                stringRedisTemplate.delete(cacheKey);
            }
        }

        Optional<Product> result = productRepository.findOnSaleById(productId);

        if (result.isPresent()) {
            Product product = result.get();
            try {
                String json = objectMapper.writeValueAsString(product);
                stringRedisTemplate.opsForValue().set(
                        cacheKey,
                        json,
                        PRODUCT_CACHE_TTL);
            } catch (JsonProcessingException exception) {
                // 序列化失败不影响本次查询，只是不写入缓存。
            }
        } else {
            // 数据库中没有商品时缓存一个短期空值，防止缓存穿透。
            stringRedisTemplate.opsForValue().set(
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
    public Optional<Product> updateProduct(Long productId, UpdateProductRequest request) {
        Optional<Product> result = productRepository.update(productId, request);

        if (result.isPresent()) {
            // 修改成功后删除旧列表缓存，避免返回修改前的商品信息。
            stringRedisTemplate.delete(PRODUCT_LIST_CACHE_KEY);
            // 同时删除当前商品的详情缓存。
            stringRedisTemplate.delete(productDetailCacheKey(productId));
        }

        return result;
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
