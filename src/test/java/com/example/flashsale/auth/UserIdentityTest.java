package com.example.flashsale.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 新用户使用事务回滚，不改演示账号；角色展示不改变后端权限校验。 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserIdentityTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtTokenService jwt;

    @Test
    void ordinaryUserShouldRemainNonAdminThroughRegistrationLoginAndProfile() throws Exception {
        String name = "identity-" + UUID.randomUUID();
        mvc.perform(post("/api/users").contentType("application/json").content("""
                {"username":"%s","password":"test-password","nickname":"身份测试"}
                """.formatted(name)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.isAdmin").value(false));
        String response = mvc.perform(post("/api/auth/login").contentType("application/json").content("""
                {"username":"%s","password":"test-password"}
                """.formatted(name)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.isAdmin").value(false))
                .andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(response).get("token").asText();
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isAdmin").value(false))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/admin/analytics/overview").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminProfileShouldExposeAdminFlag() throws Exception {
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + jwt.generateToken(1L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isAdmin").value(true));
    }
}
