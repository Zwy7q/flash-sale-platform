package com.example.flashsale.user;

import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.example.flashsale.auth.InvalidCredentialsException;

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

    /**
     * 登录：查询用户并使用 BCrypt 校验明文密码和数据库密文是否匹配。
     */
    public AppUser login(String username, String rawPassword) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new InvalidCredentialsException();
        }

        return user;
    }
}
