package com.goride.matching.adapter.in.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TripCreatedEventPayload(
        UUID tripId,
        UUID passengerId,
        BigDecimal pickupLatitude,
        BigDecimal pickupLongitude,
        String pickupAddress,
        BigDecimal destinationLatitude,
        BigDecimal destinationLongitude,
        String destinationAddress,
        BigDecimal estimatedFare,
        String currency,
        String status,
        Instant requestedAt
) {
}
