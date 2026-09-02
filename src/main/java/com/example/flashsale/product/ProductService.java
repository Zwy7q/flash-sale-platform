package com.example.flashsale.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

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

    /** 缓存有效期，避免商品数据永久不更新。 */
    private static final Duration PRODUCT_CACHE_TTL = Duration.ofSeconds(100);

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
    public List<Product> listOnSaleProducts() {
        String cachedJson = stringRedisTemplate.opsForValue().get(PRODUCT_LIST_CACHE_KEY);

        if (cachedJson != null) {
            try {
                JavaType listType = objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, Product.class);
                // 缓存命中：直接返回 Redis 中的商品，不访问 MySQL。
                return objectMapper.readValue(cachedJson, listType);
            } catch (JsonProcessingException exception) {
                // 缓存 JSON 损坏时删除脏数据，随后降级查询 MySQL。
                stringRedisTemplate.delete(PRODUCT_LIST_CACHE_KEY);
            }
        }

        // 缓存未命中：MySQL 是真实数据来源。
        List<Product> products = productRepository.findAllOnSale();

        try {
            String json = objectMapper.writeValueAsString(products);
            // 回填缓存，并设置 TTL，让缓存最终自动失效。
            stringRedisTemplate.opsForValue().set(PRODUCT_LIST_CACHE_KEY, json, PRODUCT_CACHE_TTL);
        } catch (JsonProcessingException exception) {
            // 序列化失败不影响本次查询结果，只是不写入缓存。
        }

        return products;
    }
}
