package com.goride.trip.application.dto.response;

import com.goride.trip.domain.model.ActorType;
import com.goride.trip.infrastructure.persistence.entity.TripStatusHistoryEntity;

import java.time.Instant;
import java.util.UUID;

public class TripStatusHistoryResponse {

    private UUID id;
    private UUID tripId;
    private String fromStatus;
    private String toStatus;
    private String reason;
    private ActorType actorType;
    private UUID actorId;
    private UUID correlationId;
    private Instant changedAt;

    public TripStatusHistoryResponse() {
    }

    public static TripStatusHistoryResponse fromEntity(TripStatusHistoryEntity entity) {
        if (entity == null) {
            return null;
        }
        TripStatusHistoryResponse response = new TripStatusHistoryResponse();
        response.setId(entity.getId());
        response.setTripId(entity.getTripId());
        response.setFromStatus(entity.getFromStatus());
        response.setToStatus(entity.getToStatus());
        response.setReason(entity.getReason());
        response.setActorType(entity.getActorType());
        response.setActorId(entity.getActorId());
        response.setCorrelationId(entity.getCorrelationId());
        response.setChangedAt(entity.getChangedAt());
        return response;
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

