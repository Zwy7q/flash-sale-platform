package com.example.flashsale.product;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 管理员商品接口。
 * /api/admin/** 已由 JWT 和管理员拦截器保护。
 */
@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final ProductService productService;

    public AdminProductController(ProductService productService) {
        this.productService = productService;
    }

    /** 管理员新增商品。 */
    @PostMapping
    public ResponseEntity<Product> create(@RequestBody CreateProductRequest request) {
        Product product = productService.createProduct(request);
        return ResponseEntity.ok(product);
    }

    /**
     * 管理员修改商品的全部可编辑信息。
     */
    @PutMapping("/{id}")
    public ResponseEntity<Product> update(
            @PathVariable Long id,
            @RequestBody UpdateProductRequest request) {
        Optional<Product> result = productService.updateProduct(id, request);
        if (result.isPresent()) {
            Product product = result.get();
            return ResponseEntity.ok(product);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 管理员快速修改商品上下架状态。
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<Product> updateStatus(
            @PathVariable Long id,
            @RequestBody UpdateProductStatusRequest request) {
        Optional<Product> result = productService.updateProductStatus(id, request.getStatus());
        if (result.isPresent()) {
            Product product = result.get();
            return ResponseEntity.ok(product);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
