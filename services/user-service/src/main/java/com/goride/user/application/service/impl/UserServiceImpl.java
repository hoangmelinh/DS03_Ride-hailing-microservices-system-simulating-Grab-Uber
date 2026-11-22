package com.goride.user.application.service.impl;

import com.goride.common.exception.EntityNotFoundException;
import com.goride.user.application.dto.response.UserDto;
import com.goride.user.application.service.UserService;
import com.goride.user.infrastructure.persistence.entity.RoleEntity;
import com.goride.user.infrastructure.persistence.entity.UserEntity;
import com.goride.user.infrastructure.persistence.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of UserService for retrieving profile information.
 */
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto getProfile(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

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

