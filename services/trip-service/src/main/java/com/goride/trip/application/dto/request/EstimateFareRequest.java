package com.goride.trip.application.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request payload for estimating ride distance and fare.
 */
public class EstimateFareRequest {

    @NotNull(message = "Pickup latitude is required")
    @DecimalMin(value = "-90.0", message = "Pickup latitude must be >= -90.0")
    @DecimalMax(value = "90.0", message = "Pickup latitude must be <= 90.0")
    private BigDecimal pickupLatitude;

    @NotNull(message = "Pickup longitude is required")
    @DecimalMin(value = "-180.0", message = "Pickup longitude must be >= -180.0")
    @DecimalMax(value = "180.0", message = "Pickup longitude must be <= 180.0")
    private BigDecimal pickupLongitude;

    @NotNull(message = "Destination latitude is required")
    @DecimalMin(value = "-90.0", message = "Destination latitude must be >= -90.0")
    @DecimalMax(value = "90.0", message = "Destination latitude must be <= 90.0")
    private BigDecimal destinationLatitude;

    @NotNull(message = "Destination longitude is required")
    @DecimalMin(value = "-180.0", message = "Destination longitude must be >= -180.0")
    @DecimalMax(value = "180.0", message = "Destination longitude must be <= 180.0")
    private BigDecimal destinationLongitude;

    public EstimateFareRequest() {
    }

    public EstimateFareRequest(BigDecimal pickupLatitude, BigDecimal pickupLongitude,
                               BigDecimal destinationLatitude, BigDecimal destinationLongitude) {
        this.pickupLatitude = pickupLatitude;
        this.pickupLongitude = pickupLongitude;
        this.destinationLatitude = destinationLatitude;
        this.destinationLongitude = destinationLongitude;
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
}

