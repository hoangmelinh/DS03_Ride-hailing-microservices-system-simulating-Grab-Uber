package com.goride.user.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Filter that authenticates requests using JWT bearer tokens.
 * Also supports edge identity forwarded headers from API Gateway (X-User-Id, X-User-Roles).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            UUID userId = jwtTokenProvider.getUserId(token);
            List<String> roles = jwtTokenProvider.getRoles(token);
            var authorities = roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                    .toList();

            UserPrincipal principal = new UserPrincipal(userId, null, null, null, authorities);
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        } else {
            // Check for forwarded identity headers from API Gateway (service mesh trust boundary)
            String forwardedUserId = request.getHeader("X-User-Id");
            if (StringUtils.hasText(forwardedUserId)) {
                try {
                    UUID userId = UUID.fromString(forwardedUserId.trim());
                    String forwardedRoles = request.getHeader("X-User-Roles");
                    List<SimpleGrantedAuthority> authorities = StringUtils.hasText(forwardedRoles)
                            ? Arrays.stream(forwardedRoles.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .map(role -> new SimpleGrantedAuthority(role.startsWith("ROLE_") ? role : "ROLE_" + role.toUpperCase()))
                            .toList()
                            : List.of();

                    UserPrincipal principal = new UserPrincipal(userId, null, null, null, authorities);
                    var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (IllegalArgumentException ignored) {
                    // Invalid UUID header format, skip
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}

