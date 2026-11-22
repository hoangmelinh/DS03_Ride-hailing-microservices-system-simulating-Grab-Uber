package com.goride.driver.application.port.in;

import com.goride.driver.domain.model.DriverStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AvailableDriverResult(
        UUID driverId,
        DriverStatus status,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant locationUpdatedAt
) {
}
