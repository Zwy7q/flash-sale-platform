package com.example.flashsale.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 登录、注册和查询共用转换，避免同一用户在不同接口返回不同的管理员标识。 */
@Component
public class UserResponseFactory {
    private final Long adminUserId;

    public UserResponseFactory(@Value("${app.admin-user-id}") Long adminUserId) {
        this.adminUserId = adminUserId;
    }

    public UserResponse from(AppUser user) {
        // 与管理员拦截器读取同一份后端配置，不由前端硬编码用户编号。
        boolean admin = adminUserId.equals(user.getId());
        return new UserResponse(user.getId(), user.getUsername(), user.getNickname(), user.getStatus(), admin);
    }
}
