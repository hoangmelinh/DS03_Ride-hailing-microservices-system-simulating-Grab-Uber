package com.goride.driver.application.port.in;

import java.util.Objects;
import java.util.UUID;

public record ReserveDriverCommand(
        UUID driverId,
        UUID tripId,
        String idempotencyKey,
        UUID correlationId,
        Integer ttlSeconds
) {
    public ReserveDriverCommand {
        Objects.requireNonNull(driverId, "driverId must not be null");
        Objects.requireNonNull(tripId, "tripId must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        Objects.requireNonNull(correlationId, "correlationId must not be null");
    }

    public int getEffectiveTtlSeconds() {
        return (ttlSeconds != null && ttlSeconds > 0) ? ttlSeconds : 30;
    }
}

