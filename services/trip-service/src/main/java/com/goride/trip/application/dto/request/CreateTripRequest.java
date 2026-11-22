package com.goride.trip.application.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload for requesting a new ride.
 */
public class CreateTripRequest {

    private UUID passengerId;

    @NotNull(message = "Pickup latitude is required")
    @DecimalMin(value = "-90.0", message = "Pickup latitude must be >= -90.0")
    @DecimalMax(value = "90.0", message = "Pickup latitude must be <= 90.0")
    private BigDecimal pickupLatitude;

    @NotNull(message = "Pickup longitude is required")
    @DecimalMin(value = "-180.0", message = "Pickup longitude must be >= -180.0")
    @DecimalMax(value = "180.0", message = "Pickup longitude must be <= 180.0")
    private BigDecimal pickupLongitude;

    @Size(max = 255, message = "Pickup address must not exceed 255 characters")
    private String pickupAddress;

    @NotNull(message = "Destination latitude is required")
    @DecimalMin(value = "-90.0", message = "Destination latitude must be >= -90.0")
    @DecimalMax(value = "90.0", message = "Destination latitude must be <= 90.0")
    private BigDecimal destinationLatitude;

    @NotNull(message = "Destination longitude is required")
    @DecimalMin(value = "-180.0", message = "Destination longitude must be >= -180.0")
    @DecimalMax(value = "180.0", message = "Destination longitude must be <= 180.0")
    private BigDecimal destinationLongitude;

    @Size(max = 255, message = "Destination address must not exceed 255 characters")
    private String destinationAddress;

    private BigDecimal estimatedFare;
    private String currency = "VND";

    public CreateTripRequest() {
    }

    public CreateTripRequest(UUID passengerId, BigDecimal pickupLatitude, BigDecimal pickupLongitude,
                             String pickupAddress, BigDecimal destinationLatitude, BigDecimal destinationLongitude,
                             String destinationAddress) {
        this.passengerId = passengerId;
        this.pickupLatitude = pickupLatitude;
        this.pickupLongitude = pickupLongitude;
        this.pickupAddress = pickupAddress;
        this.destinationLatitude = destinationLatitude;
        this.destinationLongitude = destinationLongitude;
        this.destinationAddress = destinationAddress;
        this.currency = "VND";
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
        return currency != null ? currency : "VND";
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}

