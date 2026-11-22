package com.goride.user.application.dto.response;

import com.goride.user.domain.model.UserStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Representation of a user profile.
 */
public class UserDto {

    private UUID id;
    private String phone;
    private String email;
    private String fullName;
    private UserStatus status;
    private Set<String> roles;
    private Instant createdAt;
    private Instant updatedAt;

    public UserDto() {
    }

    public UserDto(UUID id, String phone, String email, String fullName,
                   UserStatus status, Set<String> roles, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.phone = phone;
        this.email = email;
        this.fullName = fullName;
        this.status = status;
        this.roles = roles;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

