package com.example.flashsale.product;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 修改商品请求对象测试。
 */
class UpdateProductRequestTest {

    @Test
    void shouldStoreAllEditableProductFields() {
        UpdateProductRequest request = new UpdateProductRequest();
        request.setName("修改后的商品");
        request.setDescription("修改后的描述");
        request.setPrice(new BigDecimal("399.00"));
        request.setSeckillPrice(new BigDecimal("299.00"));
        request.setStock(300);
        request.setSeckillStock(120);
        request.setStatus(0);
        request.setSeckillStartAt(LocalDateTime.of(2026, 9, 6, 10, 0));
        request.setSeckillEndAt(LocalDateTime.of(2026, 9, 6, 12, 0));

        assertEquals("修改后的商品", request.getName());
        assertEquals(new BigDecimal("399.00"), request.getPrice());
        assertEquals(300, request.getStock());
        assertEquals(0, request.getStatus());
    }
}
