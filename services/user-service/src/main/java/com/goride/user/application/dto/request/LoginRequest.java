package com.goride.user.application.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload for authentication/login.
 */
public class LoginRequest {

    @NotBlank(message = "Identifier (phone or email) is required")
    private String identifier;

    @NotBlank(message = "Password is required")
    private String password;

    public LoginRequest() {
    }

    public LoginRequest(String identifier, String password) {
        this.identifier = identifier;
        this.password = password;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    // Convenience setter for clients sending "phone" instead of "identifier"
    public void setPhone(String phone) {
        if (this.identifier == null || this.identifier.isBlank()) {
            this.identifier = phone;
        }
    }

    // Convenience setter for clients sending "email" instead of "identifier"
    public void setEmail(String email) {
        if (this.identifier == null || this.identifier.isBlank()) {
            this.identifier = email;
        }
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

