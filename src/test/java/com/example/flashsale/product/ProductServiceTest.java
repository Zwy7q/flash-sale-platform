package com.example.flashsale.product;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

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

    @InjectMocks
    private ProductService productService;

    @Test
    void shouldDelegateToRepository() {
        List<Product> expectedProducts = List.of(new Product());
        when(productRepository.findAllOnSale()).thenReturn(expectedProducts);

        List<Product> actualProducts = productService.listOnSaleProducts();

        assertSame(expectedProducts, actualProducts);
        verify(productRepository).findAllOnSale();
    }
}
