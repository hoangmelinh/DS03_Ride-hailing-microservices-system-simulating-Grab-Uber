package com.goride.matching.application.port.in;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record ProcessTripCreatedCommand(
        UUID eventId,
        UUID tripId,
        UUID passengerId,
        BigDecimal pickupLatitude,
        BigDecimal pickupLongitude,
        UUID correlationId
) {
    public ProcessTripCreatedCommand {
        Objects.requireNonNull(tripId, "tripId must not be null");
        Objects.requireNonNull(pickupLatitude, "pickupLatitude must not be null");
        Objects.requireNonNull(pickupLongitude, "pickupLongitude must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
    }
}
