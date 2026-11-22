package com.goride.user.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Service component for generating, signing, and validating JWT tokens.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey secretKey;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}") String secret,
            @Value("${jwt.access-token-expiration-ms:3600000}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-token-expiration-ms:604800000}") long refreshTokenExpirationMs) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // Pad or hash to ensure at least 256 bits for HMAC-SHA256
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                keyBytes = md.digest(keyBytes);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 algorithm not available", e);
            }
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    /**
     * Generate signed JWT Access Token.
     */
    public String generateAccessToken(UUID userId, String phone, String email, Collection<String> roles) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(accessTokenExpirationMs);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("phone", phone)
                .claim("email", email)
                .claim("roles", roles != null ? roles : List.of())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Generate random refresh token string.
     */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Calculate SHA-256 hash of a token for secure persistent storage.
     */
    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Validate JWT Access Token.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired: {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Extract Claims from token.
     */
    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extract user UUID from subject claim.
     */
    public UUID getUserId(String token) {
        String sub = getClaims(token).getSubject();
        return UUID.fromString(sub);
    }

    /**
     * Extract roles from claims.
     */
    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        Object rolesObj = getClaims(token).get("roles");
        if (rolesObj instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }

    public long getRefreshTokenExpirationMs() {
        return refreshTokenExpirationMs;
    }
}

