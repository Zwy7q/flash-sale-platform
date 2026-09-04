package com.example.flashsale.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理员身份拦截器的单元测试。
 *
 * 这里不启动 Spring，只验证拦截器根据 request 中的用户 ID
 * 返回放行、401 或 403 的规则。
 */
class AdminAuthorizationInterceptorTest {

    @Test
    void shouldRejectWhenCurrentUserIdIsMissing() {
        AdminAuthorizationInterceptor interceptor =
                new AdminAuthorizationInterceptor(1L);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ID))
                .thenReturn(null);

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void shouldRejectWhenCurrentUserIsNotAdmin() {
        AdminAuthorizationInterceptor interceptor =
                new AdminAuthorizationInterceptor(1L);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ID))
                .thenReturn(2L);

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void shouldAllowWhenCurrentUserIsAdmin() {
        AdminAuthorizationInterceptor interceptor =
                new AdminAuthorizationInterceptor(1L);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ID))
                .thenReturn(1L);

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertTrue(allowed);
    }
}
