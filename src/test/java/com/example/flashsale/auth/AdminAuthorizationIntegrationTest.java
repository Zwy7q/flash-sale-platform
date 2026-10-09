package com.example.flashsale.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理员路径的 Spring MVC 集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Test
    void shouldReturn401WhenAdminRequestHasNoToken() throws Exception {
        mockMvc.perform(put("/api/admin/products/-1/status").contentType("application/json").content("{\"status\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn403WhenAdminRequestUsesNormalUserToken() throws Exception {
        String normalUserToken = jwtTokenService.generateToken(2L);

        mockMvc.perform(put("/api/admin/products/-1/status").contentType("application/json").content("{\"status\":1}")
                        .header("Authorization", "Bearer " + normalUserToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldPassInterceptorsForAdminToken() throws Exception {
        String adminToken = jwtTokenService.generateToken(1L);

        // 使用真实存在的 PUT 路由。通过鉴权后，因为商品 -1 不存在而返回 404，不修改业务数据。
        mockMvc.perform(put("/api/admin/products/-1/status").contentType("application/json").content("{\"status\":1}")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
