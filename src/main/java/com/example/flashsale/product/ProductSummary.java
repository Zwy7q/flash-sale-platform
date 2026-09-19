package com.example.flashsale.product;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品列表摘要对象。
 *
 * 列表页面只需要展示这些字段，不需要商品描述、总库存和秒杀库存。
 * 单独定义摘要对象可以减少接口响应和 Redis 缓存的体积。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductSummary {

    private Long id;
    private String name;
    private BigDecimal price;
    private BigDecimal seckillPrice;
    private LocalDateTime seckillStartAt;
    private LocalDateTime seckillEndAt;
}
