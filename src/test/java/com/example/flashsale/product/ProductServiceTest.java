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
        List<Product> expectedProducts = List.of(new Product());
        when(productRepository.findAllOnSale()).thenReturn(expectedProducts);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(ProductService.PRODUCT_LIST_CACHE_KEY)).thenReturn(null);

        List<Product> actualProducts = productService.listOnSaleProducts();

        assertSame(expectedProducts, actualProducts);
        verify(productRepository).findAllOnSale();
    }

    @Test
    void shouldReadProductsFromRedisWhenCacheHit() throws Exception {
        Product product = new Product();
        product.setId(1L);
        product.setName("缓存商品");
        List<Product> cachedProducts = List.of(product);
        String cachedJson = "[{\"id\":1,\"name\":\"缓存商品\"}]";

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(ProductService.PRODUCT_LIST_CACHE_KEY)).thenReturn(cachedJson);
        List<Product> actualProducts = productService.listOnSaleProducts();

        // JSON 反序列化会创建新的 String 对象，所以比较内容应使用 assertEquals。
        assertEquals(cachedProducts.get(0).getName(), actualProducts.get(0).getName());
        verify(productRepository, org.mockito.Mockito.never()).findAllOnSale();
    }

    @Test
    void shouldFindOnSaleProductById() {
        Product expectedProduct = new Product();
        expectedProduct.setId(1L);
        expectedProduct.setName("演示机械键盘");
        when(productRepository.findOnSaleById(1L))
                .thenReturn(Optional.of(expectedProduct));

        Optional<Product> actualProduct = productService.findOnSaleProduct(1L);

        assertSame(expectedProduct, actualProduct.orElseThrow());
        verify(productRepository).findOnSaleById(1L);
    }
}
