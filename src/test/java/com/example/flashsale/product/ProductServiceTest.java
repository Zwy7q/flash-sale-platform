package com.example.flashsale.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品 Service 层单元测试。
 *
 * 这里使用 Mock 隔离数据库，专门验证 Service 是否正确调用 Repository。
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ProductService productService;

    @Test
    void shouldDelegateToRepository() {
        List<ProductSummary> expectedProducts = List.of(new ProductSummary());
        when(productRepository.findAllOnSaleSummaries()).thenReturn(expectedProducts);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(ProductService.PRODUCT_LIST_CACHE_KEY)).thenReturn(null);

        List<ProductSummary> actualProducts = productService.listOnSaleProducts();

        assertSame(expectedProducts, actualProducts);
        verify(productRepository).findAllOnSaleSummaries();
    }

    @Test
    void shouldReadProductsFromRedisWhenCacheHit() throws Exception {
        ProductSummary product = new ProductSummary();
        product.setId(1L);
        product.setName("缓存商品");
        List<ProductSummary> cachedProducts = List.of(product);
        String cachedJson = "[{\"id\":1,\"name\":\"缓存商品\"}]";

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(ProductService.PRODUCT_LIST_CACHE_KEY)).thenReturn(cachedJson);
        List<ProductSummary> actualProducts = productService.listOnSaleProducts();

        // JSON 反序列化会创建新的 String 对象，所以比较内容应使用 assertEquals。
        assertEquals(cachedProducts.get(0).getName(), actualProducts.get(0).getName());
        verify(productRepository, org.mockito.Mockito.never()).findAllOnSaleSummaries();
    }

    @Test
    void shouldFindOnSaleProductById() {
        Product expectedProduct = new Product();
        expectedProduct.setId(1L);
        expectedProduct.setName("演示机械键盘");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(ProductService.productDetailCacheKey(1L))).thenReturn(null);
        when(productRepository.findOnSaleById(1L))
                .thenReturn(Optional.of(expectedProduct));

        Optional<Product> actualProduct = productService.findOnSaleProduct(1L);

        assertSame(expectedProduct, actualProduct.orElseThrow());
        verify(productRepository).findOnSaleById(1L);
    }

    @Test
    void shouldReadProductDetailFromRedisWhenCacheHit() {
        String cacheKey = ProductService.productDetailCacheKey(1L);
        String cachedJson = """
                {
                  "id": 1,
                  "name": "缓存详情商品",
                  "price": 99.00,
                  "seckillStock": 20
                }
                """;

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(cachedJson);

        Optional<Product> result = productService.findOnSaleProduct(1L);

        assertEquals("缓存详情商品", result.orElseThrow().getName());
        assertEquals(20, result.orElseThrow().getSeckillStock());
        verify(productRepository, org.mockito.Mockito.never()).findOnSaleById(1L);
    }

    @Test
    void shouldReadProductDetailFromDatabaseAndCacheItWhenCacheMiss() throws Exception {
        Product databaseProduct = new Product();
        databaseProduct.setId(1L);
        databaseProduct.setName("数据库详情商品");
        databaseProduct.setPrice(new BigDecimal("199.00"));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(ProductService.productDetailCacheKey(1L))).thenReturn(null);
        when(productRepository.findOnSaleById(1L))
                .thenReturn(Optional.of(databaseProduct));

        Optional<Product> result = productService.findOnSaleProduct(1L);

        assertEquals("数据库详情商品", result.orElseThrow().getName());
        verify(productRepository).findOnSaleById(1L);
        verify(valueOperations).set(
                org.mockito.ArgumentMatchers.eq(ProductService.productDetailCacheKey(1L)),
                org.mockito.ArgumentMatchers.contains("数据库详情商品"),
                org.mockito.ArgumentMatchers.any(java.time.Duration.class));
    }

    @Test
    void shouldReturnFalseBeforeSeckillStarts() {
        Product product = productWithSeckillTime(
                LocalDateTime.of(2026, 9, 4, 10, 0),
                LocalDateTime.of(2026, 9, 4, 12, 0));

        boolean active = productService.isSeckillActive(
                product, LocalDateTime.of(2026, 9, 4, 9, 59));

        assertEquals(false, active);
    }

    @Test
    void shouldReturnTrueDuringSeckill() {
        Product product = productWithSeckillTime(
                LocalDateTime.of(2026, 9, 4, 10, 0),
                LocalDateTime.of(2026, 9, 4, 12, 0));

        boolean active = productService.isSeckillActive(
                product, LocalDateTime.of(2026, 9, 4, 11, 0));

        assertEquals(true, active);
    }

    @Test
    void shouldReturnFalseAtOrAfterSeckillEnds() {
        Product product = productWithSeckillTime(
                LocalDateTime.of(2026, 9, 4, 10, 0),
                LocalDateTime.of(2026, 9, 4, 12, 0));

        boolean activeAtEnd = productService.isSeckillActive(
                product, LocalDateTime.of(2026, 9, 4, 12, 0));
        boolean activeAfterEnd = productService.isSeckillActive(
                product, LocalDateTime.of(2026, 9, 4, 12, 1));

        assertEquals(false, activeAtEnd);
        assertEquals(false, activeAfterEnd);
    }

    @Test
    void shouldCreateProductThroughRepository() {
        CreateProductRequest request = new CreateProductRequest();
        request.setName("新增商品");
        request.setDescription("新增商品描述");
        request.setPrice(new BigDecimal("199.00"));
        request.setSeckillPrice(new BigDecimal("129.00"));
        request.setStock(100);
        request.setSeckillStock(50);
        request.setStatus(1);
        request.setSeckillStartAt(LocalDateTime.of(2026, 9, 5, 10, 0));
        request.setSeckillEndAt(LocalDateTime.of(2026, 9, 5, 12, 0));

        Product createdProduct = new Product();
        createdProduct.setId(10L);
        createdProduct.setName("新增商品");
        when(productRepository.create(request)).thenReturn(createdProduct);

        Product actualProduct = productService.createProduct(request);

        assertEquals(10L, actualProduct.getId());
        assertEquals("新增商品", actualProduct.getName());
        verify(productRepository).create(request);
        verify(stringRedisTemplate).delete(ProductService.PRODUCT_LIST_CACHE_KEY);
    }

    @Test
    void shouldUpdateProductThroughRepository() {
        UpdateProductRequest request = new UpdateProductRequest();
        request.setName("修改后的商品");
        request.setDescription("修改后的描述");
        request.setPrice(new BigDecimal("399.00"));
        request.setSeckillPrice(new BigDecimal("299.00"));
        request.setStock(300);
        request.setSeckillStock(120);
        request.setStatus(1);
        request.setSeckillStartAt(LocalDateTime.of(2026, 9, 6, 10, 0));
        request.setSeckillEndAt(LocalDateTime.of(2026, 9, 6, 12, 0));

        Product updatedProduct = new Product();
        updatedProduct.setId(1L);
        updatedProduct.setName("修改后的商品");
        updatedProduct.setStock(300);
        when(productRepository.update(1L, request)).thenReturn(Optional.of(updatedProduct));

        Optional<Product> actualProduct = productService.updateProduct(1L, request);

        assertEquals("修改后的商品", actualProduct.orElseThrow().getName());
        assertEquals(300, actualProduct.orElseThrow().getStock());
        verify(productRepository).update(1L, request);
        verify(stringRedisTemplate).delete(ProductService.PRODUCT_LIST_CACHE_KEY);
        verify(stringRedisTemplate).delete(ProductService.productDetailCacheKey(1L));
    }

    @Test
    void shouldUpdateProductStatusThroughRepository() {
        Product updatedProduct = new Product();
        updatedProduct.setId(1L);
        updatedProduct.setStatus(0);
        when(productRepository.updateStatus(1L, 0))
                .thenReturn(Optional.of(updatedProduct));

        Optional<Product> result = productService.updateProductStatus(1L, 0);

        assertEquals(0, result.orElseThrow().getStatus());
        verify(productRepository).updateStatus(1L, 0);
        verify(stringRedisTemplate).delete(ProductService.PRODUCT_LIST_CACHE_KEY);
        verify(stringRedisTemplate).delete(ProductService.productDetailCacheKey(1L));
    }

    /** 构造只包含活动时间的商品，避免每个测试重复填写其他字段。 */
    private Product productWithSeckillTime(
            LocalDateTime startAt,
            LocalDateTime endAt) {
        Product product = new Product();
        product.setSeckillStartAt(startAt);
        product.setSeckillEndAt(endAt);
        return product;
    }
}
