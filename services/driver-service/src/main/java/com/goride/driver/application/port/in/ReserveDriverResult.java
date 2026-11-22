package com.goride.driver.application.port.in;

import com.goride.driver.domain.model.ReservationStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReserveDriverResult(
        UUID reservationId,
        UUID driverId,
        UUID tripId,
        ReservationStatus status,
        Instant expiresAt
) {
    public ReserveDriverResult {
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        Objects.requireNonNull(driverId, "driverId must not be null");
        Objects.requireNonNull(tripId, "tripId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}

