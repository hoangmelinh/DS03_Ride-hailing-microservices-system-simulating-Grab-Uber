package com.goride.driver.application.port.in;

import com.goride.driver.domain.model.DriverStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReleaseDriverResult(
        UUID driverId,
        UUID tripId,
        DriverStatus status,
        Instant releasedAt
) {
    public ReleaseDriverResult {
        Objects.requireNonNull(driverId, "driverId must not be null");
        Objects.requireNonNull(tripId, "tripId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(releasedAt, "releasedAt must not be null");
    }
}
