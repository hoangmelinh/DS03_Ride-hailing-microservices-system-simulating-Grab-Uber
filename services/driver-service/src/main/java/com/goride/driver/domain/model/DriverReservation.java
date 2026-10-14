package com.goride.driver.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Driver Reservation Domain Entity.
 * Represents an active, confirmed, released, or expired reservation lock for a trip.
 */
public class DriverReservation {

    private final UUID id;
    private final UUID driverId;
    private final UUID tripId;
    private ReservationStatus status;
    private final String idempotencyKey;
    private final UUID correlationId;
    private final Instant reservedAt;
    private final Instant expiresAt;
    private Instant confirmedAt;
    private Instant releasedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    public DriverReservation(UUID id, UUID driverId, UUID tripId, ReservationStatus status,
                             String idempotencyKey, UUID correlationId, Instant reservedAt,
                             Instant expiresAt, Instant confirmedAt, Instant releasedAt,
                             Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.driverId = Objects.requireNonNull(driverId, "driverId must not be null");
        this.tripId = Objects.requireNonNull(tripId, "tripId must not be null");
        this.status = status != null ? status : ReservationStatus.RESERVED;
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId must not be null");
        this.reservedAt = reservedAt != null ? reservedAt : Instant.now();
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.confirmedAt = confirmedAt;
        this.releasedAt = releasedAt;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static DriverReservation create(UUID driverId, UUID tripId, String idempotencyKey,
                                           UUID correlationId, Instant expiresAt) {
        Instant now = Instant.now();
        return new DriverReservation(
                UUID.randomUUID(),
                driverId,
                tripId,
                ReservationStatus.RESERVED,
                idempotencyKey,
                correlationId,
                now,
                expiresAt,
                null,
                null,
                now,
                now
        );
    }

    public void confirm() {
        if (this.status != ReservationStatus.RESERVED) {
            throw new IllegalStateException("Only RESERVED reservations can be CONFIRMED");
        }
        this.status = ReservationStatus.CONFIRMED;
        this.confirmedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void release() {
        if (this.status != ReservationStatus.RESERVED && this.status != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("Reservation cannot be released from state: " + this.status);
        }
        this.status = ReservationStatus.RELEASED;
        this.releasedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void expire() {
        if (this.status == ReservationStatus.RESERVED) {
            this.status = ReservationStatus.EXPIRED;
            this.updatedAt = Instant.now();
        }
    }

    public boolean isExpired(Instant currentInstant) {
        return this.status == ReservationStatus.RESERVED && currentInstant.isAfter(this.expiresAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public UUID getTripId() {
        return tripId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public Instant getReservedAt() {
        return reservedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
