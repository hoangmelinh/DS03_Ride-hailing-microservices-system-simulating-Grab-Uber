package com.goride.driver.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Driver Location Value Object / Domain Entity.
 * Pure Java class representing the latest position of a driver in PostgreSQL.
 */
public class DriverLocation {

    private final UUID driverId;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Instant updatedAt;

    public DriverLocation(UUID driverId, BigDecimal latitude, BigDecimal longitude, Instant updatedAt) {
        this.driverId = Objects.requireNonNull(driverId, "driverId must not be null");
        this.latitude = Objects.requireNonNull(latitude, "latitude must not be null");
        this.longitude = Objects.requireNonNull(longitude, "longitude must not be null");
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public void updateCoordinates(BigDecimal latitude, BigDecimal longitude) {
        this.latitude = Objects.requireNonNull(latitude, "latitude must not be null");
        this.longitude = Objects.requireNonNull(longitude, "longitude must not be null");
        this.updatedAt = Instant.now();
    }

    public UUID getDriverId() {
        return driverId;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
