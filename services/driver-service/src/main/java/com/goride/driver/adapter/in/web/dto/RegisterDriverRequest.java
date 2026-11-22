package com.goride.driver.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RegisterDriverRequest(
        @NotNull(message = "User ID is required")
        UUID userId,

        @NotBlank(message = "License number is required")
        String licenseNumber,

        @NotBlank(message = "Vehicle plate is required")
        String vehiclePlate,

        @NotBlank(message = "Vehicle model is required")
        String vehicleModel
) {
}
