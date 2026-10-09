package com.example.flashsale.order;

/** 订单状态不允许支付的业务异常；由 Controller 转换为 HTTP 409。 */
public class OrderNotPayableException extends RuntimeException {
    public OrderNotPayableException(String message) {
        super(message);
    }
}
