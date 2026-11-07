package com.goride.user.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.exception.EntityNotFoundException;
import com.goride.user.application.dto.response.UserDto;
import com.goride.user.application.service.UserService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {UserController.class, UserGlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("GET /api/v1/users/me with forwarded header X-User-Id returns user profile")
    void getMyProfile_WithHeader_Returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        UserDto userDto = new UserDto(userId, "+84912345678", "user@goride.com", "Nguyen Van A", UserStatus.ACTIVE, Set.of("PASSENGER"), Instant.now(), Instant.now());

        when(userService.getProfile(userId)).thenReturn(userDto);

        mockMvc.perform(get("/api/v1/users/me")
                        .header("X-User-Id", userId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.phone").value("+84912345678"))
                .andExpect(jsonPath("$.data.fullName").value("Nguyen Van A"));
    }

    @Test
    @DisplayName("GET /api/v1/users/me without identity returns 401 Unauthorized")
    void getMyProfile_NoIdentity_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/v1/users/me when user not found returns 404 Not Found")
    void getMyProfile_UserNotFound_Returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.getProfile(userId)).thenThrow(new EntityNotFoundException("User not found"));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("X-User-Id", userId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }
}
