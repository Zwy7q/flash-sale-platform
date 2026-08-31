package com.example.flashsale.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
