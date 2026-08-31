package com.example.flashsale.user;

import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

/**
 * 用户业务层。
 *
 * 当前实现用户查询和注册。登录和 JWT 会在后续阶段逐步加入这里。
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<AppUser> findById(Long id) {
        return userRepository.findById(id);
    }

    /**
     * 注册用户：检查用户名、加密密码、保存用户。
     */
    public AppUser register(String username, String rawPassword, String nickname) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new UsernameAlreadyExistsException(username);
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setNickname(nickname);
        user.setStatus(1);

        userRepository.insert(user);
        return userRepository.findByUsername(username).orElseThrow();
    }
}
