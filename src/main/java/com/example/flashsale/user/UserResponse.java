package com.example.flashsale.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户接口响应对象。
 *
 * 注意：这里故意不包含 passwordHash，避免把密码相关信息返回给客户端。
 */
@Getter
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String username;
    private String nickname;
    private Integer status;
}
