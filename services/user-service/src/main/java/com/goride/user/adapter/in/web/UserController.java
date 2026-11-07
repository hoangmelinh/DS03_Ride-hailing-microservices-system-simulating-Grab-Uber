package com.goride.user.adapter.in.web;

import com.goride.common.dto.ApiResponse;
import com.goride.common.exception.UnauthorizedException;
import com.goride.user.application.dto.response.UserDto;
import com.goride.user.application.service.UserService;
import com.goride.user.infrastructure.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/**
 * REST Controller exposing User Profile endpoints.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = Objects.requireNonNull(userService, "userService must not be null");
    }

    /**
     * Retrieve authenticated user's own profile.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> getMyProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "X-User-Id", required = false) String forwardedUserId) {
        UUID userId = null;
        if (principal != null) {
            userId = principal.getId();
        } else if (StringUtils.hasText(forwardedUserId)) {
            try {
                userId = UUID.fromString(forwardedUserId.trim());
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (userId == null) {
            throw new UnauthorizedException("User identity could not be resolved from token or header");
        }

        log.info("Received REST request to retrieve profile for userId: {}", userId);
        UserDto profile = userService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.success(profile));
    }
}

