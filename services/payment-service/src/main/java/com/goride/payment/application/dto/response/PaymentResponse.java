package com.goride.payment.application.dto.response;

import com.goride.payment.domain.model.PaymentStatus;
import com.goride.payment.infrastructure.persistence.entity.PaymentEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Standard response representation for payment operations.
 */
public record PaymentResponse(
    UUID id,
    UUID tripId,
    UUID passengerId,
    BigDecimal amount,
    String currency,
    PaymentStatus status,
    String provider,
    String providerReference,
    Instant createdAt,
    Instant completedAt
) {
    public static PaymentResponse from(PaymentEntity entity) {
        if (entity == null) {
            return null;
        }
        return new PaymentResponse(
            entity.getId(),
            entity.getTripId(),
            entity.getPassengerId(),
            entity.getAmount(),
            entity.getCurrency(),
            entity.getStatus(),
            entity.getProvider(),
            entity.getProviderReference(),
            entity.getCreatedAt(),
            entity.getCompletedAt()
        );
    }
}
