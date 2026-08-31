package com.example.flashsale.user;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 平台用户领域对象。
 *
 * 类名使用 AppUser，避免和 Spring Security 或其他框架中的 User 类型混淆。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppUser {

    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
