package com.example.flashsale.product;

import com.example.flashsale.auth.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理员新增商品接口测试。
 * 使用真实 MySQL，验证完整的 HTTP → Service → Repository 链路。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper mapper;
    private final List<Long> fixtureIds = new ArrayList<>();

    @Test
    void adminShouldReadCompleteProductsIncludingOffSaleFromDatabase() throws Exception {
        Long id = fixture(LocalDateTime.now().plusDays(1).withNano(0));
        jdbc.update("UPDATE app_product SET status = 0, description = '管理详情', seckill_stock = 17 WHERE id = ?", id);
        // 用户商品缓存故意放入旧值；管理员查询必须读 MySQL，不受该缓存影响。
        redis.opsForValue().set("flash-sale:product:" + id, "旧缓存");
        String token = jwtTokenService.generateToken(1L);
        String json = mockMvc.perform(get("/api/admin/products").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Product[] products = mapper.readValue(json, Product[].class);
        Product found = null;
        for (Product product : products) {
            if (product.getId().equals(id)) { found = product; }
        }
        assertNotNull(found);
        assertEquals(0, found.getStatus());
        assertEquals(17, found.getSeckillStock());
        assertEquals("管理详情", found.getDescription());
        assertNotNull(found.getPrice());

        mockMvc.perform(get("/api/admin/products/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(0))
                .andExpect(jsonPath("$.seckillStock").value(17))
                .andExpect(jsonPath("$.description").value("管理详情"));
    }

    @Test
    void missingAdminProductShouldReturn404() throws Exception {
        mockMvc.perform(get("/api/admin/products/-1")
                .header("Authorization", "Bearer " + jwtTokenService.generateToken(1L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminReadRoutesShouldRequireJwtAndAdminIdentity() throws Exception {
        for (String path : List.of("/api/admin/products", "/api/admin/products/1")) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(path).header("Authorization", "Bearer " + jwtTokenService.generateToken(2L)))
                    .andExpect(status().isForbidden());
        }
    }

    private Long fixture(LocalDateTime start) {
        String name = "库存规则测试-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_product (name, price, seckill_price, stock, seckill_stock,
                    status, seckill_start_at, seckill_end_at) VALUES (?, 299, 129, 100, 20, 1, ?, ?)
                """, name, start, start.plusHours(2));
        Long id = jdbc.queryForObject("SELECT id FROM app_product WHERE name = ?", Long.class, name);
        fixtureIds.add(id);
        return id;
    }

    private String editBody(LocalDateTime start, int stock) throws Exception {
        UpdateProductRequest request = new UpdateProductRequest();
        request.setName("修改后的商品");
        request.setPrice(new BigDecimal("399.00"));
        request.setSeckillPrice(new BigDecimal("129.00"));
        request.setStock(100);
        request.setSeckillStock(stock);
        request.setStatus(1);
        request.setSeckillStartAt(start);
        request.setSeckillEndAt(start.plusHours(2));
        return mapper.writeValueAsString(request);
    }

    @AfterEach
    void cleanupFixtureRedis() {
        // MySQL 测试事务自动回滚；Redis 不参加该事务，所以只清理自己的商品 Key。
        for (Long id : fixtureIds) {
            redis.delete("flash-sale:stock:" + id);
            redis.delete("flash-sale:product:" + id);
        }
    }

    @Test
    void shouldCreateProductWhenAdminTokenIsValid() throws Exception {
        String adminToken = jwtTokenService.generateToken(1L);

        mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "接口新增商品",
                                  "description": "用于接口测试",
                                  "price": 199.00,
                                  "seckillPrice": 129.00,
                                  "stock": 100,
                                  "seckillStock": 50,
                                  "status": 1,
                                  "seckillStartAt": "2026-09-05T10:00:00",
                                  "seckillEndAt": "2026-09-05T12:00:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("接口新增商品"))
                .andExpect(jsonPath("$.seckillStock").value(50));
    }

    @Test
    void shouldUpdateAllProductFieldsWhenAdminTokenIsValid() throws Exception {
        String adminToken = jwtTokenService.generateToken(1L);
        LocalDateTime start = LocalDateTime.now().plusDays(1).withNano(0);
        Long id = fixture(start);
        redis.opsForValue().set("flash-sale:stock:" + id, "20");

        mockMvc.perform(put("/api/admin/products/{id}", id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "修改后的演示键盘",
                                  "description": "修改后的描述",
                                  "price": 399.00,
                                  "seckillPrice": 299.00,
                                  "stock": 300,
                                  "seckillStock": 120,
                                  "status": 1,
                                  "seckillStartAt": "%s",
                                  "seckillEndAt": "%s"
                                }
                                """.formatted(start, start.plusHours(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("修改后的演示键盘"))
                .andExpect(jsonPath("$.price").value(399.00))
                .andExpect(jsonPath("$.seckillStock").value(120));
        assertNull(redis.opsForValue().get("flash-sale:stock:" + id));
        assertEquals(120, jdbc.queryForObject("SELECT seckill_stock FROM app_product WHERE id = ?", Integer.class, id));
    }

    @Test
    void shouldReturnConflictForStockChangeAfterStart() throws Exception {
        LocalDateTime start = LocalDateTime.now().minusHours(1).withNano(0);
        Long id = fixture(start);
        mockMvc.perform(put("/api/admin/products/{id}", id)
                .header("Authorization", "Bearer " + jwtTokenService.generateToken(1L))
                .contentType(MediaType.APPLICATION_JSON).content(editBody(start, 30)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_CHANGE_NOT_ALLOWED"));
        assertEquals(20, jdbc.queryForObject("SELECT seckill_stock FROM app_product WHERE id = ?", Integer.class, id));
    }

    @Test
    void shouldAllowNameChangeAfterStartIfStockAndTimeStaySame() throws Exception {
        LocalDateTime start = LocalDateTime.now().minusHours(1).withNano(0);
        Long id = fixture(start);
        mockMvc.perform(put("/api/admin/products/{id}", id)
                .header("Authorization", "Bearer " + jwtTokenService.generateToken(1L))
                .contentType(MediaType.APPLICATION_JSON).content(editBody(start, 20)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("修改后的商品"));
    }

    @Test
    void shouldReturnNotFoundWhenUpdatingMissingProduct() throws Exception {
        String adminToken = jwtTokenService.generateToken(1L);

        mockMvc.perform(put("/api/admin/products/999999")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "不存在的商品",
                                  "description": "测试",
                                  "price": 99.00,
                                  "seckillPrice": 59.00,
                                  "stock": 10,
                                  "seckillStock": 5,
                                  "status": 1,
                                  "seckillStartAt": "2026-09-06T10:00:00",
                                  "seckillEndAt": "2026-09-06T12:00:00"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldTakeProductOffSaleWhenAdminChangesStatus() throws Exception {
        String adminToken = jwtTokenService.generateToken(1L);

        mockMvc.perform(put("/api/admin/products/1/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value(0));
    }

    @Test
    void shouldReturnNotFoundWhenChangingStatusOfMissingProduct() throws Exception {
        String adminToken = jwtTokenService.generateToken(1L);

        mockMvc.perform(put("/api/admin/products/999999/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": 0
                                }
                                """))
                .andExpect(status().isNotFound());
    }
}
