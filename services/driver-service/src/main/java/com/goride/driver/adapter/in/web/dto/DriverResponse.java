package com.goride.driver.adapter.in.web.dto;

import com.goride.driver.domain.model.DriverStatus;
import com.goride.driver.domain.model.VehicleStatus;

import java.time.Instant;
import java.util.UUID;

public record DriverResponse(
        UUID id,
        UUID userId,
        String licenseNumber,
        DriverStatus status,
        VehicleResponse vehicle,
        Instant createdAt
) {
    public record VehicleResponse(
            UUID id,
            String plateNumber,
            String model,
            int capacity,
            VehicleStatus status
    ) {
    }
}
