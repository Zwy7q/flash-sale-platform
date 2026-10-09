package com.example.flashsale.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 鉴权拦截器。
 *
 * Controller 执行之前会先经过这里：
 * 1. 读取 Authorization 请求头；
 * 2. 检查 Bearer 格式；
 * 3. 校验 JWT 签名和过期时间；
 * 4. 把 Token 中的用户 ID 放入 request，交给 Controller 使用。
 */
@Component
public class JwtAuthenticationInterceptor implements HandlerInterceptor {

    public static final String CURRENT_USER_ID = "currentUserId";

    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationInterceptor(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {

        if (isPublicEndpoint(request)) {
            // 登录和注册本来就发生在取得 Token 之前，不能要求它们先通过 JWT 校验。
            return true;
        }

        String authorization = request.getHeader("Authorization");

        // 标准格式是：Bearer 空格 JWT 字符串。
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        String token = authorization.substring("Bearer ".length()).trim();

        try {
            Long userId = jwtTokenService.parseUserId(token);
            // request 只在当前请求生命周期内存在，适合传递当前用户信息。
            request.setAttribute(CURRENT_USER_ID, userId);
            return true;
        } catch (RuntimeException exception) {
            // 签名错误、格式错误或过期都会进入这里。
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
    }

    /** 只公开明确的三个入口，不按 /api/users/** 或 /api/auth/** 整段放行。 */
    private boolean isPublicEndpoint(HttpServletRequest request) {
        String method = request.getMethod();
        // 去掉应用 context-path，得到和 Controller 路由一致的应用内部路径。
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if ("GET".equals(method) && "/api/health".equals(path)) {
            return true;
        }
        if ("POST".equals(method) && "/api/auth/login".equals(path)) {
            return true;
        }
        if ("POST".equals(method) && "/api/users".equals(path)) {
            return true;
        }
        return false;
    }
}
