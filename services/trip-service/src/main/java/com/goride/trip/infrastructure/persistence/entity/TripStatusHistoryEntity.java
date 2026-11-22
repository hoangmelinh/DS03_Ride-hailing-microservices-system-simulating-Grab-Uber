package com.goride.trip.infrastructure.persistence.entity;

import com.goride.trip.domain.model.ActorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "trip_status_history",
    indexes = {
        @Index(name = "idx_trip_history_trip", columnList = "trip_id")
    }
)
public class TripStatusHistoryEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", length = 30, nullable = false)
    private String toStatus;

    @Column(name = "reason", length = 255)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", length = 30, nullable = false)
    private ActorType actorType;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    public TripStatusHistoryEntity() {
    }

    public TripStatusHistoryEntity(UUID tripId, String fromStatus, String toStatus, String reason,
                                   ActorType actorType, UUID actorId, UUID correlationId) {
        this.id = UUID.randomUUID();
        this.tripId = tripId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.actorType = actorType;
        this.actorId = actorId;
        this.correlationId = correlationId;
        this.changedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.changedAt == null) {
            this.changedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTripId() {
        return tripId;
    }

    public void setTripId(UUID tripId) {
        this.tripId = tripId;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public ActorType getActorType() {
        return actorType;
    }

    public void setActorType(ActorType actorType) {
        this.actorType = actorType;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(UUID correlationId) {
        this.correlationId = correlationId;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }
}
