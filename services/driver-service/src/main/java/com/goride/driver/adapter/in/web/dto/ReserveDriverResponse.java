package com.goride.driver.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

public record ReserveDriverResponse(
        UUID reservationId,
        UUID driverId,
        UUID tripId,
        String status,
        Instant expiresAt
) {
}

