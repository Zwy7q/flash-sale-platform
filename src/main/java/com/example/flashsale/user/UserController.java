package com.example.flashsale.user;

import com.example.flashsale.auth.JwtAuthenticationInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户查询接口。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 根据路径中的用户 ID 查询用户。
     * 找到用户返回 200，找不到返回 404。
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> findById(@PathVariable Long id) {
        return userService.findById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 查询当前登录用户。
     *
     * 当前用户 ID 已经由 JWT 拦截器放入 request，不直接相信客户端传入的 ID。
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> currentUser(HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute(JwtAuthenticationInterceptor.CURRENT_USER_ID);

        return userService.findById(currentUserId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 注册用户。
     */
    @PostMapping
    public ResponseEntity<UserResponse> register(@RequestBody CreateUserRequest request) {
        AppUser user = userService.register(
                request.getUsername(),
                request.getPassword(),
                request.getNickname());

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user));
    }

    private UserResponse toResponse(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getNickname(), user.getStatus());
    }
}
