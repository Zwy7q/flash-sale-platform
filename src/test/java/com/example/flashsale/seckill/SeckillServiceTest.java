package com.example.flashsale.seckill;

import com.example.flashsale.product.Product;
import com.example.flashsale.product.ProductService;
import com.example.flashsale.messaging.SeckillMessageProducer;
import com.example.flashsale.messaging.SeckillMessage;
import com.example.flashsale.messaging.MessagePublishException;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 秒杀业务层测试。 */
@ExtendWith(MockitoExtension.class)
class SeckillServiceTest {

    @Mock
    private ProductService productService;

    @Mock
    private StockRedisService stockRedisService;

    @Mock
    private SeckillMessageProducer producer;

    @InjectMocks
    private SeckillService seckillService;

    @Test
    void shouldReturnSuccessWhenProductIsActiveAndStockIsDeducted() {
        Product product = new Product();
        product.setId(1L);
        product.setSeckillPrice(new java.math.BigDecimal("129.00"));
        when(productService.findOnSaleProduct(1L)).thenReturn(Optional.of(product));
        when(productService.isSeckillActive(eq(product), any(LocalDateTime.class))).thenReturn(true);
        when(stockRedisService.tryDeductStock(eq(1L), eq(20L), anyString()))
                .thenReturn(StockDeductionResult.SUCCESS);

        SeckillResponse result = seckillService.seckill(1L, 20L);

        assertEquals(SeckillResult.SUCCESS, result.getResult());
        assertNotNull(result.getRequestId());
        org.mockito.ArgumentCaptor<SeckillMessage> message = org.mockito.ArgumentCaptor.forClass(SeckillMessage.class);
        verify(producer).send(message.capture());
        assertEquals(result.getRequestId(), message.getValue().getRequestId());
        assertEquals(20L, message.getValue().getUserId());
        assertEquals(product.getSeckillPrice(), message.getValue().getAmount());
        verify(stockRedisService).ensureStockWarmUp(1L);
        verify(stockRedisService).tryDeductStock(1L, 20L, result.getRequestId());
    }

    @Test
    void shouldReturnProductNotFoundWhenProductIsNotOnSale() {
        when(productService.findOnSaleProduct(999L)).thenReturn(Optional.empty());

        SeckillResponse result = seckillService.seckill(999L, 20L);

        assertEquals(SeckillResult.PRODUCT_NOT_FOUND, result.getResult());
        verifyNoInteractions(producer);
    }

    @Test
    void shouldReturnDuplicatePurchaseWhenLuaRejectsTheUser() {
        Product product = new Product();
        product.setId(1L);
        when(productService.findOnSaleProduct(1L)).thenReturn(Optional.of(product));
        when(productService.isSeckillActive(eq(product), any(LocalDateTime.class))).thenReturn(true);
        when(stockRedisService.tryDeductStock(eq(1L), eq(20L), anyString()))
                .thenReturn(StockDeductionResult.DUPLICATE_PURCHASE);

        SeckillResponse result = seckillService.seckill(1L, 20L);

        assertEquals(SeckillResult.DUPLICATE_PURCHASE, result.getResult());
        verifyNoInteractions(producer);
    }

    @Test
    void shouldReportUnknownPublishWithRequestId() {
        Product product = new Product();
        product.setId(1L);
        product.setSeckillPrice(new java.math.BigDecimal("129.00"));
        when(productService.findOnSaleProduct(1L)).thenReturn(Optional.of(product));
        when(productService.isSeckillActive(eq(product), any(LocalDateTime.class))).thenReturn(true);
        when(stockRedisService.tryDeductStock(eq(1L), eq(20L), anyString())).thenReturn(StockDeductionResult.SUCCESS);
        doThrow(new MessagePublishException(MessagePublishException.Outcome.UNKNOWN, "timeout", null))
                .when(producer).send(any(SeckillMessage.class));
        SeckillResponse response = seckillService.seckill(1L, 20L);
        assertEquals(SeckillResult.PUBLISH_UNKNOWN, response.getResult());
        assertNotNull(response.getRequestId());
    }
}
