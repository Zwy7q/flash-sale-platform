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
    private final JwtTokenService jwtTokenService;

    public AuthController(UserService userService, JwtTokenService jwtTokenService) {
        this.userService = userService;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * 校验用户名和密码。
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        AppUser user = userService.login(request.getUsername(), request.getPassword());
        UserResponse response = new UserResponse(
                user.getId(), user.getUsername(), user.getNickname(), user.getStatus());
        String token = jwtTokenService.generateToken(user.getId());
        return ResponseEntity.ok(new LoginResponse(token, response));
    }
}
