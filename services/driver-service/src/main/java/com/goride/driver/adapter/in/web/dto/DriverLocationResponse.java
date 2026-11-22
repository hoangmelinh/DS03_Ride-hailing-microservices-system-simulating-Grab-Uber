package com.goride.driver.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DriverLocationResponse(
        UUID driverId,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant updatedAt
) {
}
