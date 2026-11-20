package com.goride.trip.application.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request payload for assigning a driver to a trip by Matching Service Saga.
 */
public class AssignDriverRequest {

    @NotNull(message = "Driver ID is required")
    private UUID driverId;

    public AssignDriverRequest() {
    }

    public AssignDriverRequest(UUID driverId) {
        this.driverId = driverId;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public void setDriverId(UUID driverId) {
        this.driverId = driverId;
    }
}
