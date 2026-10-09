package com.example.flashsale.order;

import com.example.flashsale.auth.JwtAuthenticationInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Optional;

/** 我的订单接口：只处理 HTTP 和当前用户信息，数据库查询交给业务层。 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 查询当前用户的订单，不接受前端传入的 userId 作为身份依据。 */
    @GetMapping
    public ResponseEntity<List<SeckillOrder>> list(HttpServletRequest request) {
        // JWT 拦截器先验证 Token，再将可信的用户 ID 放入本次请求属性。
        Long currentUserId = (Long) request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ID);
        List<SeckillOrder> orders = orderService.listMyOrders(currentUserId);
        // 没有订单时返回 200 + []，表示查询成功但列表为空。
        return ResponseEntity.ok(orders);
    }

    /** 查自己的订单；他人订单和不存在的订单统一返回 404。 */
    @GetMapping("/{id}")
    public ResponseEntity<SeckillOrder> detail(@PathVariable Long id, HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ID);
        Optional<SeckillOrder> result = orderService.findMyOrder(id, currentUserId);
        if (result.isPresent()) {
            SeckillOrder order = result.get();
            return ResponseEntity.ok(order);
        } else {
            // 不区分“无此订单”和“他人的订单”，避免泄露订单是否存在。
            return ResponseEntity.notFound().build();
        }
    }
}
