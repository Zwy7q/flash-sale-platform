package com.example.flashsale.order;

/** MySQL 已完成取消，但 Redis 释放失败或结果未知，不能向用户报告完整成功。 */
public class OrderStockReleaseException extends RuntimeException {
    public OrderStockReleaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
