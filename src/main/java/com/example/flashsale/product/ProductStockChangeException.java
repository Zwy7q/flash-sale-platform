package com.example.flashsale.product;

/** 商品存在，但当前库存或活动状态不允许该修改；返回 HTTP 409。 */
public class ProductStockChangeException extends RuntimeException {
    public ProductStockChangeException(String message) {
        super(message);
    }
}
