package com.goride.user.application.service;

import com.goride.user.application.dto.request.LoginRequest;
import com.goride.user.application.dto.request.RefreshTokenRequest;
import com.goride.user.application.dto.request.RegisterRequest;
import com.goride.user.application.dto.response.AuthResponse;

import java.util.UUID;

/**
 * Service interface handling user registration, login, and token operations.
 */
public interface AuthService {

    AuthResponse register(RegisterRequest request, UUID correlationId);

    AuthResponse login(LoginRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);
}

