package com.example.flashsale.order;

/** 枚举限制 Java 中可使用的订单状态，避免到处手写字符串。 */
public enum OrderStatus {
    PENDING_PAYMENT, // 订单已创建，等待支付；与尚未创建订单的“排队中”不同。
    PAID,            // 已支付。
    CANCELLED,       // 用户取消。
    PAYMENT_TIMEOUT  // 超过支付期限。
}
