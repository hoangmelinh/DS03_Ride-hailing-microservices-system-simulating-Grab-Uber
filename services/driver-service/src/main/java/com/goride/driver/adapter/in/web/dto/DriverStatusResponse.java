package com.goride.driver.adapter.in.web.dto;

import com.goride.driver.domain.model.DriverStatus;

import java.time.Instant;
import java.util.UUID;

public record DriverStatusResponse(
        UUID id,
        UUID userId,
        String licenseNumber,
        DriverStatus status,
        UUID currentTripId,
        Instant updatedAt
) {
}
