package com.goride.driver.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AvailableDriverResponse(
        UUID driverId,
        String status,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant locationUpdatedAt
) {
}
