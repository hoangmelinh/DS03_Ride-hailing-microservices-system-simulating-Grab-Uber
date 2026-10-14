package com.goride.driver.adapter.out.persistence.entity;

import com.goride.driver.domain.model.ReservationStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "driver_reservations")
public class DriverReservationJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "reserved_at", nullable = false)
    private Instant reservedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DriverReservationJpaEntity() {
    }

    public DriverReservationJpaEntity(UUID id, UUID driverId, UUID tripId, ReservationStatus status,
                                      String idempotencyKey, UUID correlationId, Instant reservedAt,
                                      Instant expiresAt, Instant confirmedAt, Instant releasedAt,
                                      Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.driverId = driverId;
        this.tripId = tripId;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.correlationId = correlationId;
        this.reservedAt = reservedAt;
        this.expiresAt = expiresAt;
        this.confirmedAt = confirmedAt;
        this.releasedAt = releasedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.reservedAt == null) {
            this.reservedAt = Instant.now();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public void setDriverId(UUID driverId) {
        this.driverId = driverId;
    }

    public UUID getTripId() {
        return tripId;
    }

    public void setTripId(UUID tripId) {
        this.tripId = tripId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(UUID correlationId) {
        this.correlationId = correlationId;
    }

    public Instant getReservedAt() {
        return reservedAt;
    }

    public void setReservedAt(Instant reservedAt) {
        this.reservedAt = reservedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(Instant releasedAt) {
        this.releasedAt = releasedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
