package com.goride.user.application.service;

import com.goride.user.application.dto.response.UserDto;

import java.util.UUID;

/**
 * Service interface for querying and managing user profiles.
 */
public interface UserService {

    UserDto getProfile(UUID userId);
}

