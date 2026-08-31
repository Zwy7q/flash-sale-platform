package com.example.flashsale.product;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品业务层。
 *
 * 当前商品查询规则比较简单，所以 Service 只是调用 Repository。
 * 后续可以在这里增加权限判断、活动时间判断和库存业务规则。
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * 查询所有上架商品。
     */
    public List<Product> listOnSaleProducts() {
        return productRepository.findAllOnSale();
    }
}
