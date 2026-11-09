package com.goride.user;

import com.goride.user.infrastructure.persistence.repository.OutboxEventRepository;
import com.goride.user.infrastructure.persistence.repository.RefreshTokenRepository;
import com.goride.user.infrastructure.persistence.repository.RoleRepository;
import com.goride.user.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class UserServiceApplicationTests {

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private RoleRepository roleRepository;

    @MockBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockBean
    private OutboxEventRepository outboxEventRepository;

    @Test
    void contextLoads() {
    }
}
