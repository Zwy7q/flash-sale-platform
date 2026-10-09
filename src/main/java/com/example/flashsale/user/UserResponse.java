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
    /** 服务端计算的展示标识，不接受前端指定，不代替管理员拦截器。 */
    private Boolean isAdmin;
}
