package com.example.flashsale.product;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * 管理员商品接口。
 * /api/admin/** 已由 JWT 和管理员拦截器保护。
 */
@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {
    /** 返回具体冲突原因，方便初学者在 Apifox 中理解为什么被拒绝。 */
    @ExceptionHandler(ProductStockChangeException.class)
    public ResponseEntity<Map<String, String>> handleStockConflict(ProductStockChangeException exception) {
        return ResponseEntity.status(409).body(Map.of("code", "STOCK_CHANGE_NOT_ALLOWED",
                "message", exception.getMessage()));
    }

    private final ProductService productService;

    public AdminProductController(ProductService productService) {
        this.productService = productService;
    }

    /** 管理端查询所有商品，包含下架商品，读取数据库最新信息。 */
    @GetMapping
    public ResponseEntity<List<Product>> list() {
        List<Product> products = productService.listAdminProducts();
        return ResponseEntity.ok(products);
    }

    /** 编辑前按 ID 查询最新详情，不复用用户侧缓存。 */
    @GetMapping("/{id}")
    public ResponseEntity<Product> detail(@PathVariable Long id) {
        Optional<Product> result = productService.findAdminProduct(id);
        if (result.isPresent()) {
            Product product = result.get();
            return ResponseEntity.ok(product);
        } else {
            return ResponseEntity.notFound().build();
        }
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
