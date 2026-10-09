package com.example.flashsale.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 健康检查接口的第一个测试。
 *
 * WebMvcTest 只加载 Web 层相关组件，不会启动 MySQL、Redis 等外部服务，
 * 适合在项目早期快速验证 Controller 行为。
 */
@WebMvcTest(HealthController.class)
// Web 切片不会自动加载普通 Service，显式导入鉴权依赖，仍不连接数据库或 Redis。
@org.springframework.context.annotation.Import({com.example.flashsale.auth.JwtAuthenticationInterceptor.class,
        com.example.flashsale.auth.JwtTokenService.class, com.example.flashsale.auth.AdminAuthorizationInterceptor.class})
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }
}
