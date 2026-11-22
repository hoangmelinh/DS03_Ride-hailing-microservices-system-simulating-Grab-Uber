package com.goride.driver.adapter.in.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReserveDriverRequest(
        @NotNull(message = "tripId must not be null")
        UUID tripId,

        @Min(value = 5, message = "ttlSeconds must be at least 5")
        @Max(value = 300, message = "ttlSeconds must be at most 300")
        Integer ttlSeconds
) {
}

