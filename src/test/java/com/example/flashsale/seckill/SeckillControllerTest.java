package com.example.flashsale.seckill;

import com.example.flashsale.auth.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 秒杀接口集成测试：验证 HTTP、JWT、Service 和真实 Redis 的完整链路。 */
@SpringBootTest
@AutoConfigureMockMvc
class SeckillControllerTest {

    private static final Long PRODUCT_ID = 1L;
    private static final Long USER_ID = 990001L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void clearSeckillRedisData() {
        // 每次测试都清理 Redis，避免上次测试的库存或购买标记影响本次测试。
        stringRedisTemplate.delete("flash-sale:stock:" + PRODUCT_ID);
        stringRedisTemplate.delete("flash-sale:purchase:" + PRODUCT_ID + ":" + USER_ID);
    }

    @Test
    void shouldAcceptSeckillRequestWhenJwtIsValid() throws Exception {
        String token = jwtTokenService.generateToken(USER_ID);

        mockMvc.perform(post("/api/seckill/products/{productId}", PRODUCT_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.result").value("SUCCESS"));
    }

    @Test
    void shouldReturnUnauthorizedWhenSeckillRequestHasNoJwt() throws Exception {
        mockMvc.perform(post("/api/seckill/products/{productId}", PRODUCT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectRepeatedSeckillRequestFromSameUser() throws Exception {
        String token = jwtTokenService.generateToken(USER_ID);

        mockMvc.perform(post("/api/seckill/products/{productId}", PRODUCT_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/seckill/products/{productId}", PRODUCT_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.result").value("DUPLICATE_PURCHASE"));
    }
}
