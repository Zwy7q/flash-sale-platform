package com.example.flashsale.auth;

import com.example.flashsale.user.AppUser;
import com.example.flashsale.user.UserResponse;
import com.example.flashsale.user.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录认证接口。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 校验用户名和密码。
     */
    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@RequestBody LoginRequest request) {
        AppUser user = userService.login(request.getUsername(), request.getPassword());
        UserResponse response = new UserResponse(
                user.getId(), user.getUsername(), user.getNickname(), user.getStatus());
        return ResponseEntity.ok(response);
    }
}
