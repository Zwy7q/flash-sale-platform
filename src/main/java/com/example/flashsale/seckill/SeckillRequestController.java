package com.example.flashsale.seckill;

import com.example.flashsale.auth.JwtAuthenticationInterceptor;
import com.example.flashsale.order.OrderService;
import com.example.flashsale.order.SeckillOrder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;

/** 基础结果查询：JWT 路径已受保护，只返回当前用户的订单。 */
@RestController
@RequestMapping("/api/seckill/requests")
public class SeckillRequestController {
    private final OrderService orders;

    public SeckillRequestController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<?> result(@PathVariable String requestId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ID);
        Optional<SeckillOrder> result = orders.findByRequestIdAndUserId(requestId, userId);
        if (result.isPresent()) {
            return ResponseEntity.ok(result.get());
        }
        // 没有任务表，查不到只能返回未知；不能假装明确知道任务仍在队列中。
        return ResponseEntity.accepted().body(new SeckillResponse(SeckillResult.RESULT_UNKNOWN,
                "尚未查询到你的订单，请稍后查询；持续无结果需检查消息和死信", requestId));
    }
}
