package com.goride.user.application.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.exception.DuplicateResourceException;
import com.goride.common.exception.ForbiddenException;
import com.goride.common.exception.UnauthorizedException;
import com.goride.user.application.dto.request.LoginRequest;
import com.goride.user.application.dto.request.RefreshTokenRequest;
import com.goride.user.application.dto.request.RegisterRequest;
import com.goride.user.application.dto.response.AuthResponse;
import com.goride.user.domain.model.RoleCode;
import com.goride.user.domain.model.UserStatus;
import com.goride.user.infrastructure.persistence.entity.OutboxEventEntity;
import com.goride.user.infrastructure.persistence.entity.RefreshTokenEntity;
import com.goride.user.infrastructure.persistence.entity.RoleEntity;
import com.goride.user.infrastructure.persistence.entity.UserEntity;
import com.goride.user.infrastructure.persistence.repository.OutboxEventRepository;
import com.goride.user.infrastructure.persistence.repository.RefreshTokenRepository;
import com.goride.user.infrastructure.persistence.repository.RoleRepository;
import com.goride.user.infrastructure.persistence.repository.UserRepository;
import com.goride.user.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
                3600000,
                604800000
        );
        authService = new AuthServiceImpl(
                userRepository,
                roleRepository,
                refreshTokenRepository,
                outboxEventRepository,
                passwordEncoder,
                jwtTokenProvider,
                objectMapper
        );
    }

    @Test
    @DisplayName("Register new user successfully records Outbox event and saves hashed password")
    void register_Success() {
        RegisterRequest request = new RegisterRequest("+84912345678", "user@goride.com", "Password123", "Nguyen Van A", RoleCode.PASSENGER);
        RoleEntity passengerRole = new RoleEntity((short) 1, "PASSENGER");

        when(userRepository.existsByPhone(request.getPhone())).thenReturn(false);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(roleRepository.findByCode("PASSENGER")).thenReturn(Optional.of(passengerRole));
        when(passwordEncoder.encode(request.getPassword())).thenReturn("$2a$10$hashedpassword");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID correlationId = UUID.randomUUID();
        AuthResponse response = authService.register(request, correlationId);

        assertNotNull(response);
        assertNotNull(response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertEquals("+84912345678", response.getUser().getPhone());

        // Verify Outbox Event created
        verify(outboxEventRepository, times(1)).save(argThat(event ->
                "USER".equals(event.getAggregateType()) &&
                "user.registered".equals(event.getEventType()) &&
                correlationId.equals(event.getCorrelationId())
        ));

        // Verify RefreshToken saved
        verify(refreshTokenRepository, times(1)).save(any(RefreshTokenEntity.class));
    }

    @Test
    @DisplayName("Register with duplicate phone throws DuplicateResourceException")
    void register_DuplicatePhone_ThrowsException() {
        RegisterRequest request = new RegisterRequest("+84912345678", "user@goride.com", "Password123", "Nguyen Van A", RoleCode.PASSENGER);
        when(userRepository.existsByPhone(request.getPhone())).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(request, null));
        verify(userRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Register with duplicate email throws DuplicateResourceException")
    void register_DuplicateEmail_ThrowsException() {
        RegisterRequest request = new RegisterRequest("+84912345678", "user@goride.com", "Password123", "Nguyen Van A", RoleCode.PASSENGER);
        when(userRepository.existsByPhone(request.getPhone())).thenReturn(false);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(request, null));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Login with valid phone and password returns tokens")
    void login_Success_Phone() {
        LoginRequest request = new LoginRequest("+84912345678", "Password123");
        UserEntity user = new UserEntity(UUID.randomUUID(), "+84912345678", "user@goride.com", "$2a$10$hashedpassword", "Nguyen Van A", UserStatus.ACTIVE);
        user.addRole(new RoleEntity((short) 1, "PASSENGER"));

        when(userRepository.findByPhone("+84912345678")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123", "$2a$10$hashedpassword")).thenReturn(true);

        AuthResponse response = authService.login(request);
        assertNotNull(response);
        assertNotNull(response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertEquals("+84912345678", response.getUser().getPhone());

        verify(refreshTokenRepository, times(1)).save(any(RefreshTokenEntity.class));
    }

    @Test
    @DisplayName("Login with wrong password throws UnauthorizedException")
    void login_WrongPassword_ThrowsException() {
        LoginRequest request = new LoginRequest("+84912345678", "WrongPassword");
        UserEntity user = new UserEntity(UUID.randomUUID(), "+84912345678", "user@goride.com", "$2a$10$hashedpassword", "Nguyen Van A", UserStatus.ACTIVE);

        when(userRepository.findByPhone("+84912345678")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "$2a$10$hashedpassword")).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> authService.login(request));
    }

    @Test
    @DisplayName("Login with non-active user account throws ForbiddenException")
    void login_NonActiveAccount_ThrowsForbiddenException() {
        LoginRequest request = new LoginRequest("+84912345678", "Password123");
        UserEntity user = new UserEntity(UUID.randomUUID(), "+84912345678", "user@goride.com", "$2a$10$hashedpassword", "Nguyen Van A", UserStatus.LOCKED);

        when(userRepository.findByPhone("+84912345678")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123", "$2a$10$hashedpassword")).thenReturn(true);

        assertThrows(ForbiddenException.class, () -> authService.login(request));
    }

    @Test
    @DisplayName("Refresh token rotates token and returns new tokens")
    void refreshToken_Success() {
        String rawToken = "raw-refresh-token-12345";
        String tokenHash = jwtTokenProvider.hashToken(rawToken);

        UUID userId = UUID.randomUUID();
        RefreshTokenEntity existingToken = new RefreshTokenEntity(UUID.randomUUID(), userId, tokenHash, Instant.now().plusSeconds(3600));

        UserEntity user = new UserEntity(userId, "+84912345678", "user@goride.com", "hash", "Nguyen Van A", UserStatus.ACTIVE);
        user.addRole(new RoleEntity((short) 1, "PASSENGER"));

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(existingToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        AuthResponse response = authService.refreshToken(new RefreshTokenRequest(rawToken));

        assertNotNull(response);
        assertNotNull(response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertNotNull(existingToken.getRevokedAt()); // Old token revoked

        verify(refreshTokenRepository, times(1)).save(existingToken);
        verify(refreshTokenRepository, times(1)).save(argThat(token -> !token.getId().equals(existingToken.getId())));
    }

    @Test
    @DisplayName("Refresh token with revoked token throws UnauthorizedException")
    void refreshToken_RevokedToken_ThrowsException() {
        String rawToken = "raw-refresh-token-12345";
        String tokenHash = jwtTokenProvider.hashToken(rawToken);

        RefreshTokenEntity existingToken = new RefreshTokenEntity(UUID.randomUUID(), UUID.randomUUID(), tokenHash, Instant.now().plusSeconds(3600));
        existingToken.setRevokedAt(Instant.now().minusSeconds(10)); // already revoked

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(existingToken));

        assertThrows(UnauthorizedException.class, () -> authService.refreshToken(new RefreshTokenRequest(rawToken)));
    }
}

