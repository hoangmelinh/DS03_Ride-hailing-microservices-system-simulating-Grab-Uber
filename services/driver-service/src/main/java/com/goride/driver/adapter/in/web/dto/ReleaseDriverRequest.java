package com.goride.driver.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReleaseDriverRequest(
        @NotNull(message = "tripId must not be null")
        UUID tripId,

        String reason
) {
}
