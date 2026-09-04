package com.example.flashsale.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理员身份拦截器。
 *
 * JWT 拦截器只负责确认“用户是谁”；
 * 本拦截器再判断这个用户是否拥有管理员权限。
 */
@Component
public class AdminAuthorizationInterceptor implements HandlerInterceptor {

    private final Long adminUserId;

    /**
     * 从 application.yml 读取管理员用户 ID。
     */
    public AdminAuthorizationInterceptor(
            @Value("${app.admin-user-id}") Long adminUserId) {
        this.adminUserId = adminUserId;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {

        Object attribute = request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ID);

        // 没有用户 ID，说明 JWT 拦截没有完成认证，返回 401。
        if (!(attribute instanceof Long currentUserId)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        // 用户已经登录，但不是管理员，返回 403。
        if (!adminUserId.equals(currentUserId)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }

        // 当前用户就是管理员，允许继续执行 Controller。
        return true;
    }
}
