package com.goride.notification.adapter.in.web;

import com.goride.common.dto.ApiResponse;
import com.goride.common.handler.BaseGlobalExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Controller advice providing centralized exception handling for Notification Service.
 * Extends BaseGlobalExceptionHandler to inherit standard business exception & validation mapping.
 */
@RestControllerAdvice
public class NotificationGlobalExceptionHandler extends BaseGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationGlobalExceptionHandler.class);

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        log.warn("Database constraint violation in Notification Service: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("CONFLICT", "Resource state conflict or database unique constraint violation"));
    }
}

