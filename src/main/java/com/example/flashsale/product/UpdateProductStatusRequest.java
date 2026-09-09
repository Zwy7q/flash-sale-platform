package com.example.flashsale.product;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 商品上下架请求对象。
 * status=1 表示上架，status=0 表示下架。
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateProductStatusRequest {

    private Integer status;
}
