package com.goride.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goride.common.dto.ApiResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Global filter for JWT authentication at the API Gateway boundary.
 * Validates bearer token, blocks unauthenticated requests to protected endpoints,
 * and enriches forwarded requests with verified identity headers (X-User-Id, X-User-Roles).
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLES_HEADER = "X-User-Roles";
    public static final String USER_PHONE_HEADER = "X-User-Phone";

    private final SecretKey secretKey;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final List<String> openEndpoints = List.of(
            "/api/v1/auth/**",
            "/actuator/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/favicon.ico"
    );

    public JwtAuthenticationFilter(
            @Value("${jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}") String secret,
            ObjectMapper objectMapper) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                keyBytes = md.digest(keyBytes);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 algorithm not available", e);
            }
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 1. Bypass authentication for public whitelist endpoints
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        // 2. Extract Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            return onError(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        // 3. Validate token and extract claims
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();
            if (!StringUtils.hasText(userId)) {
                log.warn("Token contains empty subject claim for path: {}", path);
                return onError(exchange, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Token subject is invalid");
            }

            String rolesStr = "";
            Object rolesObj = claims.get("roles");
            if (rolesObj instanceof List<?> list) {
                rolesStr = String.join(",", list.stream().map(Object::toString).toList());
            }

            String phone = claims.get("phone", String.class);

            // 4. Mutate request: forward verified identity headers to internal services
            ServerHttpRequest.Builder requestBuilder = request.mutate()
                    .header(USER_ID_HEADER, userId)
                    .header(USER_ROLES_HEADER, rolesStr);

            if (StringUtils.hasText(phone)) {
                requestBuilder.header(USER_PHONE_HEADER, phone);
            }

            ServerHttpRequest mutatedRequest = requestBuilder.build();
            log.debug("Authenticated request path: {} for userId: {} with roles: [{}]", path, userId, rolesStr);

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired for path {}: {}", path, e.getMessage());
            return onError(exchange, HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "JWT access token has expired");
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token for path {}: {}", path, e.getMessage());
            return onError(exchange, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "JWT access token is invalid");
        }
    }

    private boolean isWhitelisted(String path) {
        return openEndpoints.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Void> apiResponse = ApiResponse.error(code, message);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(apiResponse);
        } catch (JsonProcessingException e) {
            bytes = ("{\"success\":false,\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}}")
                    .getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // Runs immediately after CorrelationIdFilter (-2147483648)
        return -100;
    }
}

