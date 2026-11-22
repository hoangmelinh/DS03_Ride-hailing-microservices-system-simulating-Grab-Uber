package com.goride.trip.application.dto.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload carried inside the event envelope for `trip.cancelled`.
 */
public class TripCancelledPayload {

    private UUID tripId;
    private UUID passengerId;
    private UUID driverId;
    private String cancellationReason;
    private String status;
    private Instant cancelledAt;

    public TripCancelledPayload() {
    }

    public TripCancelledPayload(UUID tripId, UUID passengerId, UUID driverId,
                                String cancellationReason, String status, Instant cancelledAt) {
        this.tripId = tripId;
        this.passengerId = passengerId;
        this.driverId = driverId;
        this.cancellationReason = cancellationReason;
        this.status = status;
        this.cancelledAt = cancelledAt;
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

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }
}
