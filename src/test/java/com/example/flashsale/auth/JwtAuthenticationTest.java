package com.example.flashsale.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * JWT 拦截器集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
class JwtAuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @org.springframework.beans.factory.annotation.Value("${spring.jwt.secret}")
    private String secret;

    @Test
    void shouldProtectAllExistingBusinessRoutesAndFutureRoutes() throws Exception {
        String[] paths = {"/api/products", "/api/products/1", "/api/users/1",
                "/api/users/me", "/api/orders", "/api/future-feature",
                "/api/auth/login/extra", "/api/health/extra"};
        for (String path : paths) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/seckill/products/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/orders/1/pay")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/orders/1/cancel")).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectExpiredMalformedAndWrongSignatureTokens() throws Exception {
        String expired = new JwtTokenService(secret, -60).generateToken(1L);
        String wrongSignature = new JwtTokenService(
                "different-test-signing-secret-at-least-32-bytes", 3600).generateToken(1L);
        for (String token : new String[]{expired, wrongSignature, "not-a-jwt", ""}) {
            mockMvc.perform(get("/api/products").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void shouldAllowHealthWithoutTokenAndIgnoreStaleTokenOnPublicRoute() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/health").header("Authorization", "Bearer invalid"))
                .andExpect(status().isOk());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void shouldRegisterAndLoginWithoutJwt() throws Exception {
        String username = "jwt-policy-" + java.util.UUID.randomUUID();
        mockMvc.perform(post("/api/users").contentType("application/json")
                .header("Authorization", "Bearer stale-token")
                .content("""
                        {"username":"%s","password":"test-password","nickname":"权限测试用户"}
                        """.formatted(username)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                .content("""
                        {"username":"%s","password":"test-password"}
                        """.formatted(username)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void shouldAllowAuthenticatedProductQueries() throws Exception {
        mockMvc.perform(get("/api/products").header("Authorization", "Bearer " + jwtTokenService.generateToken(1L)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldHideOtherUserProfileEvenWithValidJwt() throws Exception {
        mockMvc.perform(get("/api/users/1").header("Authorization", "Bearer " + jwtTokenService.generateToken(2L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnCurrentUserWithValidToken() throws Exception {
        String token = jwtTokenService.generateToken(1L);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("demo_user"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
}
