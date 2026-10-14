package com.goride.driver.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Driver Aggregate Root (Clean Architecture Domain Entity).
 * Pure Java class with zero framework dependencies (No Spring, JPA, Hibernate).
 */
public class Driver {

    private final UUID id;
    private final UUID userId;
    private String licenseNumber;
    private DriverStatus status;
    private UUID currentTripId;
    private Instant reservedUntil;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    public Driver(UUID id, UUID userId, String licenseNumber, DriverStatus status,
                  UUID currentTripId, Instant reservedUntil, long version,
                  Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.licenseNumber = Objects.requireNonNull(licenseNumber, "licenseNumber must not be null");
        this.status = status != null ? status : DriverStatus.OFFLINE;
        this.currentTripId = currentTripId;
        this.reservedUntil = reservedUntil;
        this.version = version;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static Driver createNew(UUID userId, String licenseNumber) {
        Instant now = Instant.now();
        return new Driver(
                UUID.randomUUID(),
                userId,
                licenseNumber,
                DriverStatus.OFFLINE,
                null,
                null,
                0L,
                now,
                now
        );
    }

    public void goOnline() {
        if (this.status != DriverStatus.OFFLINE) {
            throw new IllegalStateException("Only OFFLINE drivers can transition to AVAILABLE");
        }
        this.status = DriverStatus.AVAILABLE;
        this.updatedAt = Instant.now();
    }

    public void goOffline() {
        if (this.status == DriverStatus.RESERVED || this.status == DriverStatus.ON_TRIP) {
            throw new IllegalStateException("Cannot go offline while RESERVED or ON_TRIP");
        }
        this.status = DriverStatus.OFFLINE;
        this.updatedAt = Instant.now();
    }

    public void markReserved(UUID tripId, Instant reservedUntil) {
        if (this.status != DriverStatus.AVAILABLE) {
            throw new IllegalStateException("Driver is not AVAILABLE for reservation");
        }
        this.status = DriverStatus.RESERVED;
        this.currentTripId = Objects.requireNonNull(tripId, "tripId must not be null");
        this.reservedUntil = Objects.requireNonNull(reservedUntil, "reservedUntil must not be null");
        this.updatedAt = Instant.now();
    }

    public void releaseReservation() {
        if (this.status != DriverStatus.RESERVED) {
            throw new IllegalStateException("Driver is not currently RESERVED");
        }
        this.status = DriverStatus.AVAILABLE;
        this.currentTripId = null;
        this.reservedUntil = null;
        this.updatedAt = Instant.now();
    }

    public void acceptTrip(UUID tripId) {
        if (this.status != DriverStatus.RESERVED) {
            throw new IllegalStateException("Driver must be RESERVED before starting a trip");
        }
        if (!Objects.equals(this.currentTripId, tripId)) {
            throw new IllegalStateException("Trip ID mismatch for driver reservation");
        }
        this.status = DriverStatus.ON_TRIP;
        this.updatedAt = Instant.now();
    }

    public void completeTrip(UUID tripId) {
        if (this.status != DriverStatus.ON_TRIP) {
            throw new IllegalStateException("Driver is not currently ON_TRIP");
        }
        if (!Objects.equals(this.currentTripId, tripId)) {
            throw new IllegalStateException("Trip ID mismatch for completing trip");
        }
        this.status = DriverStatus.AVAILABLE;
        this.currentTripId = null;
        this.reservedUntil = null;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = Objects.requireNonNull(licenseNumber, "licenseNumber must not be null");
        this.updatedAt = Instant.now();
    }

    public DriverStatus getStatus() {
        return status;
    }

    public UUID getCurrentTripId() {
        return currentTripId;
    }

    public Instant getReservedUntil() {
        return reservedUntil;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
