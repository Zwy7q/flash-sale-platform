package com.example.flashsale.order;

import com.example.flashsale.auth.JwtAuthenticationInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Optional;
import java.util.Map;

/** 我的订单接口：只处理 HTTP 和当前用户信息，数据库查询交给业务层。 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;
    private final OrderCancellationService cancellationService;

    public OrderController(OrderService orderService, OrderCancellationService cancellationService) {
        this.orderService = orderService;
        this.cancellationService = cancellationService;
    }

    /** 取消自己的待支付订单，无请求体；重复取消返回同一笔已取消订单。 */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<SeckillOrder> cancel(@PathVariable Long id, HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ID);
        Optional<SeckillOrder> result = cancellationService.cancel(id, currentUserId);
        if (result.isPresent()) {
            return ResponseEntity.ok(result.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @ExceptionHandler(OrderNotCancellableException.class)
    public ResponseEntity<Map<String, String>> handleNotCancellable(OrderNotCancellableException exception) {
        return ResponseEntity.status(409).body(Map.of(
                "code", "ORDER_NOT_CANCELLABLE", "message", exception.getMessage()));
    }

    /** 503 明确提示 MySQL 已取消、Redis 尚未确认，而不是伪装成全部成功。 */
    @ExceptionHandler(OrderStockReleaseException.class)
    public ResponseEntity<Map<String, String>> handleStockReleasePending(OrderStockReleaseException exception) {
        return ResponseEntity.status(503).body(Map.of(
                "code", "STOCK_RELEASE_PENDING", "message", exception.getMessage()));
    }

    /** 无请求体的模拟支付；身份取自 JWT，金额取自原订单，均不接受前端指定。 */
    @PostMapping("/{id}/pay")
    public ResponseEntity<SeckillOrder> pay(@PathVariable Long id, HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ID);
        Optional<SeckillOrder> result = orderService.payMyOrder(id, currentUserId);
        if (result.isPresent()) {
            return ResponseEntity.ok(result.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /** 409 表示请求与当前订单状态冲突，便于前端展示具体原因。 */
    @ExceptionHandler(OrderNotPayableException.class)
    public ResponseEntity<Map<String, String>> handleNotPayable(OrderNotPayableException exception) {
        return ResponseEntity.status(409).body(Map.of(
                "code", "ORDER_NOT_PAYABLE", "message", exception.getMessage()));
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
