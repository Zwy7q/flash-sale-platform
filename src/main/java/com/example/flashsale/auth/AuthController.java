package com.example.flashsale.auth;

import com.example.flashsale.user.AppUser;
import com.example.flashsale.user.UserResponse;
import com.example.flashsale.user.UserService;
import com.example.flashsale.user.UserResponseFactory;
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
    private final UserResponseFactory responseFactory;

    public AuthController(UserService userService, JwtTokenService jwtTokenService,
                          UserResponseFactory responseFactory) {
        this.userService = userService;
        this.jwtTokenService = jwtTokenService;
        this.responseFactory = responseFactory;
    }

    /**
     * 校验用户名和密码。
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        AppUser user = userService.login(request.getUsername(), request.getPassword());
        UserResponse response = responseFactory.from(user);
        String token = jwtTokenService.generateToken(user.getId());
        return ResponseEntity.ok(new LoginResponse(token, response));
    }
}
