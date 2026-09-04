package com.example.flashsale.product;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 新增商品请求对象测试。
 * 这里只验证请求对象可以承载前端提交的商品字段。
 */
class CreateProductRequestTest {

    @Test
    void shouldStoreProductFields() {
        CreateProductRequest request = new CreateProductRequest();
        request.setName("测试商品");
        request.setDescription("测试描述");
        request.setPrice(new BigDecimal("99.00"));
        request.setSeckillPrice(new BigDecimal("59.00"));
        request.setStock(100);
        request.setSeckillStock(30);
        request.setStatus(1);
        request.setSeckillStartAt(LocalDateTime.of(2026, 9, 5, 10, 0));
        request.setSeckillEndAt(LocalDateTime.of(2026, 9, 5, 12, 0));

        assertEquals("测试商品", request.getName());
        assertEquals(100, request.getStock());
        assertEquals(30, request.getSeckillStock());
    }
}
