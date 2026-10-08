package com.example.flashsale.seckill;

import com.example.flashsale.product.Product;
import com.example.flashsale.product.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 秒杀业务层测试。 */
@ExtendWith(MockitoExtension.class)
class SeckillServiceTest {

    @Mock
    private ProductService productService;

    @Mock
    private StockRedisService stockRedisService;

    @InjectMocks
    private SeckillService seckillService;

    @Test
    void shouldReturnSuccessWhenProductIsActiveAndStockIsDeducted() {
        Product product = new Product();
        product.setId(1L);
        when(productService.findOnSaleProduct(1L)).thenReturn(Optional.of(product));
        when(productService.isSeckillActive(eq(product), any(LocalDateTime.class))).thenReturn(true);
        when(stockRedisService.tryDeductStock(1L, 20L))
                .thenReturn(StockDeductionResult.SUCCESS);

        SeckillResult result = seckillService.seckill(1L, 20L);

        assertEquals(SeckillResult.SUCCESS, result);
        verify(stockRedisService).ensureStockWarmUp(1L);
        verify(stockRedisService).tryDeductStock(1L, 20L);
    }

    @Test
    void shouldReturnProductNotFoundWhenProductIsNotOnSale() {
        when(productService.findOnSaleProduct(999L)).thenReturn(Optional.empty());

        SeckillResult result = seckillService.seckill(999L, 20L);

        assertEquals(SeckillResult.PRODUCT_NOT_FOUND, result);
    }

    @Test
    void shouldReturnDuplicatePurchaseWhenLuaRejectsTheUser() {
        Product product = new Product();
        product.setId(1L);
        when(productService.findOnSaleProduct(1L)).thenReturn(Optional.of(product));
        when(productService.isSeckillActive(eq(product), any(LocalDateTime.class))).thenReturn(true);
        when(stockRedisService.tryDeductStock(1L, 20L))
                .thenReturn(StockDeductionResult.DUPLICATE_PURCHASE);

        SeckillResult result = seckillService.seckill(1L, 20L);

        assertEquals(SeckillResult.DUPLICATE_PURCHASE, result);
    }
}
