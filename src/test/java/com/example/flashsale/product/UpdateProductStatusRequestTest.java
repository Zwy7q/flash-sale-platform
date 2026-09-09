package com.example.flashsale.product;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 商品上下架请求对象测试。 */
class UpdateProductStatusRequestTest {

    @Test
    void shouldStoreStatus() {
        UpdateProductStatusRequest request = new UpdateProductStatusRequest();
        request.setStatus(0);

        assertEquals(0, request.getStatus());
    }
}
