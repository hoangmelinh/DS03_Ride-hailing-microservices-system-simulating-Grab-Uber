package com.goride.user.application.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.exception.DuplicateResourceException;
import com.goride.common.exception.EntityNotFoundException;
import com.goride.common.exception.ForbiddenException;
import com.goride.common.exception.UnauthorizedException;
import com.goride.user.application.dto.request.LoginRequest;
import com.goride.user.application.dto.request.RefreshTokenRequest;
import com.goride.user.application.dto.request.RegisterRequest;
import com.goride.user.application.dto.response.AuthResponse;
import com.goride.user.application.dto.response.UserDto;
import com.goride.user.application.service.AuthService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of AuthService handling registration, login, and token operations.
 * Implements Transactional Outbox pattern on registration.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    public AuthServiceImpl(UserRepository userRepository,
                           RoleRepository roleRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           OutboxEventRepository outboxEventRepository,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider jwtTokenProvider,
                           ObjectMapper objectMapper) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository must not be null");
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository, "refreshTokenRepository must not be null");
        this.outboxEventRepository = Objects.requireNonNull(outboxEventRepository, "outboxEventRepository must not be null");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
        this.jwtTokenProvider = Objects.requireNonNull(jwtTokenProvider, "jwtTokenProvider must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request, UUID correlationId) {
        log.info("Processing user registration for phone: {}", request.getPhone());

        String phone = request.getPhone().trim();
        if (userRepository.existsByPhone(phone)) {
            throw new DuplicateResourceException("Phone number is already registered: " + phone);
        }

        String email = StringUtils.hasText(request.getEmail()) ? request.getEmail().trim().toLowerCase() : null;
        if (email != null && userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email is already registered: " + email);
        }

        RoleCode targetRole = request.getRole() != null ? request.getRole() : RoleCode.PASSENGER;
        RoleEntity roleEntity = roleRepository.findByCode(targetRole.name())
                .orElseGet(() -> {
                    short roleId = (short) (targetRole == RoleCode.PASSENGER ? 1 : (targetRole == RoleCode.DRIVER ? 2 : 3));
                    return roleRepository.save(new RoleEntity(roleId, targetRole.name()));
                });

        UUID userId = UUID.randomUUID();
        String hashedPassword = passwordEncoder.encode(request.getPassword());
        UserEntity user = new UserEntity(userId, phone, email, hashedPassword, request.getFullName().trim(), UserStatus.ACTIVE);
        user.addRole(roleEntity);

        user = userRepository.save(user);
        log.info("Saved user with id: {} and role: {}", user.getId(), targetRole);

        // Record Transactional Outbox Event
        recordUserRegisteredOutboxEvent(user, targetRole, correlationId);

        // Issue Access & Refresh Tokens
        return createAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String identifier = request.getIdentifier().trim();
        log.info("Processing login for identifier: {}", identifier);

        UserEntity user = userRepository.findByPhone(identifier)
                .or(() -> userRepository.findByEmail(identifier.toLowerCase()))
                .orElseThrow(() -> new UnauthorizedException("Invalid phone/email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Password mismatch for identifier: {}", identifier);
            throw new UnauthorizedException("Invalid phone/email or password");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Login attempt for non-active user: {}, status: {}", user.getId(), user.getStatus());
            throw new ForbiddenException("Account is " + user.getStatus());
        }

        return createAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String rawToken = request.getRefreshToken().trim();
        String tokenHash = jwtTokenProvider.hashToken(rawToken);

        RefreshTokenEntity tokenEntity = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid or non-existent refresh token"));

        if (tokenEntity.getRevokedAt() != null) {
            log.warn("Attempt to use revoked refresh token for user: {}", tokenEntity.getUserId());
            throw new UnauthorizedException("Refresh token has been revoked");
        }

        if (tokenEntity.getExpiresAt().isBefore(Instant.now())) {
            log.warn("Attempt to use expired refresh token for user: {}", tokenEntity.getUserId());
            throw new UnauthorizedException("Refresh token has expired");
        }

        UserEntity user = userRepository.findById(tokenEntity.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found for provided refresh token"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException("Account is " + user.getStatus());
        }

        // Token Rotation: revoke old token and create a new refresh token
        tokenEntity.setRevokedAt(Instant.now());
        refreshTokenRepository.save(tokenEntity);

        return createAuthResponse(user);
    }

    private void recordUserRegisteredOutboxEvent(UserEntity user, RoleCode role, UUID correlationId) {
        UUID eventId = UUID.randomUUID();
        UUID corrId = correlationId != null ? correlationId : UUID.randomUUID();

        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("userId", user.getId().toString());
        payloadMap.put("phone", user.getPhone());
        payloadMap.put("email", user.getEmail());
        payloadMap.put("fullName", user.getFullName());
        payloadMap.put("role", role.name());
        payloadMap.put("status", user.getStatus().name());
        payloadMap.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : Instant.now().toString());

        try {
            String payloadJson = objectMapper.writeValueAsString(payloadMap);
            OutboxEventEntity outboxEvent = new OutboxEventEntity(
                    eventId,
                    "USER",
                    user.getId(),
                    "user.registered",
                    payloadJson,
                    corrId,
                    null,
                    Instant.now()
            );
            outboxEventRepository.save(outboxEvent);
            log.info("Recorded Outbox event user.registered with eventId: {} for user: {}", eventId, user.getId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize user.registered outbox payload for user: {}", user.getId(), e);
            throw new IllegalStateException("Failed to serialize outbox event payload", e);
        }
    }

    private AuthResponse createAuthResponse(UserEntity user) {
        Set<String> roleCodes = user.getRoles().stream()
                .map(RoleEntity::getCode)
                .collect(Collectors.toSet());

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getPhone(),
                user.getEmail(),
                roleCodes
        );

        String rawRefreshToken = jwtTokenProvider.generateRefreshToken();
        String hashedRefreshToken = jwtTokenProvider.hashToken(rawRefreshToken);
        Instant refreshExpiresAt = Instant.now().plusMillis(jwtTokenProvider.getRefreshTokenExpirationMs());

        RefreshTokenEntity refreshTokenEntity = new RefreshTokenEntity(
                UUID.randomUUID(),
                user.getId(),
                hashedRefreshToken,
                refreshExpiresAt
        );
        refreshTokenRepository.save(refreshTokenEntity);

        UserDto userDto = mapToUserDto(user);
        long expiresInSeconds = jwtTokenProvider.getAccessTokenExpirationMs() / 1000;

        return new AuthResponse(accessToken, rawRefreshToken, expiresInSeconds, userDto);
    }

    private UserDto mapToUserDto(UserEntity user) {
        Set<String> roles = user.getRoles().stream()
                .map(RoleEntity::getCode)
                .collect(Collectors.toSet());

        return new UserDto(
                user.getId(),
                user.getPhone(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus(),
                roles,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
