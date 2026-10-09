package com.example.flashsale.order;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 对应 app_order 的一行记录；Lombok 自动生成 getter/setter。 */
@Getter
@Setter
public class SeckillOrder {
    private Long id;
    private String requestId;
    private Long userId;
    private Long productId;
    // 金额使用 BigDecimal，保存成交时金额，而不是查询当前商品价格。
    private BigDecimal amount;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
