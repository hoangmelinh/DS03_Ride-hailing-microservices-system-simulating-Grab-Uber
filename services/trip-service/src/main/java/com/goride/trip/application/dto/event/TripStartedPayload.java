package com.goride.trip.application.dto.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload carried inside the event envelope for `trip.started`.
 */
public class TripStartedPayload {

    private UUID tripId;
    private UUID passengerId;
    private UUID driverId;
    private String status;
    private Instant startedAt;

    public TripStartedPayload() {
    }

    public TripStartedPayload(UUID tripId, UUID passengerId, UUID driverId, String status, Instant startedAt) {
        this.tripId = tripId;
        this.passengerId = passengerId;
        this.driverId = driverId;
        this.status = status;
        this.startedAt = startedAt;
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

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }
}
