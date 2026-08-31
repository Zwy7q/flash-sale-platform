package com.example.flashsale.auth;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 登录请求对象。
 */
@Getter
@Setter
@NoArgsConstructor
public class LoginRequest {
    private String username;
    private String password;
}
