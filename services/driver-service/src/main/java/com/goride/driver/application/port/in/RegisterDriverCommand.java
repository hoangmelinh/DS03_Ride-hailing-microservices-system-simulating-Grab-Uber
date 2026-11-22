package com.goride.driver.application.port.in;

import java.util.Objects;
import java.util.UUID;

public record RegisterDriverCommand(
        UUID userId,
        String licenseNumber,
        String vehiclePlate,
        String vehicleModel
) {
    public RegisterDriverCommand {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(licenseNumber, "licenseNumber must not be null");
        Objects.requireNonNull(vehiclePlate, "vehiclePlate must not be null");
        Objects.requireNonNull(vehicleModel, "vehicleModel must not be null");
    }
}
