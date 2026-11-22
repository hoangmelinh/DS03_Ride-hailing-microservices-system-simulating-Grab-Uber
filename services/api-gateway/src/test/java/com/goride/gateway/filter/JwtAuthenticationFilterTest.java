package com.goride.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filter;
    private final String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(secret, objectMapper);
        secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private String createTestToken(UUID userId, List<String> roles, long expirationSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("roles", roles)
                .claim("phone", "+84912345678")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(secretKey)
                .compact();
    }

    @Test
    @DisplayName("Public auth endpoints bypass authentication check")
    void filter_PublicEndpoint_PassesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/auth/register").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainInvoked.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Protected endpoint without Authorization header returns 401 Unauthorized")
    void filter_MissingAuthHeader_Returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/trips").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertFalse(chainInvoked.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Protected endpoint with expired token returns 401 Unauthorized")
    void filter_ExpiredToken_Returns401() {
        String expiredToken = createTestToken(UUID.randomUUID(), List.of("PASSENGER"), -10);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/trips")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertFalse(chainInvoked.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Protected endpoint with valid token injects identity headers and passes downstream")
    void filter_ValidToken_InjectsHeadersAndPasses() {
        UUID userId = UUID.randomUUID();
        String validToken = createTestToken(userId, List.of("PASSENGER", "DRIVER"), 3600);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/trips")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<String> forwardedUserId = new AtomicReference<>();
        AtomicReference<String> forwardedRoles = new AtomicReference<>();
        GatewayFilterChain chain = mutatedExchange -> {
            forwardedUserId.set(mutatedExchange.getRequest().getHeaders().getFirst(JwtAuthenticationFilter.USER_ID_HEADER));
            forwardedRoles.set(mutatedExchange.getRequest().getHeaders().getFirst(JwtAuthenticationFilter.USER_ROLES_HEADER));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertEquals(userId.toString(), forwardedUserId.get());
        assertEquals("PASSENGER,DRIVER", forwardedRoles.get());
    }
}

