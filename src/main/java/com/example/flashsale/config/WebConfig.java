package com.example.flashsale.config;

import com.example.flashsale.auth.JwtAuthenticationInterceptor;
import com.example.flashsale.auth.AdminAuthorizationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 配置。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JwtAuthenticationInterceptor jwtAuthenticationInterceptor;
    private final AdminAuthorizationInterceptor adminAuthorizationInterceptor;

    public WebConfig(
            JwtAuthenticationInterceptor jwtAuthenticationInterceptor,
            AdminAuthorizationInterceptor adminAuthorizationInterceptor) {
        this.jwtAuthenticationInterceptor = jwtAuthenticationInterceptor;
        this.adminAuthorizationInterceptor = adminAuthorizationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthenticationInterceptor)
                // 管理员接口也必须先完成 JWT 认证，才能把用户 ID 放入 request。
                .addPathPatterns("/api/users/me", "/api/admin/**", "/api/seckill/**");

        registry.addInterceptor(adminAuthorizationInterceptor)
                // 只有管理员路径需要额外进行管理员身份判断。
                .addPathPatterns("/api/admin/**");
    }
}
