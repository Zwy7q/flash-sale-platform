package com.example.flashsale.auth;

import com.example.flashsale.user.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 登录成功响应。
 */
@Getter
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private UserResponse user;
}
