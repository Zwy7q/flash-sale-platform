package com.example.flashsale.seckill;

import com.example.flashsale.auth.JwtAuthenticationInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 秒杀 HTTP 接口。
 *
 * 这个路径已由 JWT 拦截器保护，未登录用户会在进入 Controller 前得到 401。
 */
@RestController
@RequestMapping("/api/seckill/products")
public class SeckillController {

    private final SeckillService seckillService;

    public SeckillController(SeckillService seckillService) {
        this.seckillService = seckillService;
    }

    /** 提交一次秒杀请求。 */
    @PostMapping("/{productId}")
    public ResponseEntity<SeckillResponse> seckill(
            @PathVariable Long productId,
            HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute(
                JwtAuthenticationInterceptor.CURRENT_USER_ID);

        SeckillResponse response = seckillService.seckill(productId, currentUserId);
        SeckillResult result = response.getResult();

        if (result == SeckillResult.SUCCESS) {
            return ResponseEntity.accepted().body(response);
        }

        if (result == SeckillResult.PRODUCT_NOT_FOUND) {
            return ResponseEntity.notFound().build();
        }

        if (result == SeckillResult.SECKILL_NOT_ACTIVE) {
            return ResponseEntity.badRequest().body(response);
        }

        if (result == SeckillResult.DUPLICATE_PURCHASE) {
            return ResponseEntity.status(409).body(response);
        }

        if (result == SeckillResult.OUT_OF_STOCK) {
            return ResponseEntity.status(409).body(response);
        }

        return ResponseEntity.status(503).body(response);
    }
}
