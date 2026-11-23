package com.goride.payment.application.dto.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class TripCompletedEventPayload {

    private UUID tripId;
    private UUID passengerId;
    private UUID driverId;
    private BigDecimal finalFare;
    private String currency;
    private String status;
    private Instant completedAt;

    public TripCompletedEventPayload() {
    }

    public TripCompletedEventPayload(UUID tripId, UUID passengerId, UUID driverId, BigDecimal finalFare,
                                     String currency, String status, Instant completedAt) {
        this.tripId = tripId;
        this.passengerId = passengerId;
        this.driverId = driverId;
        this.finalFare = finalFare;
        this.currency = currency;
        this.status = status;
        this.completedAt = completedAt;
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

    public BigDecimal getFinalFare() {
        return finalFare;
    }

    public void setFinalFare(BigDecimal finalFare) {
        this.finalFare = finalFare;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
