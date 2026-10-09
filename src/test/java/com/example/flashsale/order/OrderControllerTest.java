package com.example.flashsale.order;

import com.example.flashsale.auth.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 一个类测试订单查询权限和真实 MySQL 映射；测试数据结束后自动回滚。 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenService jwt;
    @Autowired private OrderRepository repository;
    @Autowired private JdbcTemplate jdbc;
    private Long userId;
    private Long firstOrderId;
    private Long secondOrderId;
    private Long otherOrderId;

    @BeforeEach
    void createIsolatedOrders() {
        // 随机测试用户 ID，避免列表混入演示用户已有订单。当前表没有物理外键。
        userId = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L);
        firstOrderId = repository.createPendingOrder(UUID.randomUUID().toString(),
                userId, 990601L, new BigDecimal("129.00"));
        secondOrderId = repository.createPendingOrder(UUID.randomUUID().toString(),
                userId, 990602L, new BigDecimal("59.00"));
        otherOrderId = repository.createPendingOrder(UUID.randomUUID().toString(),
                userId + 1, 990601L, new BigDecimal("199.00"));
    }

    private String authorization(Long id) {
        return "Bearer " + jwt.generateToken(id);
    }

    @Test
    void shouldPayOwnOrderAndReturnSameResultOnRepeat() throws Exception {
        // 连续支付两次，第二次也应成功，但不能创建新订单或重新改变金额。
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/orders/{id}/pay", firstOrderId)
                    .header("Authorization", authorization(userId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(firstOrderId))
                    .andExpect(jsonPath("$.amount").value(129.00))
                    .andExpect(jsonPath("$.status").value("PAID"));
        }
        assertEquals(OrderStatus.PAID, repository.findByIdAndUserId(firstOrderId, userId).get().getStatus());
    }

    @Test
    void shouldRejectForeignMissingAndUnauthenticatedPayment() throws Exception {
        mvc.perform(post("/api/orders/{id}/pay", otherOrderId)
                .header("Authorization", authorization(userId))).andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/{id}/pay", -1L)
                .header("Authorization", authorization(userId))).andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/{id}/pay", firstOrderId)).andExpect(status().isUnauthorized());
        assertEquals(OrderStatus.PENDING_PAYMENT,
                repository.findByIdAndUserId(otherOrderId, userId + 1).get().getStatus());
    }

    @Test
    void shouldRejectPaymentForClosedOrders() throws Exception {
        // 这里只准备关闭状态数据；取消和超时接口将在后续阶段实现。
        for (String closedStatus : new String[]{"CANCELLED", "PAYMENT_TIMEOUT"}) {
            jdbc.update("UPDATE app_order SET status = ? WHERE id = ?", closedStatus, firstOrderId);
            mvc.perform(post("/api/orders/{id}/pay", firstOrderId)
                    .header("Authorization", authorization(userId)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ORDER_NOT_PAYABLE"));
            assertEquals(closedStatus,
                    repository.findByIdAndUserId(firstOrderId, userId).get().getStatus().name());
        }
    }

    @Test
    void shouldListOnlyCurrentUsersOrdersNewestFirst() throws Exception {
        // 即使前端额外传其他 userId，接口仍只能使用 JWT 中的用户 ID。
        mvc.perform(get("/api/orders").param("userId", String.valueOf(userId + 1))
                .header("Authorization", authorization(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(secondOrderId))
                .andExpect(jsonPath("$[1].id").value(firstOrderId))
                .andExpect(jsonPath("$[0].userId").value(userId))
                .andExpect(jsonPath("$[0].status").value("PENDING_PAYMENT"));
    }

    @Test
    void shouldReturnCurrentUsersOrderDetail() throws Exception {
        mvc.perform(get("/api/orders/{id}", firstOrderId)
                .header("Authorization", authorization(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstOrderId))
                .andExpect(jsonPath("$.productId").value(990601L))
                .andExpect(jsonPath("$.amount").value(129.00))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void shouldHideOtherUsersOrderAndMissingOrder() throws Exception {
        mvc.perform(get("/api/orders/{id}", otherOrderId)
                .header("Authorization", authorization(userId))).andExpect(status().isNotFound());
        mvc.perform(get("/api/orders/{id}", -1L)
                .header("Authorization", authorization(userId))).andExpect(status().isNotFound());
    }

    @Test
    void shouldRequireJwtForListAndDetail() throws Exception {
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders/{id}", firstOrderId)).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnEmptyListForUserWithoutOrders() throws Exception {
        mvc.perform(get("/api/orders").header("Authorization", authorization(userId + 2)))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }
}
