package com.example.flashsale.product;

import com.example.flashsale.auth.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理员新增商品接口测试。
 * 使用真实 MySQL，验证完整的 HTTP → Service → Repository 链路。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

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
}
