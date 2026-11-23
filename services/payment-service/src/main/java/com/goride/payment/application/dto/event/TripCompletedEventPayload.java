package com.goride.payment.application.dto.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event payload consumed from 'trip.completed'.
 * Produced by Trip Service upon trip lifecycle completion.
 */
public record TripCompletedEventPayload(
        UUID tripId,
        UUID passengerId,
        UUID driverId,
        BigDecimal finalFare,
        String currency,
        String status,
        Instant completedAt
) {}
