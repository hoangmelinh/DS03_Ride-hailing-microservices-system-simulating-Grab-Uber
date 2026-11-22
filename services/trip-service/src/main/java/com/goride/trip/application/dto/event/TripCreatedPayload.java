package com.goride.trip.application.dto.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload carried inside the event envelope for `trip.created`.
 */
public class TripCreatedPayload {

    private UUID tripId;
    private UUID passengerId;
    private BigDecimal pickupLatitude;
    private BigDecimal pickupLongitude;
    private String pickupAddress;
    private BigDecimal destinationLatitude;
    private BigDecimal destinationLongitude;
    private String destinationAddress;
    private BigDecimal estimatedFare;
    private String currency;
    private String status;
    private Instant requestedAt;

    public TripCreatedPayload() {
    }

    public TripCreatedPayload(UUID tripId, UUID passengerId, BigDecimal pickupLatitude,
                              BigDecimal pickupLongitude, String pickupAddress,
                              BigDecimal destinationLatitude, BigDecimal destinationLongitude,
                              String destinationAddress, BigDecimal estimatedFare,
                              String currency, String status, Instant requestedAt) {
        this.tripId = tripId;
        this.passengerId = passengerId;
        this.pickupLatitude = pickupLatitude;
        this.pickupLongitude = pickupLongitude;
        this.pickupAddress = pickupAddress;
        this.destinationLatitude = destinationLatitude;
        this.destinationLongitude = destinationLongitude;
        this.destinationAddress = destinationAddress;
        this.estimatedFare = estimatedFare;
        this.currency = currency;
        this.status = status;
        this.requestedAt = requestedAt;
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

    public BigDecimal getPickupLatitude() {
        return pickupLatitude;
    }

    public void setPickupLatitude(BigDecimal pickupLatitude) {
        this.pickupLatitude = pickupLatitude;
    }

    public BigDecimal getPickupLongitude() {
        return pickupLongitude;
    }

    public void setPickupLongitude(BigDecimal pickupLongitude) {
        this.pickupLongitude = pickupLongitude;
    }

    public String getPickupAddress() {
        return pickupAddress;
    }

    public void setPickupAddress(String pickupAddress) {
        this.pickupAddress = pickupAddress;
    }

    public BigDecimal getDestinationLatitude() {
        return destinationLatitude;
    }

    public void setDestinationLatitude(BigDecimal destinationLatitude) {
        this.destinationLatitude = destinationLatitude;
    }

    public BigDecimal getDestinationLongitude() {
        return destinationLongitude;
    }

    public void setDestinationLongitude(BigDecimal destinationLongitude) {
        this.destinationLongitude = destinationLongitude;
    }

    public String getDestinationAddress() {
        return destinationAddress;
    }

    public void setDestinationAddress(String destinationAddress) {
        this.destinationAddress = destinationAddress;
    }

    public BigDecimal getEstimatedFare() {
        return estimatedFare;
    }

    public void setEstimatedFare(BigDecimal estimatedFare) {
        this.estimatedFare = estimatedFare;
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

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }
}

