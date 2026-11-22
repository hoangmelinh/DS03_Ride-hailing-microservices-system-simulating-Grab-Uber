package com.goride.user.application.dto.request;

import com.goride.user.domain.model.RoleCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for user registration.
 */
public class RegisterRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[0-9+]{9,15}$", message = "Phone number must be valid (9-15 digits, optional + prefix)")
    private String phone;

    @Email(message = "Email must be valid format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 50, message = "Password must be between 6 and 50 characters")
    private String password;

    @NotBlank(message = "Full name is required")
    @Size(max = 150, message = "Full name must not exceed 150 characters")
    private String fullName;

    private RoleCode role = RoleCode.PASSENGER;

    public RegisterRequest() {
    }

    public RegisterRequest(String phone, String email, String password, String fullName, RoleCode role) {
        this.phone = phone;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.role = role != null ? role : RoleCode.PASSENGER;
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public RoleCode getRole() {
        return role;
    }

    public void setRole(RoleCode role) {
        this.role = role != null ? role : RoleCode.PASSENGER;
    }
}

