package com.goride.user.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private final String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private final long accessExpiration = 3600000; // 1 hour
    private final long refreshExpiration = 604800000; // 7 days

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(secret, accessExpiration, refreshExpiration);
    }

    @Test
    @DisplayName("Generate access token and successfully validate and extract claims")
    void generateAccessToken_ValidClaims_SuccessfullyParsed() {
        UUID userId = UUID.randomUUID();
        String phone = "+84912345678";
        String email = "test@goride.com";
        List<String> roles = List.of("PASSENGER");

        String token = jwtTokenProvider.generateAccessToken(userId, phone, email, roles);
        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));

        assertEquals(userId, jwtTokenProvider.getUserId(token));
        assertEquals(roles, jwtTokenProvider.getRoles(token));
        assertEquals(phone, jwtTokenProvider.getClaims(token).get("phone"));
        assertEquals(email, jwtTokenProvider.getClaims(token).get("email"));
    }

    @Test
    @DisplayName("Invalid token validation returns false")
    void validateToken_InvalidToken_ReturnsFalse() {
        assertFalse(jwtTokenProvider.validateToken("invalid.token.structure"));
        assertFalse(jwtTokenProvider.validateToken(""));
        assertFalse(jwtTokenProvider.validateToken(null));
    }

    @Test
    @DisplayName("Expired token validation returns false")
    void validateToken_ExpiredToken_ReturnsFalse() {
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(secret, -1000, refreshExpiration);
        UUID userId = UUID.randomUUID();

        String expiredToken = shortLivedProvider.generateAccessToken(userId, "+84900000000", null, List.of("PASSENGER"));
        assertFalse(jwtTokenProvider.validateToken(expiredToken));
    }

    @Test
    @DisplayName("Generate refresh token produces unique strings and correct SHA-256 hash")
    void generateRefreshToken_ProducesUniqueTokensAndHashes() {
        String token1 = jwtTokenProvider.generateRefreshToken();
        String token2 = jwtTokenProvider.generateRefreshToken();

        assertNotNull(token1);
        assertNotNull(token2);
        assertNotEquals(token1, token2);

        String hash1 = jwtTokenProvider.hashToken(token1);
        String hash2 = jwtTokenProvider.hashToken(token1);
        String hash3 = jwtTokenProvider.hashToken(token2);

        assertEquals(hash1, hash2);
        assertNotEquals(hash1, hash3);
        assertEquals(64, hash1.length()); // SHA-256 hex string length
    }
}

