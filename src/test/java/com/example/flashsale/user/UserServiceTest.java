package com.example.flashsale.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * 用户 Service 层单元测试。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldFindUserById() {
        AppUser expectedUser = new AppUser();
        expectedUser.setId(1L);
        expectedUser.setUsername("demo_user");
        when(userRepository.findById(1L)).thenReturn(Optional.of(expectedUser));

        AppUser actualUser = userService.findById(1L).orElseThrow();

        assertEquals("demo_user", actualUser.getUsername());
    }

    @Test
    void shouldLoginWhenPasswordMatches() {
        AppUser expectedUser = new AppUser();
        expectedUser.setUsername("demo_user");
        expectedUser.setPasswordHash("encoded-password");
        expectedUser.setStatus(1);
        when(userRepository.findByUsername("demo_user")).thenReturn(Optional.of(expectedUser));
        when(passwordEncoder.matches("plain-password", "encoded-password")).thenReturn(true);

        AppUser actualUser = userService.login("demo_user", "plain-password");

        assertEquals("demo_user", actualUser.getUsername());
    }
}
