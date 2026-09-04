package com.example.flashsale.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        mockMvc.perform(get("/api/admin/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn403WhenAdminRequestUsesNormalUserToken() throws Exception {
        String normalUserToken = jwtTokenService.generateToken(2L);

        mockMvc.perform(get("/api/admin/products")
                        .header("Authorization", "Bearer " + normalUserToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldPassInterceptorsForAdminToken() throws Exception {
        String adminToken = jwtTokenService.generateToken(1L);

        // 管理员 Controller 已存在，但当前只实现 POST，所以 GET 会得到 405。
        mockMvc.perform(get("/api/admin/products")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isMethodNotAllowed());
    }
}
