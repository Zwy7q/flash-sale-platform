package com.example.flashsale.product;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理员新增商品时提交的请求对象。
 *
 * 请求对象和 Product 分开，避免直接让 HTTP 请求修改领域对象。
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateProductRequest {

    private String name;
    private String description;
    private BigDecimal price;
    private BigDecimal seckillPrice;
    private Integer stock;
    private Integer seckillStock;
    private Integer status;
    private LocalDateTime seckillStartAt;
    private LocalDateTime seckillEndAt;
}
