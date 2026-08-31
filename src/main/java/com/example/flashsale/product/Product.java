package com.example.flashsale.product;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品领域对象。
 *
 * 这个类用于承载数据库查询结果，并作为接口返回的数据模型。
 * 当前先保留秒杀场景需要的字段，后续再增加图片、分类等信息。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    private Long id;
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
