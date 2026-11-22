package com.goride.user.adapter.in.web;

import com.goride.common.dto.ApiResponse;
import com.goride.common.handler.BaseGlobalExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for User Service.
 * Extends BaseGlobalExceptionHandler from goride-common.
 */
@RestControllerAdvice
public class UserGlobalExceptionHandler extends BaseGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(UserGlobalExceptionHandler.class);

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("UNAUTHORIZED", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("FORBIDDEN", "Access denied: insufficient permissions"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Database constraint violation in User Service: {}", ex.getMessage());
        String message = "Database constraint violation occurred";
        if (ex.getMessage() != null) {
            String lower = ex.getMessage().toLowerCase();
            if (lower.contains("uq_users_phone")) {
                message = "Phone number is already registered";
            } else if (lower.contains("uq_users_email")) {
                message = "Email address is already registered";
            }
        }
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("DATA_INTEGRITY_VIOLATION", message));
    }
}

