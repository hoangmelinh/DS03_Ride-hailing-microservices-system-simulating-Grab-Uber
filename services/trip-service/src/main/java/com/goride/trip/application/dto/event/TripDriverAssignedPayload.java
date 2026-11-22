package com.goride.trip.application.dto.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload carried inside the event envelope for `trip.driver-assigned`.
 */
public class TripDriverAssignedPayload {

    private UUID tripId;
    private UUID passengerId;
    private UUID driverId;
    private String status;
    private Instant assignedAt;

    public TripDriverAssignedPayload() {
    }

    public TripDriverAssignedPayload(UUID tripId, UUID passengerId, UUID driverId, String status, Instant assignedAt) {
        this.tripId = tripId;
        this.passengerId = passengerId;
        this.driverId = driverId;
        this.status = status;
        this.assignedAt = assignedAt;
    }

    public UUID getTripId() {
        return tripId;
    }

    public void setTripId(UUID tripId) {
        this.tripId = tripId;
    }

    public UUID getPassengerId() {
        return passengerId;
    }

    public void setPassengerId(UUID passengerId) {
        this.passengerId = passengerId;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public void setDriverId(UUID driverId) {
        this.driverId = driverId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
    }
}
