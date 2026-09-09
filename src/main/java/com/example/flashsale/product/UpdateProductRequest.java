package com.example.flashsale.product;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理员修改商品时提交的完整字段。
 *
 * 修改接口要求客户端一次提交商品的全部可编辑信息，
 * 因此不会出现“只改了价格却意外把库存清零”的情况。
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateProductRequest {

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
