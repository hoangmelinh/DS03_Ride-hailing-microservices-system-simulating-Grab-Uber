package com.goride.user.infrastructure.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/**
 * Authenticated user principal representing identity in SecurityContext.
 */
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String phone;
    private final String email;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(UUID id, String phone, String email, String password,
                         Collection<? extends GrantedAuthority> authorities) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.phone = phone;
        this.email = email;
        this.password = password;
        this.authorities = authorities;
    }

    public UUID getId() {
        return id;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return phone != null ? phone : (email != null ? email : id.toString());
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}

