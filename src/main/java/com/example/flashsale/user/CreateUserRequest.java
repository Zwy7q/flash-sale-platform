package com.example.flashsale.user;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 注册请求对象，只接收注册所需字段。
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateUserRequest {
    private String username;
    private String password;
    private String nickname;
}
