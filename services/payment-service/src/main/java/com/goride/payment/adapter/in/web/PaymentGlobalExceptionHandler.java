package com.goride.payment.adapter.in.web;

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
 * Controller advice providing centralized exception handling for Payment Service.
 * Extends BaseGlobalExceptionHandler to inherit standard business exception & validation mapping.
 */
@RestControllerAdvice
public class PaymentGlobalExceptionHandler extends BaseGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentGlobalExceptionHandler.class);

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        if (isUniqueTripIdViolation(ex)) {
            log.warn("Concurrent duplicate payment conflict detected on uq_payments_trip_id");
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("PAYMENT_ALREADY_EXISTS",
                            "A payment transaction for this trip is already completed or in progress. Please query payment status."));
        }

        log.warn("Database data integrity violation in Payment Service: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("DATA_INTEGRITY_VIOLATION", "Database constraint violation occurred"));
    }

    /**
     * Traverses the exception cause chain to accurately identify SQLSTATE 23505 and uq_payments_trip_id constraint,
     * without relying solely on top-level exception message parsing.
     */
    private boolean isUniqueTripIdViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
                String constraintName = cve.getConstraintName();
                if (constraintName != null && constraintName.toLowerCase().contains("uq_payments_trip_id")) {
                    return true;
                }
            }
            if (cause instanceof java.sql.SQLException sqlEx) {
                if ("23505".equals(sqlEx.getSQLState())) {
                    String message = sqlEx.getMessage();
                    if (message != null && message.toLowerCase().contains("uq_payments_trip_id")) {
                        return true;
                    }
                }
            }
            cause = cause.getCause();
        }

        // Fallback inspection if the top-level or nested message contains constraint name
        String exMsg = ex.getMessage();
        return exMsg != null && exMsg.toLowerCase().contains("uq_payments_trip_id");
    }
}
