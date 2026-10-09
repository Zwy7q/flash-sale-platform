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
                // 所有业务 API 默认需要登录，未来新增接口也自动受保护。
                // 三个公开入口在 JWT 拦截器内按“方法 + 完整路径”精确放行。
                .addPathPatterns("/api/**")
                .order(0); // 数字越小越先执行：先认证身份，把用户 ID 放入 request。

        registry.addInterceptor(adminAuthorizationInterceptor)
                // 只有管理员路径需要额外进行管理员身份判断。
                .addPathPatterns("/api/admin/**")
                .order(1); // 后授权：只在 JWT 通过之后检查管理员身份。
    }
}
