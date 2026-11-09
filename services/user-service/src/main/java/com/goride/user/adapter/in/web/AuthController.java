package com.goride.user.adapter.in.web;

import com.goride.common.dto.ApiResponse;
import com.goride.user.application.dto.request.LoginRequest;
import com.goride.user.application.dto.request.RefreshTokenRequest;
import com.goride.user.application.dto.request.RegisterRequest;
import com.goride.user.application.dto.response.AuthResponse;
import com.goride.user.application.service.AuthService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/**
 * REST Controller exposing Authentication endpoints (Register, Login, Refresh Token).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = Objects.requireNonNull(authService, "authService must not be null");
    }

    /**
     * Register a new user account.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationIdHeader) {
        log.info("Received REST request to register user with phone: {}", request.getPhone());
        UUID correlationId = null;
        if (StringUtils.hasText(correlationIdHeader)) {
            try {
                correlationId = UUID.fromString(correlationIdHeader.trim());
            } catch (IllegalArgumentException ignored) {
                // Ignore invalid UUID header format
            }
        }
        AuthResponse response = authService.register(request, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /**
     * Authenticate user credentials and issue tokens.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("Received REST request to login user with identifier: {}", request.getIdentifier());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Refresh expired access token using valid refresh token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("Received REST request to refresh access token");
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

