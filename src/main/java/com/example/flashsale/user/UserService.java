package com.example.flashsale.user;

import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 用户业务层。
 *
 * 当前只实现查询。注册、登录和 JWT 会在后续阶段逐步加入这里。
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<AppUser> findById(Long id) {
        return userRepository.findById(id);
    }
}
