package com.goride.user.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.exception.DuplicateResourceException;
import com.goride.common.exception.UnauthorizedException;
import com.goride.user.application.dto.request.LoginRequest;
import com.goride.user.application.dto.request.RefreshTokenRequest;
import com.goride.user.application.dto.request.RegisterRequest;
import com.goride.user.application.dto.response.AuthResponse;
import com.goride.user.application.dto.response.UserDto;
import com.goride.user.application.service.AuthService;
import com.goride.user.domain.model.RoleCode;
import com.goride.user.domain.model.UserStatus;
import com.goride.user.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, UserGlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("POST /api/v1/auth/register with valid payload returns 201 Created and tokens")
    void register_ValidPayload_Returns201() throws Exception {
        RegisterRequest request = new RegisterRequest("+84912345678", "passenger@goride.com", "Password123", "Nguyen Van A", RoleCode.PASSENGER);
        UserDto userDto = new UserDto(UUID.randomUUID(), request.getPhone(), request.getEmail(), request.getFullName(), UserStatus.ACTIVE, Set.of("PASSENGER"), Instant.now(), Instant.now());
        AuthResponse authResponse = new AuthResponse("access.jwt.token", "refresh-token-xyz", 3600L, userDto);

        when(authService.register(any(RegisterRequest.class), any())).thenReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", UUID.randomUUID().toString())
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access.jwt.token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token-xyz"))
                .andExpect(jsonPath("$.data.user.phone").value("+84912345678"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register with duplicate phone returns 409 Conflict")
    void register_DuplicatePhone_Returns409() throws Exception {
        RegisterRequest request = new RegisterRequest("+84912345678", "passenger@goride.com", "Password123", "Nguyen Van A", RoleCode.PASSENGER);
        when(authService.register(any(RegisterRequest.class), any()))
                .thenThrow(new DuplicateResourceException("Phone number is already registered: +84912345678"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login with valid credentials returns 200 OK")
    void login_ValidCredentials_Returns200() throws Exception {
        LoginRequest request = new LoginRequest("+84912345678", "Password123");
        UserDto userDto = new UserDto(UUID.randomUUID(), request.getIdentifier(), null, "Nguyen Van A", UserStatus.ACTIVE, Set.of("PASSENGER"), Instant.now(), Instant.now());
        AuthResponse authResponse = new AuthResponse("access.jwt.token", "refresh-token-xyz", 3600L, userDto);

        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access.jwt.token"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login with invalid credentials returns 401 Unauthorized")
    void login_InvalidCredentials_Returns401() throws Exception {
        LoginRequest request = new LoginRequest("+84912345678", "WrongPassword");
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new UnauthorizedException("Invalid phone/email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh with valid token returns 200 OK and rotated tokens")
    void refresh_ValidToken_Returns200() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
        UserDto userDto = new UserDto(UUID.randomUUID(), "+84912345678", null, "Nguyen Van A", UserStatus.ACTIVE, Set.of("PASSENGER"), Instant.now(), Instant.now());
        AuthResponse authResponse = new AuthResponse("new.access.jwt.token", "new-refresh-token", 3600L, userDto);

        when(authService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("new.access.jwt.token"));
    }
}

