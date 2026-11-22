package com.goride.trip.adapter.in.web;

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
 * Global exception handler for Trip Service.
 * Extends BaseGlobalExceptionHandler from goride-common.
 */
@RestControllerAdvice
public class TripGlobalExceptionHandler extends BaseGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TripGlobalExceptionHandler.class);

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Database constraint violation in Trip Service: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("DATA_INTEGRITY_VIOLATION", "Database constraint violation occurred"));
    }
}

