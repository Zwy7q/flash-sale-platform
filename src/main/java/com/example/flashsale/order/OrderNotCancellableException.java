package com.example.flashsale.order;

/** 已支付或已超时订单不允许走普通取消入口，交给 Controller 返回 409。 */
public class OrderNotCancellableException extends RuntimeException {
    public OrderNotCancellableException(String message) {
        super(message);
    }
}
